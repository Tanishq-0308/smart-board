package com.smartboard.teach.feature.whiteboard.container

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row/column reindexing.
 *
 * The highest-value tests in M1: an off-by-one here does not crash, it puts a
 * teacher's handwriting in the wrong cell, and only after a save and reload.
 */
class TableGridTest {

    @Test
    fun `a new grid has one cell per row-column pair`() {
        val t = TableGrid.create(0f, 0f, rows = 3, cols = 4)
        assertEquals(12, t.cells.size)
        assertEquals(3, t.rows)
        assertEquals(4, t.cols)
    }

    @Test
    fun `cells are laid out row-major and contiguous`() {
        val t = TableGrid.create(0f, 0f, rows = 2, cols = 3, cellWidth = 10f, cellHeight = 5f)
        t.cells.forEachIndexed { index, cell ->
            assertEquals("index $index row", index / 3, cell.row)
            assertEquals("index $index col", index % 3, cell.col)
        }
        // No gaps: the right edge of one cell is the left edge of the next.
        assertEquals(t.cells[0].right, t.cells[1].left, 0.001f)
        assertEquals(t.cells[0].bottom, t.cells[3].top, 0.001f)
    }

    @Test
    fun `bounds cover the whole grid`() {
        val t = TableGrid.create(100f, 200f, rows = 2, cols = 2, cellWidth = 50f, cellHeight = 25f)
        val b = t.bounds()
        assertEquals(100f, b[0], 0.001f)
        assertEquals(200f, b[1], 0.001f)
        assertEquals(200f, b[2], 0.001f)
        assertEquals(250f, b[3], 0.001f)
    }

    // --- row insert ---

    @Test
    fun `inserting a row pushes later rows down and leaves earlier rows alone`() {
        val cols = 3
        // Row 0 keeps its indices.
        assertEquals(0, TableGrid.reindexAfterRowInsert(0, insertAt = 1, cols = cols))
        assertEquals(2, TableGrid.reindexAfterRowInsert(2, insertAt = 1, cols = cols))
        // Row 1 becomes row 2: index 3 -> 6.
        assertEquals(6, TableGrid.reindexAfterRowInsert(3, insertAt = 1, cols = cols))
        // Row 2 becomes row 3: index 7 -> 10.
        assertEquals(10, TableGrid.reindexAfterRowInsert(7, insertAt = 1, cols = cols))
    }

    @Test
    fun `inserting a row at the end moves nothing`() {
        val cols = 2
        for (i in 0 until 4) {
            assertEquals(i, TableGrid.reindexAfterRowInsert(i, insertAt = 2, cols = cols))
        }
    }

    @Test
    fun `row insert preserves the column of every cell`() {
        val cols = 4
        for (index in 0 until 12) {
            val moved = TableGrid.reindexAfterRowInsert(index, insertAt = 1, cols = cols)
            assertEquals("cell $index changed column", index % cols, moved % cols)
        }
    }

    // --- row delete ---

    @Test
    fun `deleting a row reports its own cells as gone`() {
        val cols = 3
        // Row 1 is indices 3,4,5.
        assertEquals(-1, TableGrid.reindexAfterRowDelete(3, deleteAt = 1, cols = cols))
        assertEquals(-1, TableGrid.reindexAfterRowDelete(5, deleteAt = 1, cols = cols))
    }

    @Test
    fun `deleting a row pulls later rows up`() {
        val cols = 3
        assertEquals(0, TableGrid.reindexAfterRowDelete(0, deleteAt = 1, cols = cols))
        // Row 2 (index 6) becomes row 1 (index 3).
        assertEquals(3, TableGrid.reindexAfterRowDelete(6, deleteAt = 1, cols = cols))
    }

    @Test
    fun `insert then delete of the same row is the identity`() {
        val cols = 3
        for (index in 0 until 9) {
            val after = TableGrid.reindexAfterRowInsert(index, insertAt = 1, cols = cols)
            assertEquals(index, TableGrid.reindexAfterRowDelete(after, deleteAt = 1, cols = cols))
        }
    }

    // --- column insert / delete ---

    @Test
    fun `inserting a column widens the row stride`() {
        val cols = 2
        // Index 2 is (row 1, col 0). Inserting at column 0 pushes it to col 1,
        // and the row is now 3 wide: 1*3 + 1 = 4.
        assertEquals(4, TableGrid.reindexAfterColumnInsert(2, insertAt = 0, cols = cols))
        // Same cell, insert to its RIGHT: stays col 0, but the stride grows.
        assertEquals(3, TableGrid.reindexAfterColumnInsert(2, insertAt = 1, cols = cols))
    }

    @Test
    fun `inserting a column shifts later columns right`() {
        val cols = 3
        // (row 0, col 2) -> (row 0, col 3) in a 4-wide grid.
        assertEquals(3, TableGrid.reindexAfterColumnInsert(2, insertAt = 1, cols = cols))
        // (row 0, col 0) is left of the insert and keeps its place.
        assertEquals(0, TableGrid.reindexAfterColumnInsert(0, insertAt = 1, cols = cols))
    }

    @Test
    fun `deleting a column reports its own cells as gone and narrows the rest`() {
        val cols = 3
        assertEquals(-1, TableGrid.reindexAfterColumnDelete(1, deleteAt = 1, cols = cols))
        // (row 1, col 0) is index 3 at 3 wide, index 2 at 2 wide.
        assertEquals(2, TableGrid.reindexAfterColumnDelete(3, deleteAt = 1, cols = cols))
    }

    @Test
    fun `insert then delete of the same column is the identity`() {
        val cols = 3
        for (index in 0 until 9) {
            val after = TableGrid.reindexAfterColumnInsert(index, insertAt = 1, cols = cols)
            assertEquals(
                index,
                TableGrid.reindexAfterColumnDelete(after, deleteAt = 1, cols = cols + 1),
            )
        }
    }

    @Test
    fun `reindexing never collides two cells onto one index`() {
        val cols = 3
        val moved = (0 until 9).map { TableGrid.reindexAfterRowInsert(it, 1, cols) }
        assertEquals("two cells landed on the same index", moved.size, moved.toSet().size)
        assertTrue(moved.none { it < 0 })
    }

    // --- Growing a table from its edge buttons ---

    @Test
    fun `inserting a row grows the grid and keeps it contiguous`() {
        val before = TableGrid.create(0f, 0f, rows = 2, cols = 3, cellWidth = 10f, cellHeight = 5f)
        val after = TableGrid.withRowInserted(before, insertAt = 1)

        assertEquals(3, after.rows)
        assertEquals(3, after.cols)
        assertEquals(9, after.cells.size)
        after.cells.forEachIndexed { index, cell ->
            assertEquals("index $index row", index / 3, cell.row)
            assertEquals("index $index col", index % 3, cell.col)
        }
        // Still gapless vertically: each row starts where the last one ended.
        for (row in 1 until after.rows) {
            val above = after.cells.first { it.row == row - 1 && it.col == 0 }
            val here = after.cells.first { it.row == row && it.col == 0 }
            assertEquals(above.bottom, here.top, 0.001f)
        }
    }

    @Test
    fun `inserting a column grows the grid and keeps it contiguous`() {
        val before = TableGrid.create(0f, 0f, rows = 2, cols = 2, cellWidth = 10f, cellHeight = 5f)
        val after = TableGrid.withColumnInserted(before, insertAt = 2)

        assertEquals(2, after.rows)
        assertEquals(3, after.cols)
        assertEquals(6, after.cells.size)
        for (col in 1 until after.cols) {
            val left = after.cells.first { it.row == 0 && it.col == col - 1 }
            val here = after.cells.first { it.row == 0 && it.col == col }
            assertEquals(left.right, here.left, 0.001f)
        }
    }

    @Test
    fun `inserting at either end is accepted and anchored at the origin`() {
        val before = TableGrid.create(7f, 9f, rows = 2, cols = 2, cellWidth = 10f, cellHeight = 5f)

        // Top and left edges, the cases the edge buttons pass as 0.
        val topped = TableGrid.withRowInserted(before, insertAt = 0)
        val lefted = TableGrid.withColumnInserted(before, insertAt = 0)
        assertEquals(3, topped.rows)
        assertEquals(3, lefted.cols)

        // The table keeps its origin rather than drifting: growing downward
        // must not walk the whole grid up the board.
        assertEquals(7f, topped.cells.first().left, 0.001f)
        assertEquals(9f, topped.cells.first().top, 0.001f)
        assertEquals(7f, lefted.cells.first().left, 0.001f)
    }

    @Test
    fun `an out of range insert is clamped rather than throwing`() {
        val before = TableGrid.create(0f, 0f, rows = 2, cols = 2)
        assertEquals(3, TableGrid.withRowInserted(before, insertAt = 99).rows)
        assertEquals(3, TableGrid.withColumnInserted(before, insertAt = -5).cols)
    }

    @Test
    fun `a stroke's retagged cell still sits under the cell it moved to`() {
        // The corruption this guards: ink retagged to an index whose rect is
        // somewhere else entirely, which only shows up after a reload.
        val cols = 3
        val before = TableGrid.create(0f, 0f, rows = 2, cols = cols, cellWidth = 10f, cellHeight = 5f)
        val after = TableGrid.withRowInserted(before, insertAt = 1)

        for (oldIndex in before.cells.indices) {
            val newIndex = TableGrid.reindexAfterRowInsert(oldIndex, insertAt = 1, cols = cols)
            val old = before.cells[oldIndex]
            val new = after.cells[newIndex]
            // Same column, and the row either stayed or moved down by one.
            assertEquals(old.col, new.col)
            assertTrue(new.row == old.row || new.row == old.row + 1)
        }
    }
}
