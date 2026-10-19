package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeHitTestTest {

    private fun stroke(vararg xy: Float): Stroke {
        val points = FloatArray(xy.size / 2 * 3)
        for (i in 0 until xy.size / 2) {
            points[i * 3] = xy[i * 2]
            points[i * 3 + 1] = xy[i * 2 + 1]
            points[i * 3 + 2] = 1f
        }
        return Stroke(
            id = "s",
            tool = DrawTool.PEN,
            style = StrokeStyle(colorArgb = 0xFF000000.toInt(), baseWidthPx = 4f),
            points = points,
        )
    }

    @Test
    fun `eraser over the middle of a stroke hits`() {
        val s = stroke(0f, 0f, 100f, 0f)
        assertTrue(StrokeHitTest.intersects(s, 50f, 0f, 10f))
    }

    @Test
    fun `eraser far from a stroke misses`() {
        val s = stroke(0f, 0f, 100f, 0f)
        assertFalse(StrokeHitTest.intersects(s, 50f, 500f, 10f))
    }

    @Test
    fun `eraser just within radius hits`() {
        val s = stroke(0f, 0f, 100f, 0f)
        assertTrue(StrokeHitTest.intersects(s, 50f, 9f, 10f))
    }

    @Test
    fun `eraser just outside radius misses`() {
        val s = stroke(0f, 0f, 100f, 0f)
        assertFalse(StrokeHitTest.intersects(s, 50f, 40f, 10f))
    }

    @Test
    fun `a single point stroke can be erased`() {
        val s = stroke(50f, 50f)
        assertTrue(StrokeHitTest.intersects(s, 52f, 52f, 10f))
        assertFalse(StrokeHitTest.intersects(s, 200f, 200f, 10f))
    }

    @Test
    fun `erasing near a vertex of a bent stroke hits`() {
        val s = stroke(0f, 0f, 50f, 0f, 50f, 50f)
        assertTrue(StrokeHitTest.intersects(s, 50f, 25f, 8f))
    }

    @Test
    fun `the gap inside an L shape is not hit`() {
        // Corner at (50,0); the inner area near (5,45) is far from both legs.
        val s = stroke(0f, 0f, 50f, 0f, 50f, 50f)
        assertFalse(StrokeHitTest.intersects(s, 5f, 45f, 8f))
    }

    @Test
    fun `an empty stroke is never hit`() {
        val s = Stroke(
            "s", DrawTool.PEN,
            StrokeStyle(0xFF000000.toInt(), 4f),
            FloatArray(0),
        )
        assertFalse(StrokeHitTest.intersects(s, 0f, 0f, 100f))
    }

    @Test
    fun `distance to segment clamps at the endpoints`() {
        // Beyond point B, the nearest point on the segment is B itself.
        val d2 = StrokeHitTest.pointToSegmentDistanceSquared(
            px = 200f, py = 0f, ax = 0f, ay = 0f, bx = 100f, by = 0f,
        )
        assertEquals(100f * 100f, d2, 0.01f)
    }

    @Test
    fun `distance to a degenerate segment is point distance`() {
        val d2 = StrokeHitTest.pointToSegmentDistanceSquared(
            px = 3f, py = 4f, ax = 0f, ay = 0f, bx = 0f, by = 0f,
        )
        assertEquals(25f, d2, 0.01f)
    }

    @Test
    fun `perpendicular distance is measured correctly`() {
        val d2 = StrokeHitTest.pointToSegmentDistanceSquared(
            px = 50f, py = 30f, ax = 0f, ay = 0f, bx = 100f, by = 0f,
        )
        assertEquals(900f, d2, 0.01f)
    }

    @Test
    fun `a generous eraser radius catches nearby ink`() {
        // ~30dp radius is what the board actually uses.
        val s = stroke(0f, 0f, 100f, 100f)
        assertTrue(StrokeHitTest.intersects(s, 60f, 40f, 30f))
    }

    @Test
    fun `rubbing the middle of a line leaves both ends`() {
        val pieces = StrokeHitTest.cut(stroke(0f, 0f, 100f, 0f), 50f, 0f, 10f)!!
        assertEquals(2, pieces.size)
        // Cut exactly at the eraser's edge, not at the nearest sampled point.
        assertEquals(40f, pieces[0][3], 0.01f)
        assertEquals(60f, pieces[1][0], 0.01f)
        assertEquals(100f, pieces[1][3], 0.01f)
    }

    @Test
    fun `rubbing one end leaves the rest`() {
        val pieces = StrokeHitTest.cut(stroke(0f, 0f, 50f, 0f, 100f, 0f), 100f, 0f, 10f)!!
        assertEquals(1, pieces.size)
        assertEquals(0f, pieces[0][0], 0.01f)
        assertEquals(90f, pieces[0][pieces[0].size - 3], 0.01f)
    }

    @Test
    fun `an eraser covering the whole stroke leaves nothing`() {
        assertEquals(0, StrokeHitTest.cut(stroke(0f, 0f, 5f, 0f), 2f, 0f, 20f)!!.size)
    }

    @Test
    fun `a miss leaves the stroke alone`() {
        assertEquals(null, StrokeHitTest.cut(stroke(0f, 0f, 100f, 0f), 50f, 300f, 10f))
    }

    @Test
    fun `crumbs shorter than the minimum are dropped`() {
        // Leaves 1px at the start: too short to keep.
        val pieces = StrokeHitTest.cut(stroke(0f, 0f, 100f, 0f), 30f, 0f, 29f, minLength = 2f)!!
        assertEquals(1, pieces.size)
        assertEquals(59f, pieces[0][0], 0.01f)
    }

    private val square = floatArrayOf(40f, -10f, 60f, -10f, 60f, 10f, 40f, 10f)

    @Test
    fun `a point is inside a loop only when it is`() {
        assertTrue(StrokeHitTest.insidePolygon(50f, 0f, square))
        assertFalse(StrokeHitTest.insidePolygon(70f, 0f, square))
    }

    @Test
    fun `an area erase keeps the ink either side of the loop`() {
        val s = stroke(0f, 0f, 20f, 0f, 45f, 0f, 55f, 0f, 80f, 0f, 100f, 0f)
        val pieces = StrokeHitTest.cutByPolygon(s, square)!!
        assertEquals(2, pieces.size)
        assertEquals(20f, pieces[0][3], 0.01f)
        assertEquals(80f, pieces[1][0], 0.01f)
    }

    @Test
    fun `an area erase misses ink outside the loop`() {
        assertEquals(null, StrokeHitTest.cutByPolygon(stroke(0f, 50f, 100f, 50f), square))
    }
}
