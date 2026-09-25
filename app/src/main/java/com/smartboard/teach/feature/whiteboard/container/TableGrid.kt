package com.smartboard.teach.feature.whiteboard.container

import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.ContainerCell
import com.smartboard.teach.domain.model.ContainerKind
import java.util.UUID

/**
 * Grid geometry for table containers.
 *
 * Pure functions over immutable data — no Android types, no state — so the
 * row/column arithmetic that decides where a teacher's handwriting ends up is
 * unit-testable. Off-by-one errors here only surface after a save and reload,
 * which is the worst possible time to find them.
 */
object TableGrid {

    /**
     * A uniform grid anchored at (x, y).
     *
     * Cells are laid out row-major, so `cellIndex == row * cols + col`. Every
     * caller depends on that ordering; see [reindexAfterRowInsert].
     */
    fun create(
        x: Float,
        y: Float,
        rows: Int,
        cols: Int,
        cellWidth: Float = DEFAULT_CELL_WIDTH,
        cellHeight: Float = DEFAULT_CELL_HEIGHT,
        id: String = UUID.randomUUID().toString(),
    ): Container {
        require(rows > 0 && cols > 0) { "a table needs at least one cell" }
        val cells = ArrayList<ContainerCell>(rows * cols)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val left = x + col * cellWidth
                val top = y + row * cellHeight
                cells += ContainerCell(
                    left = left,
                    top = top,
                    right = left + cellWidth,
                    bottom = top + cellHeight,
                    row = row,
                    col = col,
                )
            }
        }
        return Container(
            id = id,
            kind = ContainerKind.TABLE,
            x = x,
            y = y,
            rows = rows,
            cols = cols,
            cells = cells,
        )
    }

    /** Row-major index of a cell, matching [create]'s ordering. */
    fun indexOf(row: Int, col: Int, cols: Int): Int = row * cols + col

    /**
     * Where cell [oldIndex] moves to when a row is inserted at [insertAt].
     *
     * Rows at or after the insertion point shift down by one row's worth of
     * indices; rows above keep their index. Returned so contained ink can be
     * retagged in the same pass that rebuilds the cell rects.
     */
    fun reindexAfterRowInsert(oldIndex: Int, insertAt: Int, cols: Int): Int {
        val row = oldIndex / cols
        val col = oldIndex % cols
        val newRow = if (row >= insertAt) row + 1 else row
        return indexOf(newRow, col, cols)
    }

    /**
     * Where cell [oldIndex] moves to when [deleteAt] is removed, or -1 when the
     * cell itself is being deleted (its ink dies with it).
     */
    fun reindexAfterRowDelete(oldIndex: Int, deleteAt: Int, cols: Int): Int {
        val row = oldIndex / cols
        val col = oldIndex % cols
        if (row == deleteAt) return -1
        val newRow = if (row > deleteAt) row - 1 else row
        return indexOf(newRow, col, cols)
    }

    fun reindexAfterColumnInsert(oldIndex: Int, insertAt: Int, cols: Int): Int {
        val row = oldIndex / cols
        val col = oldIndex % cols
        val newCol = if (col >= insertAt) col + 1 else col
        return indexOf(row, newCol, cols + 1)
    }

    fun reindexAfterColumnDelete(oldIndex: Int, deleteAt: Int, cols: Int): Int {
        val row = oldIndex / cols
        val col = oldIndex % cols
        if (col == deleteAt) return -1
        val newCol = if (col > deleteAt) col - 1 else col
        return indexOf(row, newCol, cols - 1)
    }

    /** Height of the row at [row], used to translate the ink pushed by an insert. */
    fun rowHeight(container: Container, row: Int): Float =
        container.cells.firstOrNull { it.row == row }?.height ?: DEFAULT_CELL_HEIGHT

    fun columnWidth(container: Container, col: Int): Float =
        container.cells.firstOrNull { it.col == col }?.width ?: DEFAULT_CELL_WIDTH

    /**
     * A copy of [container] with a row inserted at [insertAt].
     *
     * Rebuilds every rect from the surviving row heights and column widths
     * rather than nudging the existing ones: a table whose rows were resized
     * would otherwise drift a little further out of true with each insert.
     *
     * [insertAt] == rows appends at the bottom; 0 inserts above the first row.
     */
    fun withRowInserted(container: Container, insertAt: Int): Container {
        val at = insertAt.coerceIn(0, container.rows)
        val heights = (0 until container.rows).map { rowHeight(container, it) }.toMutableList()
        heights.add(at, heights.getOrElse(at) { heights.lastOrNull() ?: DEFAULT_CELL_HEIGHT })
        return rebuild(container, heights, columnWidths(container))
    }

    /** A copy of [container] with a column inserted at [insertAt]. */
    fun withColumnInserted(container: Container, insertAt: Int): Container {
        val at = insertAt.coerceIn(0, container.cols)
        val widths = columnWidths(container).toMutableList()
        widths.add(at, widths.getOrElse(at) { widths.lastOrNull() ?: DEFAULT_CELL_WIDTH })
        return rebuild(container, (0 until container.rows).map { rowHeight(container, it) }, widths)
    }

    private fun columnWidths(container: Container): List<Float> =
        (0 until container.cols).map { columnWidth(container, it) }

    /** Lays out a grid of the given row heights and column widths at the container's origin. */
    private fun rebuild(
        container: Container,
        heights: List<Float>,
        widths: List<Float>,
    ): Container {
        val cells = ArrayList<ContainerCell>(heights.size * widths.size)
        var top = container.y
        for (row in heights.indices) {
            var left = container.x
            for (col in widths.indices) {
                cells += ContainerCell(
                    left = left,
                    top = top,
                    right = left + widths[col],
                    bottom = top + heights[row],
                    row = row,
                    col = col,
                )
                left += widths[col]
            }
            top += heights[row]
        }
        return container.copy(rows = heights.size, cols = widths.size, cells = cells)
    }

    /** Wide enough for a few handwritten words at a comfortable pen size. */
    const val DEFAULT_CELL_WIDTH = 260f
    const val DEFAULT_CELL_HEIGHT = 140f
}
