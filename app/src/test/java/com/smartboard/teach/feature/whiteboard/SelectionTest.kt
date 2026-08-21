package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import com.smartboard.teach.domain.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class SelectionTest {

    private fun stroke(id: String = "s", vararg xy: Float, width: Float = 4f): Stroke {
        val points = FloatArray(xy.size / 2 * 3)
        for (i in 0 until xy.size / 2) {
            points[i * 3] = xy[i * 2]
            points[i * 3 + 1] = xy[i * 2 + 1]
            points[i * 3 + 2] = 1f
        }
        return Stroke(id, DrawTool.PEN, StrokeStyle(0xFF000000.toInt(), width), points)
    }

    private fun textBox(x: Float, y: Float, w: Float = 100f, text: String = "hi") =
        TextBox("t", x, y, w, text, 0xFF000000.toInt(), 20f)

    // --- bounds ---

    @Test
    fun `bounds of a single stroke include the stroke width padding`() {
        val b = Selection.boundsOf(listOf(stroke("s", 10f, 10f, 50f, 50f, width = 4f)), emptyList())
        assertTrue(b[0] < 10f)
        assertTrue(b[2] > 50f)
    }

    @Test
    fun `bounds union multiple strokes`() {
        val b = Selection.boundsOf(
            listOf(
                stroke("a", 0f, 0f, 10f, 10f),
                stroke("b", 100f, 200f, 150f, 250f),
            ),
            emptyList(),
        )
        assertTrue(b[0] <= 0f)
        assertTrue(b[2] >= 150f)
        assertTrue(b[3] >= 250f)
    }

    @Test
    fun `empty selection reports empty bounds`() {
        assertTrue(Selection.isEmpty(Selection.boundsOf(emptyList(), emptyList())))
    }

    @Test
    fun `bounds include text boxes`() {
        val b = Selection.boundsOf(emptyList(), listOf(textBox(500f, 400f, w = 200f)))
        assertEquals(500f, b[0], 0.01f)
        assertEquals(700f, b[2], 0.01f)
    }

    // --- marquee ---

    @Test
    fun `marquee selects an intersecting stroke`() {
        val s = stroke("a", 50f, 50f, 150f, 150f)
        val hits = Selection.strokesInMarquee(listOf(s), floatArrayOf(0f, 0f, 100f, 100f))
        assertEquals(1, hits.size)
    }

    @Test
    fun `marquee selects a partially overlapping stroke`() {
        // Intersection, not containment: a rough box round a diagram must work.
        val s = stroke("a", 90f, 90f, 500f, 500f)
        val hits = Selection.strokesInMarquee(listOf(s), floatArrayOf(0f, 0f, 100f, 100f))
        assertEquals(1, hits.size)
    }

    @Test
    fun `marquee ignores a distant stroke`() {
        val s = stroke("a", 900f, 900f, 950f, 950f)
        val hits = Selection.strokesInMarquee(listOf(s), floatArrayOf(0f, 0f, 100f, 100f))
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `normalizeRect handles a drag in any direction`() {
        val r = Selection.normalizeRect(200f, 300f, 50f, 80f)
        assertEquals(50f, r[0], 0.01f)
        assertEquals(80f, r[1], 0.01f)
        assertEquals(200f, r[2], 0.01f)
        assertEquals(300f, r[3], 0.01f)
    }

    // --- tap select ---

    @Test
    fun `tap selects the topmost stroke`() {
        val bottom = stroke("bottom", 0f, 0f, 100f, 0f)
        val top = stroke("top", 0f, 0f, 100f, 0f)
        val hit = Selection.strokeAt(listOf(bottom, top), 50f, 0f, 10f)
        assertEquals("top", hit?.id)
    }

    @Test
    fun `tap on empty space selects nothing`() {
        val s = stroke("a", 0f, 0f, 10f, 10f)
        assertNull(Selection.strokeAt(listOf(s), 900f, 900f, 10f))
    }

    // --- handles ---

    @Test
    fun `handle detection finds each corner`() {
        val b = floatArrayOf(0f, 0f, 100f, 100f)
        assertEquals(Selection.Handle.TOP_LEFT, Selection.handleAt(b, 0f, 0f, 8f))
        assertEquals(Selection.Handle.TOP_RIGHT, Selection.handleAt(b, 100f, 0f, 8f))
        assertEquals(Selection.Handle.BOTTOM_LEFT, Selection.handleAt(b, 0f, 100f, 8f))
        assertEquals(Selection.Handle.BOTTOM_RIGHT, Selection.handleAt(b, 100f, 100f, 8f))
    }

    @Test
    fun `handle detection finds edge handles`() {
        val b = floatArrayOf(0f, 0f, 100f, 100f)
        assertEquals(Selection.Handle.TOP, Selection.handleAt(b, 50f, 0f, 8f))
        assertEquals(Selection.Handle.BOTTOM, Selection.handleAt(b, 50f, 100f, 8f))
        assertEquals(Selection.Handle.LEFT, Selection.handleAt(b, 0f, 50f, 8f))
        assertEquals(Selection.Handle.RIGHT, Selection.handleAt(b, 100f, 50f, 8f))
    }

    @Test
    fun `un-rotating a point by the selection angle recovers the handle frame`() {
        // The chrome draws handles rotated about the centre; the hit-test
        // un-rotates the touch by the same angle and tests the upright rect.
        // Round-tripping a corner must land back on that corner.
        val cx = 50f
        val cy = 50f
        val angle = (PI / 3).toFloat()

        // Rotate the top-left corner forward the way the chrome draws it...
        val c = kotlin.math.cos(angle)
        val sn = kotlin.math.sin(angle)
        val dx = 0f - cx
        val dy = 0f - cy
        val drawnX = cx + dx * c - dy * sn
        val drawnY = cy + dx * sn + dy * c

        // ...then back the way the hit-test does.
        val ic = kotlin.math.cos(-angle)
        val isn = kotlin.math.sin(-angle)
        val bx = drawnX - cx
        val by = drawnY - cy
        assertEquals(0f, cx + bx * ic - by * isn, 0.01f)
        assertEquals(0f, cy + bx * isn + by * ic, 0.01f)
    }

    @Test
    fun `rotate handle is found where the chrome draws it when zoomed`() {
        // The chrome draws the stalk in SCREEN px; handleAt takes the gap in
        // WORLD units. At zoom 0.5 a 28px radius is 56 world units, and the
        // 84px stalk is 168 world units up -- NOT 56*3. Deriving the gap from
        // the radius put the hit-zone 3x too close and made rotation
        // untriggerable at every zoom except 1.0.
        val zoom = 0.5f
        val b = floatArrayOf(0f, 0f, 100f, 100f)
        val radiusWorld = HANDLE_TOUCH_RADIUS_PX / zoom
        val gapWorld = (HANDLE_TOUCH_RADIUS_PX * Selection.ROTATE_HANDLE_GAP) / zoom

        val drawnY = 0f - gapWorld
        assertEquals(
            Selection.Handle.ROTATE,
            Selection.handleAt(b, 50f, drawnY, radiusWorld, gapWorld),
        )
    }

    @Test
    fun `rotate handle wins over the top edge handle where they overlap`() {
        val b = floatArrayOf(0f, 0f, 100f, 100f)
        // A large radius makes the two zones overlap; ROTATE must win, or a
        // rotate drag silently becomes a resize.
        val gap = 8f * Selection.ROTATE_HANDLE_GAP
        assertEquals(
            Selection.Handle.ROTATE,
            Selection.handleAt(b, 50f, -gap, 30f, gap),
        )
    }

    @Test
    fun `rotate handle sits above the box`() {
        val b = floatArrayOf(0f, 0f, 100f, 100f)
        val y = 0f - 8f * Selection.ROTATE_HANDLE_GAP
        assertEquals(Selection.Handle.ROTATE, Selection.handleAt(b, 50f, y, 8f))
    }

    @Test
    fun `no handle in the middle of the box`() {
        assertNull(Selection.handleAt(floatArrayOf(0f, 0f, 100f, 100f), 50f, 50f, 8f))
    }

    // --- transforms ---

    @Test
    fun `translate moves every point`() {
        val moved = Selection.translateStroke(stroke("a", 10f, 20f, 30f, 40f), 5f, -5f)
        assertEquals(15f, moved.x(0), 0.01f)
        assertEquals(15f, moved.y(0), 0.01f)
        assertEquals(35f, moved.x(1), 0.01f)
        assertEquals(35f, moved.y(1), 0.01f)
    }

    @Test
    fun `translate preserves the stroke id and pressure`() {
        val original = stroke("keep-me", 0f, 0f, 10f, 10f)
        val moved = Selection.translateStroke(original, 100f, 100f)
        assertEquals("keep-me", moved.id)
        assertEquals(original.pressure(0), moved.pressure(0), 0.001f)
    }

    @Test
    fun `scale about a pivot leaves the pivot fixed`() {
        val s = stroke("a", 0f, 0f, 100f, 100f)
        val scaled = Selection.scaleStroke(s, pivotX = 0f, pivotY = 0f, scaleX = 2f, scaleY = 2f)
        assertEquals(0f, scaled.x(0), 0.01f)
        assertEquals(200f, scaled.x(1), 0.01f)
    }

    @Test
    fun `scale grows the stroke width`() {
        val s = stroke("a", 0f, 0f, 100f, 100f, width = 4f)
        val scaled = Selection.scaleStroke(s, 0f, 0f, 2f, 2f)
        assertEquals(8f, scaled.style.baseWidthPx, 0.01f)
    }

    @Test
    fun `scale clamps stroke width to sane bounds`() {
        val s = stroke("a", 0f, 0f, 10f, 10f, width = 4f)
        val huge = Selection.scaleStroke(s, 0f, 0f, 1000f, 1000f)
        assertTrue(huge.style.baseWidthPx <= Selection.MAX_STROKE_WIDTH)

        val tiny = Selection.scaleStroke(s, 0f, 0f, 0.0001f, 0.0001f)
        assertTrue(tiny.style.baseWidthPx >= Selection.MIN_STROKE_WIDTH)
    }

    @Test
    fun `rotating by a full turn returns to the start`() {
        val s = stroke("a", 100f, 0f, 200f, 0f)
        val rotated = Selection.rotateStroke(s, 0f, 0f, (2 * PI).toFloat())
        assertEquals(100f, rotated.x(0), 0.05f)
        assertEquals(0f, rotated.y(0), 0.05f)
    }

    @Test
    fun `rotating ninety degrees maps x onto y`() {
        val s = stroke("a", 100f, 0f)
        val rotated = Selection.rotateStroke(s, 0f, 0f, (PI / 2).toFloat())
        assertEquals(0f, rotated.x(0), 0.05f)
        assertEquals(100f, rotated.y(0), 0.05f)
    }

    @Test
    fun `rotation about a pivot leaves the pivot fixed`() {
        val s = stroke("a", 50f, 50f)
        val rotated = Selection.rotateStroke(s, 50f, 50f, 1.234f)
        assertEquals(50f, rotated.x(0), 0.01f)
        assertEquals(50f, rotated.y(0), 0.01f)
    }

    // --- duplicate ---

    @Test
    fun `duplicate assigns new ids`() {
        val original = stroke("original", 0f, 0f, 10f, 10f)
        val copy = Selection.duplicateStrokes(listOf(original)).first()
        assertNotEquals(original.id, copy.id)
    }

    @Test
    fun `duplicate offsets the copy so it is visible`() {
        val original = stroke("a", 0f, 0f, 10f, 10f)
        val copy = Selection.duplicateStrokes(listOf(original)).first()
        assertEquals(Selection.DUPLICATE_OFFSET, copy.x(0) - original.x(0), 0.01f)
    }

    // --- handle drag maths ---

    @Test
    fun `dragging the right handle scales x only`() {
        val b = floatArrayOf(0f, 0f, 100f, 100f)
        val r = Selection.scaleForHandleDrag(Selection.Handle.RIGHT, b, 200f, 50f)
        assertEquals(2f, r[0], 0.01f)   // scaleX
        assertEquals(1f, r[1], 0.01f)   // scaleY unchanged
        assertEquals(0f, r[2], 0.01f)   // pivot on the left edge
    }

    @Test
    fun `dragging the left handle pivots on the right edge`() {
        val b = floatArrayOf(0f, 0f, 100f, 100f)
        val r = Selection.scaleForHandleDrag(Selection.Handle.LEFT, b, -100f, 50f)
        assertEquals(2f, r[0], 0.01f)
        assertEquals(100f, r[2], 0.01f)
    }

    @Test
    fun `dragging a corner scales both axes`() {
        val b = floatArrayOf(0f, 0f, 100f, 100f)
        val r = Selection.scaleForHandleDrag(Selection.Handle.BOTTOM_RIGHT, b, 300f, 200f)
        assertEquals(3f, r[0], 0.01f)
        assertEquals(2f, r[1], 0.01f)
    }

    @Test
    fun `handle drag never flips or collapses the selection`() {
        val b = floatArrayOf(0f, 0f, 100f, 100f)
        // Dragging the right handle far past the left edge would invert it.
        val r = Selection.scaleForHandleDrag(Selection.Handle.RIGHT, b, -500f, 50f)
        assertTrue("scale must stay positive", r[0] >= Selection.MIN_SCALE)
    }

    @Test
    fun `degenerate bounds produce no scaling`() {
        val b = floatArrayOf(10f, 10f, 10f, 10f)
        val r = Selection.scaleForHandleDrag(Selection.Handle.BOTTOM_RIGHT, b, 50f, 50f)
        assertEquals(1f, r[0], 0.01f)
        assertEquals(1f, r[1], 0.01f)
    }

    // --- rect helpers ---

    @Test
    fun `rectsIntersect detects overlap and separation`() {
        assertTrue(
            Selection.rectsIntersect(
                floatArrayOf(0f, 0f, 10f, 10f),
                floatArrayOf(5f, 5f, 15f, 15f),
            ),
        )
        assertFalse(
            Selection.rectsIntersect(
                floatArrayOf(0f, 0f, 10f, 10f),
                floatArrayOf(20f, 20f, 30f, 30f),
            ),
        )
    }

    @Test
    fun `touching edges count as intersecting`() {
        assertTrue(
            Selection.rectsIntersect(
                floatArrayOf(0f, 0f, 10f, 10f),
                floatArrayOf(10f, 10f, 20f, 20f),
            ),
        )
    }

    // --- rotating a RECT ---

    private fun rect(x0: Float, y0: Float, x1: Float, y1: Float): Stroke {
        val pts = floatArrayOf(x0, y0, 1f, x1, y1, 1f)
        return Stroke("r", DrawTool.RECT, StrokeStyle(0xFF000000.toInt(), 4f), pts)
    }

    @Test
    fun `rotating a rect turns it into a four-vertex polygon`() {
        // RECT is drawn with min/max, which rebuilds an upright box and drops
        // any rotation. It must become a POLYGON so the angle survives.
        val rotated = Selection.rotateStroke(rect(0f, 0f, 100f, 100f), 50f, 50f, 0.4f)

        assertEquals(DrawTool.POLYGON, rotated.tool)
        assertEquals(4, rotated.pointCount)
    }

    @Test
    fun `a rotated square is no longer axis aligned`() {
        val rotated = Selection.rotateStroke(rect(0f, 0f, 100f, 100f), 50f, 50f, (PI / 4).toFloat())

        // At 45 degrees the corners sit on the vertical/horizontal midlines --
        // the kite/diamond the teacher asked for. If min/max had been applied
        // the corners would still be at 0 and 100 on both axes.
        val xs = (0 until rotated.pointCount).map { rotated.x(it) }
        val ys = (0 until rotated.pointCount).map { rotated.y(it) }
        val halfDiagonal = (50f * kotlin.math.sqrt(2f))

        assertEquals(50f - halfDiagonal, xs.min(), 0.5f)
        assertEquals(50f + halfDiagonal, xs.max(), 0.5f)
        assertEquals(50f - halfDiagonal, ys.min(), 0.5f)
        assertEquals(50f + halfDiagonal, ys.max(), 0.5f)
    }

    @Test
    fun `rotating a rect preserves its size`() {
        val rotated = Selection.rotateStroke(rect(0f, 0f, 100f, 60f), 50f, 30f, 0.9f)

        // Opposite corners must stay the original diagonal apart.
        val d = kotlin.math.hypot(
            rotated.x(0) - rotated.x(2),
            rotated.y(0) - rotated.y(2),
        )
        assertEquals(kotlin.math.hypot(100f, 60f), d, 0.5f)
    }

    @Test
    fun `re-rotating an already rotated rect does not re-expand it`() {
        // The drag rebuilds from the ORIGINAL each frame, so a second rotation
        // must stay a 4-vertex polygon rather than compounding.
        val once = Selection.rotateStroke(rect(0f, 0f, 100f, 100f), 50f, 50f, 0.3f)
        val twice = Selection.rotateStroke(once, 50f, 50f, 0.3f)

        assertEquals(DrawTool.POLYGON, twice.tool)
        assertEquals(4, twice.pointCount)
    }
}
