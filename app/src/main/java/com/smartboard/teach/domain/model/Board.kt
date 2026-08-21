package com.smartboard.teach.domain.model

/**
 * Drawing tools. Shapes are tools rather than a separate object type so that
 * one uniform stroke list covers everything: undo/redo, serialization and
 * committed-layer replay need no special cases per shape.
 */
enum class DrawTool {
    PEN,
    HIGHLIGHTER,
    ERASER,

    // --- lines ---
    LINE,
    DASHED_LINE,
    ARROW,
    DASHED_ARROW,

    // --- triangles ---
    TRIANGLE,
    ISOSCELES_TRIANGLE,
    RIGHT_TRIANGLE,

    // --- quadrilaterals ---
    DIAMOND,
    PARALLELOGRAM,
    TRAPEZOID,
    RECT,
    ROUNDED_RECT,

    // --- round ---
    CIRCLE,
    ELLIPSE,
    SEMICIRCLE,

    // --- regular polygons ---
    PENTAGON,
    HEXAGON,
    STAR,

    /**
     * 3-D solids, drawn as flat projections with hidden edges dashed.
     *
     * Geometry is still derived from the drag box, exactly like the 2-D
     * shapes — a cube is a front face, a back face and three joining edges,
     * all computed from two corners. That keeps them inside the existing
     * two-point model rather than needing stored meshes.
     */
    CUBE,
    PYRAMID,
    PRISM,
    TETRAHEDRON,
    CYLINDER,
    CONE,
    SPHERE,

    /**
     * A closed polygon with N vertices — triangle, pentagon, hexagon, and so
     * on. Unlike the other shapes it stores every vertex rather than two
     * endpoints, because a bounding box cannot describe where the corners of
     * a triangle are.
     *
     * Only produced by shape recognition; there is no freehand polygon tool,
     * since drawing one by hand IS the gesture.
     */
    POLYGON;

    /**
     * Shape tools derive their geometry from stored points rather than from
     * a freehand path.
     */
    val isShape: Boolean
        get() = this != PEN && this != HIGHLIGHTER && this != ERASER

    /**
     * True for shapes defined by exactly two dragged endpoints.
     *
     * Everything except POLYGON, which carries its own vertex list. A drag box
     * describes every other shape here, including the 3-D solids.
     */
    val isTwoPointShape: Boolean
        get() = isShape && this != POLYGON

    val isFreehand: Boolean get() = this == PEN || this == HIGHLIGHTER

    /** Rendered with a dashed stroke, for the two dashed line tools. */
    val isDashed: Boolean get() = this == DASHED_LINE || this == DASHED_ARROW

    /** Carries an arrow head at the far end. */
    val hasArrowHead: Boolean get() = this == ARROW || this == DASHED_ARROW

    /** A solid, so hidden edges are drawn dashed behind the visible ones. */
    val isSolid: Boolean
        get() = this == CUBE || this == PYRAMID || this == PRISM ||
            this == TETRAHEDRON || this == CYLINDER || this == CONE || this == SPHERE
}

data class StrokeStyle(
    val colorArgb: Int,
    val baseWidthPx: Float,
    val alpha: Float = 1f,
    val isPressureSensitive: Boolean = true,
)

/**
 * A single stroke.
 *
 * [points] is a FLAT FloatArray of [x, y, pressure] triples rather than a
 * List<Offset>. A 400-point stroke is then one allocation instead of 400 boxed
 * objects; with hundreds of strokes per page that is the difference between
 * smooth ink and GC stutter.
 */
class Stroke(
    val id: String,
    val tool: DrawTool,
    val style: StrokeStyle,
    val points: FloatArray,
    /**
     * The container this ink was written into, or null for free ink.
     *
     * Resolved ONCE, at the press that created the stroke, and never updated
     * while drawing: a letter whose descender leaves the cell still belongs to
     * the cell, exactly as writing on paper works. The render clip handles the
     * visual.
     */
    val containerId: String? = null,
    /** Index into the container's cells. Meaningless when [containerId] is null. */
    val cellIndex: Int = -1,
) {
    val pointCount: Int get() = points.size / STRIDE

    fun x(i: Int): Float = points[i * STRIDE]
    fun y(i: Int): Float = points[i * STRIDE + 1]
    fun pressure(i: Int): Float = points[i * STRIDE + 2]

    /**
     * A transformed copy that KEEPS the container tag.
     *
     * Every transform must go through here rather than calling the constructor
     * positionally. `containerId` and `cellIndex` are defaulted, so a direct
     * `Stroke(id, tool, style, points)` compiles fine and silently drops the
     * tag — ink would escape its cell the first time it was moved, scaled,
     * rotated or shape-snapped, and the compiler would never say a word.
     */
    fun copyWith(
        id: String = this.id,
        tool: DrawTool = this.tool,
        style: StrokeStyle = this.style,
        points: FloatArray = this.points,
        containerId: String? = this.containerId,
        /** Retagging is real: deleting a mindmap node renumbers the cells after it. */
        cellIndex: Int = this.cellIndex,
    ): Stroke = Stroke(id, tool, style, points, containerId, cellIndex)

    /** Axis-aligned bounds, inflated by stroke width — used for eraser hit-tests. */
    fun bounds(): FloatArray {
        if (points.isEmpty()) return floatArrayOf(0f, 0f, 0f, 0f)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until pointCount) {
            val px = x(i)
            val py = y(i)
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py
        }
        val pad = style.baseWidthPx
        return floatArrayOf(minX - pad, minY - pad, maxX + pad, maxY + pad)
    }

    companion object {
        /** Floats per point: x, y, pressure. */
        const val STRIDE = 3
    }
}

data class TextBox(
    val id: String,
    val x: Float,
    val y: Float,
    val widthPx: Float,
    val text: String,
    val colorArgb: Int,
    val fontSizeSp: Float,
)

enum class BackgroundKind { IMAGE, PDF_PAGE }

/**
 * An imported image or rendered PDF page shown behind the ink.
 *
 * Kept as its own layer and never rasterized into the committed bitmap, so it
 * can be swapped or removed without disturbing strokes. This is also the seam
 * Phase 2 uses to annotate LMS-supplied PDFs.
 */
data class BoardBackground(
    val id: String,
    val kind: BackgroundKind,
    val sourcePath: String,
    val pdfPageIndex: Int? = null,
    val renderedPath: String,
    /** Top-left in world coordinates. */
    val x: Float = 0f,
    val y: Float = 0f,
    /** Uniform scale; images keep their aspect ratio. */
    val scale: Float = 1f,
    /** Radians, clockwise, about the image centre. */
    val rotation: Float = 0f,
)

/** Saved camera position for a page on the infinite canvas. */
data class CameraState(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val zoom: Float = 1f,
)

data class BoardPage(
    val id: String,
    val sessionId: String,
    val pageIndex: Int,
    val widthPx: Int,
    val heightPx: Int,
    val backgroundId: String? = null,
    val thumbnailPath: String? = null,
    /** The page's paper: colour and grid. */
    val canvasStyle: BoardCanvasStyle = BoardCanvasStyle(),
    /** Where the teacher was looking when they left this page. */
    val camera: CameraState = CameraState(),
)

/**
 * A saved lesson: a named session of pages.
 *
 * A session with no Lesson is an unsaved working session, which is what the
 * board starts with. Saving simply gives the current session a name.
 */
data class Lesson(
    val sessionId: String,
    val name: String,
    val updatedAt: Long,
    /** Filled by the repository for the Open list; not stored. */
    val pageCount: Int = 0,
)
