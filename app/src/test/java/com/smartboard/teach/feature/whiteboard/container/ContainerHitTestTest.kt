package com.smartboard.teach.feature.whiteboard.container

import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.ContainerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerHitTestTest {

    private fun table(id: String = "t", x: Float = 0f, y: Float = 0f) =
        TableGrid.create(x = x, y = y, rows = 2, cols = 2, cellWidth = 100f, cellHeight = 50f, id = id)

    @Test
    fun `a point inside a cell resolves to that cell`() {
        val hit = ContainerHitTest.cellAt(listOf(table()), 50f, 25f)
        assertEquals("t", hit?.containerId)
        assertEquals(0, hit?.cellIndex)
    }

    @Test
    fun `cells are indexed row-major`() {
        val t = table()
        // (row 1, col 1) is the bottom-right cell of a 2x2 grid.
        assertEquals(3, ContainerHitTest.cellAt(listOf(t), 150f, 75f)?.cellIndex)
        assertEquals(1, ContainerHitTest.cellAt(listOf(t), 150f, 25f)?.cellIndex)
        assertEquals(2, ContainerHitTest.cellAt(listOf(t), 50f, 75f)?.cellIndex)
    }

    @Test
    fun `a shared edge belongs to exactly one cell`() {
        // Adjacent cells share an edge. If containment were closed on both
        // sides they would BOTH claim x=100, and which one won would depend on
        // iteration order -- stable in a test, arbitrary on a board.
        val t = table()
        val onEdge = ContainerHitTest.cellAt(listOf(t), 100f, 25f)
        assertEquals("the right-hand cell owns the shared edge", 1, onEdge?.cellIndex)
    }

    @Test
    fun `a point outside every container is free ink`() {
        assertNull(ContainerHitTest.cellAt(listOf(table()), 900f, 900f))
    }

    @Test
    fun `a point in the gap between two containers is free ink`() {
        val a = table("a", 0f, 0f)
        val b = table("b", 500f, 0f)
        assertNull(ContainerHitTest.cellAt(listOf(a, b), 300f, 25f))
    }

    @Test
    fun `the topmost container wins where two overlap`() {
        // Last in the list is drawn on top, so it must also be written into.
        val under = table("under", 0f, 0f)
        val over = table("over", 0f, 0f)
        assertEquals("over", ContainerHitTest.cellAt(listOf(under, over), 50f, 25f)?.containerId)
    }

    @Test
    fun `isFullyInside is true only when the whole container is enclosed`() {
        val t = table()   // spans 0,0 -> 200,100
        assertTrue(ContainerHitTest.isFullyInside(t, floatArrayOf(-10f, -10f, 300f, 300f)))
        // A marquee across half the table must NOT count, or dragging it would
        // pull some ink out of its cells and leave the grid behind.
        assertFalse(ContainerHitTest.isFullyInside(t, floatArrayOf(-10f, -10f, 120f, 300f)))
    }

    @Test
    fun `containerAt finds a container by bounds even between cells`() {
        assertNotNull(ContainerHitTest.containerAt(listOf(table()), 50f, 25f))
        assertNull(ContainerHitTest.containerAt(listOf(table()), 900f, 900f))
    }

    @Test
    fun `an empty container claims nothing`() {
        val empty = Container(id = "e", kind = ContainerKind.TABLE, x = 0f, y = 0f)
        assertNull(ContainerHitTest.cellAt(listOf(empty), 0f, 0f))
    }
}
