package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.TextBox
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Geometry for selecting and transforming board content.
 *
 * All coordinates here are WORLD coordinates. Transforms rewrite the stored
 * point arrays rather than carrying a per-object matrix: the renderer, the
 * serializer and the eraser hit-test all read raw points, and keeping one
 * representation avoids every one of them having to understand transforms.
 */
object Selection {

    /** Handle positions on the selection bounding box. */
    enum class Handle {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        TOP, BOTTOM, LEFT, RIGHT,
        ROTATE;

        val isCorner: Boolean
            get() = this == TOP_LEFT || this == TOP_RIGHT ||
                this == BOTTOM_LEFT || this == BOTTOM_RIGHT
    }

    /** Empty bounds sentinel: left > right. */
    fun emptyBounds(): FloatArray = floatArrayOf(1f, 1f, -1f, -1f)

    /** Typical line spacing as a multiple of font size. */
    private const val LINE_HEIGHT = 1.3f

    private const val LINE_BREAK = '\n'

    fun isEmpty(bounds: FloatArray): Boolean = bounds[2] < bounds[0] || bounds[3] < bounds[1]

    /**
     * How many world pixels one sp is, set once from the composition.
     *
     * Text boxes store their size in sp but live in a world-pixel coordinate
     * space, so every bound and hit test has to convert. Treating sp as px
     * directly made a box's rect roughly a third of its real height: taps
     * below the first line missed it, and fit-to-content cropped the text.
     */
    var spToWorldPx: Float = 1f

    /** World-space height of a text box, including every wrapped line. */
    fun textBoxHeight(box: TextBox): Float {
        val lines = box.text.count { it == LINE_BREAK } + 1
        return box.fontSizeSp * spToWorldPx * LINE_HEIGHT * lines
    }

    /**
     * Union bounds of the given strokes and text boxes, in world space.
     * Text boxes are approximated by their declared width and font height —
     * exact glyph metrics are not available outside a composition.
     */
    fun boundsOf(strokes: List<Stroke>, textBoxes: List<TextBox>): FloatArray {
        if (strokes.isEmpty() && textBoxes.isEmpty()) return emptyBounds()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        strokes.forEach { stroke ->
            val b = stroke.bounds()
            minX = min(minX, b[0]); minY = min(minY, b[1])
            maxX = max(maxX, b[2]); maxY = max(maxY, b[3])
        }

        textBoxes.forEach { box ->
            val height = textBoxHeight(box)
            minX = min(minX, box.x); minY = min(minY, box.y)
            maxX = max(maxX, box.x + box.widthPx); maxY = max(maxY, box.y + height)
        }

        return floatArrayOf(minX, minY, maxX, maxY)
    }

    fun rectsIntersect(a: FloatArray, b: FloatArray): Boolean =
        a[0] <= b[2] && a[2] >= b[0] && a[1] <= b[3] && a[3] >= b[1]

    fun rectContains(outer: FloatArray, inner: FloatArray): Boolean =
        outer[0] <= inner[0] && outer[1] <= inner[1] &&
            outer[2] >= inner[2] && outer[3] >= inner[3]

    fun pointInRect(x: Float, y: Float, rect: FloatArray): Boolean =
        x >= rect[0] && x <= rect[2] && y >= rect[1] && y <= rect[3]

    /** Normalizes a drag rectangle so left<=right and top<=bottom. */
    fun normalizeRect(x0: Float, y0: Float, x1: Float, y1: Float): FloatArray =
        floatArrayOf(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1))

    /**
     * Strokes touched by a marquee.
     *
     * Uses INTERSECTION rather than full containment: on a board a teacher
     * drags a rough box around a diagram, and requiring every stroke to be
     * fully enclosed makes selection feel broken.
     */
    fun strokesInMarquee(strokes: List<Stroke>, marquee: FloatArray): List<Stroke> =
        strokes.filter { rectsIntersect(it.bounds(), marquee) }

    fun textBoxesInMarquee(textBoxes: List<TextBox>, marquee: FloatArray): List<TextBox> =
        textBoxes.filter { box ->
            val height = textBoxHeight(box)
            rectsIntersect(
                floatArrayOf(box.x, box.y, box.x + box.widthPx, box.y + height),
                marquee,
            )
        }

    /**
     * Topmost stroke under a tap, or null.
     *
     * Searched in reverse so the most recently drawn stroke wins, matching
     * what the teacher sees on top.
     */
    fun strokeAt(strokes: List<Stroke>, x: Float, y: Float, tolerance: Float): Stroke? =
        strokes.lastOrNull { StrokeHitTest.intersects(it, x, y, tolerance) }

    fun textBoxAt(textBoxes: List<TextBox>, x: Float, y: Float): TextBox? =
        textBoxes.lastOrNull { box ->
            val height = textBoxHeight(box)
            pointInRect(x, y, floatArrayOf(box.x, box.y, box.x + box.widthPx, box.y + height))
        }

    /**
     * Which handle, if any, is under a world point.
     *
     * [handleRadius] is in WORLD units — the caller converts from a screen-space
     * touch radius so handles stay the same physical size at any zoom.
     *
     * @param rotateGapWorld distance from the top edge to the rotate handle,
     *        in WORLD units. The chrome draws that stalk in SCREEN space so
     *        the handle stays a constant physical size, so the caller must
     *        convert; deriving it from [handleRadius] here silently made the
     *        hit-zone drift away from the drawn circle at every zoom except
     *        1.0, which made rotation untriggerable.
     */
    fun handleAt(
        bounds: FloatArray,
        x: Float,
        y: Float,
        handleRadius: Float,
        rotateGapWorld: Float = handleRadius * ROTATE_HANDLE_GAP,
    ): Handle? {
        if (isEmpty(bounds)) return null

        val left = bounds[0]; val top = bounds[1]
        val right = bounds[2]; val bottom = bounds[3]
        val midX = (left + right) / 2f
        val midY = (top + bottom) / 2f

        // Rotate handle sits above the top edge. Checked FIRST so it wins over
        // the TOP edge handle where the two touch zones overlap.
        val rotateY = top - rotateGapWorld
        if (near(x, y, midX, rotateY, handleRadius)) return Handle.ROTATE

        // Corners take priority over edges where they overlap.
        if (near(x, y, left, top, handleRadius)) return Handle.TOP_LEFT
        if (near(x, y, right, top, handleRadius)) return Handle.TOP_RIGHT
        if (near(x, y, left, bottom, handleRadius)) return Handle.BOTTOM_LEFT
        if (near(x, y, right, bottom, handleRadius)) return Handle.BOTTOM_RIGHT

        if (near(x, y, midX, top, handleRadius)) return Handle.TOP
        if (near(x, y, midX, bottom, handleRadius)) return Handle.BOTTOM
        if (near(x, y, left, midY, handleRadius)) return Handle.LEFT
        if (near(x, y, right, midY, handleRadius)) return Handle.RIGHT

        return null
    }

    private fun near(x: Float, y: Float, tx: Float, ty: Float, radius: Float): Boolean =
        abs(x - tx) <= radius && abs(y - ty) <= radius

    // --- Transforms -------------------------------------------------------

    /**
     * A copy of [stroke] with its points in SCREEN coordinates.
     *
     * Used only to feed the handwriting recognizer, which expects writing at
     * a natural on-screen size — the same words written on a board zoomed to
     * 30%% would otherwise arrive a third as large as at 100%%.
     */
    fun scaleStrokeToScreen(stroke: Stroke, camera: Camera): Stroke {
        val points = stroke.points.copyOf()
        for (i in 0 until stroke.pointCount) {
            points[i * Stroke.STRIDE] = camera.worldToScreenX(stroke.x(i))
            points[i * Stroke.STRIDE + 1] = camera.worldToScreenY(stroke.y(i))
        }
        return stroke.copyWith(points = points)
    }

    fun translateStroke(stroke: Stroke, dx: Float, dy: Float): Stroke {
        val points = stroke.points.copyOf()
        for (i in 0 until stroke.pointCount) {
            points[i * Stroke.STRIDE] += dx
            points[i * Stroke.STRIDE + 1] += dy
        }
        return stroke.copyWith(points = points)
    }

    fun translateTextBox(box: TextBox, dx: Float, dy: Float): TextBox =
        box.copy(x = box.x + dx, y = box.y + dy)

    /**
     * Scales a stroke about a pivot.
     *
     * Stroke WIDTH scales with the average of the two axes, so a stroke
     * enlarged 2x looks like the same pen drawn bigger rather than a hairline
     * stretched across the board.
     */
    fun scaleStroke(
        stroke: Stroke,
        pivotX: Float,
        pivotY: Float,
        scaleX: Float,
        scaleY: Float,
    ): Stroke {
        val points = stroke.points.copyOf()
        for (i in 0 until stroke.pointCount) {
            val xi = i * Stroke.STRIDE
            val yi = xi + 1
            points[xi] = pivotX + (points[xi] - pivotX) * scaleX
            points[yi] = pivotY + (points[yi] - pivotY) * scaleY
        }
        val widthScale = (abs(scaleX) + abs(scaleY)) / 2f
        return stroke.copyWith(
            style = stroke.style.copy(
                baseWidthPx = (stroke.style.baseWidthPx * widthScale)
                    .coerceIn(MIN_STROKE_WIDTH, MAX_STROKE_WIDTH),
            ),
            points = points,
        )
    }

    fun scaleTextBox(
        box: TextBox,
        pivotX: Float,
        pivotY: Float,
        scaleX: Float,
        scaleY: Float,
    ): TextBox = box.copy(
        x = pivotX + (box.x - pivotX) * scaleX,
        y = pivotY + (box.y - pivotY) * scaleY,
        widthPx = (box.widthPx * abs(scaleX)).coerceAtLeast(MIN_TEXT_WIDTH),
        fontSizeSp = (box.fontSizeSp * (abs(scaleX) + abs(scaleY)) / 2f)
            .coerceIn(MIN_FONT_SP, MAX_FONT_SP),
    )

    fun rotateStroke(stroke: Stroke, pivotX: Float, pivotY: Float, radians: Float): Stroke {
        // A RECT stores two CORNERS and is drawn with min/max, which rebuilds
        // an upright box and silently discards any rotation. Expand it to its
        // four actual corners as a POLYGON first: that tool already draws a
        // closed path through stored vertices, so the rotation survives.
        if (stroke.tool == DrawTool.RECT && stroke.pointCount >= 2) {
            return rotateStroke(rectToPolygon(stroke), pivotX, pivotY, radians)
        }

        val cos = cos(radians)
        val sin = sin(radians)
        val points = stroke.points.copyOf()
        for (i in 0 until stroke.pointCount) {
            val xi = i * Stroke.STRIDE
            val yi = xi + 1
            val dx = points[xi] - pivotX
            val dy = points[yi] - pivotY
            points[xi] = pivotX + dx * cos - dy * sin
            points[yi] = pivotY + dx * sin + dy * cos
        }
        return stroke.copyWith(points = points)
    }

    /**
     * Two-corner RECT -> four-vertex POLYGON, same visual result.
     *
     * Done only when a rectangle is first rotated. Drawing and resizing a
     * rectangle stay on the cheaper two-point representation until then.
     */
    private fun rectToPolygon(stroke: Stroke): Stroke {
        val x0 = stroke.x(0); val y0 = stroke.y(0)
        val x1 = stroke.x(1); val y1 = stroke.y(1)
        val left = min(x0, x1); val right = max(x0, x1)
        val top = min(y0, y1); val bottom = max(y0, y1)

        val points = FloatArray(4 * Stroke.STRIDE)
        val corners = listOf(
            left to top, right to top, right to bottom, left to bottom,
        )
        corners.forEachIndexed { i, (cx, cy) ->
            points[i * Stroke.STRIDE] = cx
            points[i * Stroke.STRIDE + 1] = cy
            points[i * Stroke.STRIDE + 2] = 1f
        }
        return stroke.copyWith(tool = DrawTool.POLYGON, points = points)
    }

    /**
     * Text boxes rotate as a position change only.
     *
     * Their glyphs stay upright: rotated text on a classroom board is almost
     * never wanted, and rendering it would mean carrying a rotation on every
     * text composable.
     */
    fun rotateTextBox(box: TextBox, pivotX: Float, pivotY: Float, radians: Float): TextBox {
        val cos = cos(radians)
        val sin = sin(radians)
        val dx = box.x - pivotX
        val dy = box.y - pivotY
        return box.copy(
            x = pivotX + dx * cos - dy * sin,
            y = pivotY + dx * sin + dy * cos,
        )
    }

    /** Copies with fresh ids, offset so the duplicate is visibly separate. */
    fun duplicateStrokes(strokes: List<Stroke>, offset: Float = DUPLICATE_OFFSET): List<Stroke> =
        strokes.map { stroke ->
            translateStroke(stroke, offset, offset)
                .copyWith(id = UUID.randomUUID().toString())
        }

    fun duplicateTextBoxes(
        boxes: List<TextBox>,
        offset: Float = DUPLICATE_OFFSET,
    ): List<TextBox> = boxes.map {
        it.copy(id = UUID.randomUUID().toString(), x = it.x + offset, y = it.y + offset)
    }

    /**
     * Scale factors for dragging [handle] to a new world point.
     *
     * Edge handles scale one axis only. Corner handles scale both. The pivot
     * is always the opposite corner or edge, which is what makes a resize feel
     * anchored rather than sliding.
     */
    fun scaleForHandleDrag(
        handle: Handle,
        bounds: FloatArray,
        pointerX: Float,
        pointerY: Float,
    ): FloatArray {
        val left = bounds[0]; val top = bounds[1]
        val right = bounds[2]; val bottom = bounds[3]
        val width = (right - left).takeIf { it > MIN_DIMENSION } ?: return NO_SCALE
        val height = (bottom - top).takeIf { it > MIN_DIMENSION } ?: return NO_SCALE

        var scaleX = 1f
        var scaleY = 1f
        var pivotX = left
        var pivotY = top

        when (handle) {
            Handle.RIGHT, Handle.TOP_RIGHT, Handle.BOTTOM_RIGHT -> {
                scaleX = (pointerX - left) / width
                pivotX = left
            }
            Handle.LEFT, Handle.TOP_LEFT, Handle.BOTTOM_LEFT -> {
                scaleX = (right - pointerX) / width
                pivotX = right
            }
            else -> Unit
        }

        when (handle) {
            Handle.BOTTOM, Handle.BOTTOM_LEFT, Handle.BOTTOM_RIGHT -> {
                scaleY = (pointerY - top) / height
                pivotY = top
            }
            Handle.TOP, Handle.TOP_LEFT, Handle.TOP_RIGHT -> {
                scaleY = (bottom - pointerY) / height
                pivotY = bottom
            }
            else -> Unit
        }

        // Never allow a flip or a collapse to zero — both destroy the content
        // irrecoverably from the user's point of view.
        scaleX = scaleX.coerceAtLeast(MIN_SCALE)
        scaleY = scaleY.coerceAtLeast(MIN_SCALE)

        return floatArrayOf(scaleX, scaleY, pivotX, pivotY)
    }

    private val NO_SCALE = floatArrayOf(1f, 1f, 0f, 0f)

    const val MIN_SCALE = 0.05f
    const val MIN_DIMENSION = 0.01f
    const val MIN_STROKE_WIDTH = 0.5f
    const val MAX_STROKE_WIDTH = 400f
    const val MIN_TEXT_WIDTH = 20f
    const val MIN_FONT_SP = 6f
    const val MAX_FONT_SP = 400f
    const val DUPLICATE_OFFSET = 24f

    /** Rotate handle distance above the box, in handle radii. */
    const val ROTATE_HANDLE_GAP = 3f
}
