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

    /** Wide enough for a few handwritten words at a comfortable pen size. */
    const val DEFAULT_CELL_WIDTH = 260f
    const val DEFAULT_CELL_HEIGHT = 140f
}
