package com.smartboard.teach.domain.model

/**
 * What a container looks like and how its structure behaves.
 *
 * Both kinds are the same underlying thing — a set of world rects that clip
 * and own handwritten ink — so they share one model. Only the STRUCTURE logic
 * differs (grid arithmetic vs tree reflow), and that lives in `TableGrid` and
 * `MindmapLayout` rather than in two parallel models.
 */
enum class ContainerKind {
    TABLE,
    MINDMAP,

    /**
     * An inserted image, PDF page or video frame.
     *
     * One cell, no structure, and [Container.mediaPath] points at the file.
     * Modelling media as a container rather than a fourth top-level object
     * means selection, move, resize, clipping, persistence and cascade-delete
     * all work the day it lands — and ink written over a picture is tagged to
     * it, so annotating a diagram moves with the diagram.
     */
    IMAGE,

    /**
     * An inserted video, shown on the board as its first frame.
     *
     * Renders exactly like [IMAGE] — the poster frame goes in the same bitmap
     * cache — so move, resize, clipping, annotation and persistence are the
     * IMAGE paths unchanged. Only playback differs, and that happens in a
     * full-screen player rather than on the canvas: a live video surface above
     * the board would compete with it for touches, and the canvas has to stay
     * the single owner of pointer input.
     */
    VIDEO,
    ;

    /** True when the cell is filled with media rather than drawn as a frame. */
    val isMedia: Boolean get() = this == IMAGE || this == VIDEO
}

/**
 * One cell of a table, or one node of a mindmap.
 *
 * Rects are absolute WORLD coordinates, matching the strokes they contain.
 * Storing them relative to the container would mean transforming on every
 * render, hit-test and export — see the note on [Container].
 */
data class ContainerCell(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** TABLE: grid row. MINDMAP: unused (-1). */
    val row: Int = -1,
    /**
     * TABLE: grid column.
     *
     * MINDMAP: index of this node's PARENT cell, -1 for the root. Reusing the
     * column field avoids a second nullable column in Room and a second sealed
     * subclass, for a concept that is structurally identical: "where does this
     * cell sit relative to the others".
     */
    val col: Int = -1,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /**
     * Half-open containment: top/left inclusive, bottom/right exclusive.
     *
     * Adjacent cells share an edge, so a closed test would let both claim a
     * point on the boundary and the ink would land in whichever was checked
     * first — stable in a unit test, arbitrary on a board.
     */
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < right && y >= top && y < bottom
}

/**
 * A table or mindmap: a frame that owns handwritten ink cell by cell.
 *
 * Contained strokes are NOT stored here. They live in the page's single stroke
 * list tagged with [Stroke.containerId] and [Stroke.cellIndex], which keeps the
 * renderer, eraser, selection and persistence paths working on one list. The
 * container only supplies the rects used to clip and to move that ink.
 *
 * Cell rects are absolute world coordinates. Container-local coordinates would
 * be cheaper to move but would require a transform inside every renderer,
 * hit-test, marquee, bounds and export path — and a missed transform there
 * yields ink that draws correctly and erases in the wrong place, which is the
 * kind of bug that reaches a classroom.
 */
data class Container(
    val id: String,
    val kind: ContainerKind,
    /** World top-left; the anchor a move is expressed against. */
    val x: Float,
    val y: Float,
    /** TABLE only; 0 for a mindmap. */
    val rows: Int = 0,
    val cols: Int = 0,
    val cells: List<ContainerCell> = emptyList(),
    val strokeColorArgb: Int = DEFAULT_LINE_ARGB,
    val lineWidthPx: Float = DEFAULT_LINE_WIDTH,
    /**
     * File backing an [ContainerKind.IMAGE], or null for a frame container.
     *
     * A path rather than a bitmap: containers are copied on every move frame,
     * and carrying a decoded bitmap through that would be ruinous. The canvas
     * keeps its own id-keyed bitmap cache.
     */
    val mediaPath: String? = null,
) {
    /** Union of every cell, or an empty rect when there are none. */
    fun bounds(): FloatArray {
        if (cells.isEmpty()) return floatArrayOf(x, y, x, y)
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        cells.forEach { cell ->
            if (cell.left < left) left = cell.left
            if (cell.top < top) top = cell.top
            if (cell.right > right) right = cell.right
            if (cell.bottom > bottom) bottom = cell.bottom
        }
        return floatArrayOf(left, top, right, bottom)
    }

    /** Index of the cell under a world point, or -1. */
    fun cellIndexAt(worldX: Float, worldY: Float): Int =
        cells.indexOfFirst { it.contains(worldX, worldY) }

    fun cellAt(index: Int): ContainerCell? = cells.getOrNull(index)

    companion object {
        /** Muted slate: a grid is scaffolding, the ink is the lesson. */
        const val DEFAULT_LINE_ARGB: Int = 0xFF546170.toInt()
        const val DEFAULT_LINE_WIDTH: Float = 2f
    }
}
