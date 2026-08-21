package com.smartboard.teach.feature.whiteboard.container

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import com.smartboard.teach.feature.whiteboard.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the world-coordinate decision.
 *
 * Contained ink is stored in world coordinates, exactly like free ink, so the
 * renderer, eraser, marquee and export all keep working untouched. The price is
 * that moving a container must move its ink by the same delta. If someone ever
 * "optimises" strokes to container-local coordinates, these fail.
 */
class ContainerTransformTest {

    private fun inkAt(x: Float, y: Float, containerId: String?, cellIndex: Int): Stroke =
        Stroke(
            id = "ink",
            tool = DrawTool.PEN,
            style = StrokeStyle(0xFF000000.toInt(), 4f),
            points = floatArrayOf(x, y, 1f, x + 5f, y + 5f, 1f),
            containerId = containerId,
            cellIndex = cellIndex,
        )

    @Test
    fun `ink written in a cell stays inside it after the container moves`() {
        val table = TableGrid.create(0f, 0f, rows = 1, cols = 1, cellWidth = 100f, cellHeight = 100f)
        val ink = inkAt(40f, 40f, table.id, 0)

        val dx = 500f
        val dy = 300f
        val movedTable = table.copy(
            x = table.x + dx,
            y = table.y + dy,
            cells = table.cells.map {
                it.copy(
                    left = it.left + dx, top = it.top + dy,
                    right = it.right + dx, bottom = it.bottom + dy,
                )
            },
        )
        val movedInk = Selection.translateStroke(ink, dx, dy)

        val cell = movedTable.cells[0]
        for (i in 0 until movedInk.pointCount) {
            assertTrue(
                "point $i escaped its cell",
                cell.contains(movedInk.x(i), movedInk.y(i)),
            )
        }
    }

    @Test
    fun `translate keeps the container tag`() {
        val ink = inkAt(10f, 10f, "table-1", 3)
        val moved = Selection.translateStroke(ink, 50f, 50f)
        assertEquals("table-1", moved.containerId)
        assertEquals(3, moved.cellIndex)
    }

    @Test
    fun `scale keeps the container tag`() {
        val ink = inkAt(10f, 10f, "table-1", 3)
        val scaled = Selection.scaleStroke(ink, 0f, 0f, 2f, 2f)
        assertEquals("table-1", scaled.containerId)
        assertEquals(3, scaled.cellIndex)
    }

    @Test
    fun `rotate keeps the container tag`() {
        val ink = inkAt(10f, 10f, "table-1", 3)
        val rotated = Selection.rotateStroke(ink, 0f, 0f, 1.2f)
        assertEquals("table-1", rotated.containerId)
        assertEquals(3, rotated.cellIndex)
    }

    @Test
    fun `duplicate keeps the container tag but takes a new id`() {
        val ink = inkAt(10f, 10f, "table-1", 3)
        val copy = Selection.duplicateStrokes(listOf(ink)).first()
        assertEquals("table-1", copy.containerId)
        assertEquals(3, copy.cellIndex)
        assertTrue(copy.id != ink.id)
    }

    @Test
    fun `free ink has no container and keeps none`() {
        val ink = inkAt(10f, 10f, null, -1)
        val moved = Selection.translateStroke(ink, 5f, 5f)
        assertNull(moved.containerId)
        assertEquals(-1, moved.cellIndex)
    }

    @Test
    fun `copyWith preserves the tag through a tool change`() {
        // Shape snapping swaps the tool and points; the cell must survive, or
        // the ink jumps out of the table the moment it is recognised.
        val ink = inkAt(10f, 10f, "table-1", 2)
        val snapped = ink.copyWith(tool = DrawTool.CIRCLE)
        assertEquals("table-1", snapped.containerId)
        assertEquals(2, snapped.cellIndex)
        assertEquals(DrawTool.CIRCLE, snapped.tool)
    }
}
