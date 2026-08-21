package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ShapeRecognizerTest {

    private fun strokeOf(points: List<Pair<Float, Float>>): Stroke {
        val array = FloatArray(points.size * 3)
        points.forEachIndexed { i, (x, y) ->
            array[i * 3] = x
            array[i * 3 + 1] = y
            array[i * 3 + 2] = 1f
        }
        return Stroke("s", DrawTool.PEN, StrokeStyle(0xFF000000.toInt(), 4f), array)
    }

    /** @param wobble world units of random noise added to each sample. */
    private fun circle(
        cx: Float, cy: Float, r: Float,
        samples: Int = 48,
        wobble: Float = 0f,
        seed: Int = 1,
    ): Stroke {
        val rng = Random(seed)
        return strokeOf(
            (0 until samples).map { i ->
                val a = 2 * PI * i / samples
                val jitter = if (wobble == 0f) 0f else (rng.nextFloat() - 0.5f) * 2 * wobble
                val rr = r + jitter
                (cx + rr * cos(a)).toFloat() to (cy + rr * sin(a)).toFloat()
            },
        )
    }

    private fun rectangle(
        left: Float, top: Float, right: Float, bottom: Float,
        perSide: Int = 14,
        wobble: Float = 0f,
        seed: Int = 2,
    ): Stroke {
        val rng = Random(seed)
        fun n() = if (wobble == 0f) 0f else (rng.nextFloat() - 0.5f) * 2 * wobble
        val pts = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until perSide) {
            val t = i.toFloat() / perSide
            pts += (left + (right - left) * t + n()) to (top + n())
        }
        for (i in 0 until perSide) {
            val t = i.toFloat() / perSide
            pts += (right + n()) to (top + (bottom - top) * t + n())
        }
        for (i in 0 until perSide) {
            val t = i.toFloat() / perSide
            pts += (right - (right - left) * t + n()) to (bottom + n())
        }
        for (i in 0 until perSide) {
            val t = i.toFloat() / perSide
            pts += (left + n()) to (bottom - (bottom - top) * t + n())
        }
        pts += (left + n()) to (top + n())
        return strokeOf(pts)
    }

    private fun line(
        x0: Float, y0: Float, x1: Float, y1: Float,
        samples: Int = 20,
        wobble: Float = 0f,
        seed: Int = 3,
    ): Stroke {
        val rng = Random(seed)
        return strokeOf(
            (0 until samples).map { i ->
                val t = i.toFloat() / (samples - 1)
                val n = if (wobble == 0f) 0f else (rng.nextFloat() - 0.5f) * 2 * wobble
                (x0 + (x1 - x0) * t) to (y0 + (y1 - y0) * t + n)
            },
        )
    }

    // --- circles -----------------------------------------------------------

    @Test
    fun `recognises a clean circle`() {
        val result = ShapeRecognizer.recognise(circle(200f, 200f, 100f))
        assertNotNull(result)
        assertEquals(DrawTool.CIRCLE, result!!.tool)
    }

    @Test
    fun `recognises a hand-drawn wobbly circle`() {
        // ~8% radial wobble is what a person actually draws on a board.
        val result = ShapeRecognizer.recognise(circle(300f, 300f, 120f, wobble = 10f))
        assertNotNull("a realistically wobbly circle should still snap", result)
        assertEquals(DrawTool.CIRCLE, result!!.tool)
    }

    @Test
    fun `circle endpoints describe its bounding box`() {
        val result = ShapeRecognizer.recognise(circle(200f, 200f, 100f))!!
        val e = result.endpoints
        assertEquals(100f, e[0], 6f)
        assertEquals(100f, e[1], 6f)
        assertEquals(300f, e[2], 6f)
        assertEquals(300f, e[3], 6f)
    }

    @Test
    fun `a circle drawn with a gap still counts as closed`() {
        // People rarely join the ends exactly.
        val partial = strokeOf(
            (0 until 44).map { i ->
                val a = 2 * PI * i / 48
                (200 + 100 * cos(a)).toFloat() to (200 + 100 * sin(a)).toFloat()
            },
        )
        val result = ShapeRecognizer.recognise(partial)
        assertNotNull(result)
        assertEquals(DrawTool.CIRCLE, result!!.tool)
    }

    @Test
    fun `a very flat ellipse is not snapped to a circle`() {
        val flat = strokeOf(
            (0 until 48).map { i ->
                val a = 2 * PI * i / 48
                (200 + 300 * cos(a)).toFloat() to (200 + 40 * sin(a)).toFloat()
            },
        )
        val result = ShapeRecognizer.recognise(flat)
        assertTrue(
            "a 7:1 ellipse must not become a circle",
            result == null || result.tool != DrawTool.CIRCLE,
        )
    }

    // --- rectangles --------------------------------------------------------

    @Test
    fun `recognises a clean rectangle`() {
        val result = ShapeRecognizer.recognise(rectangle(100f, 100f, 400f, 250f))
        assertNotNull(result)
        assertEquals(DrawTool.RECT, result!!.tool)
    }

    @Test
    fun `recognises a wobbly rectangle`() {
        val result = ShapeRecognizer.recognise(
            rectangle(100f, 100f, 400f, 260f, wobble = 6f),
        )
        assertNotNull("a hand-drawn rectangle should still snap", result)
        assertEquals(DrawTool.RECT, result!!.tool)
    }

    @Test
    fun `rectangle endpoints describe its bounding box`() {
        val result = ShapeRecognizer.recognise(rectangle(50f, 60f, 350f, 200f))!!
        val e = result.endpoints
        assertEquals(50f, e[0], 8f)
        assertEquals(60f, e[1], 8f)
        assertEquals(350f, e[2], 8f)
        assertEquals(200f, e[3], 8f)
    }

    @Test
    fun `a near-square rectangle is not mistaken for a circle`() {
        // The dangerous case: a square has low radial variance too.
        val result = ShapeRecognizer.recognise(rectangle(100f, 100f, 300f, 300f))
        assertNotNull(result)
        assertEquals(
            "a square must snap to RECT, not CIRCLE",
            DrawTool.RECT, result!!.tool,
        )
    }

    // --- lines -------------------------------------------------------------

    @Test
    fun `recognises a straight line`() {
        val result = ShapeRecognizer.recognise(line(0f, 0f, 400f, 100f))
        assertNotNull(result)
        assertEquals(DrawTool.LINE, result!!.tool)
    }

    @Test
    fun `recognises a slightly shaky line`() {
        val result = ShapeRecognizer.recognise(line(0f, 0f, 500f, 0f, wobble = 8f))
        assertNotNull("a hand-drawn straight line should snap", result)
        assertEquals(DrawTool.LINE, result!!.tool)
    }

    @Test
    fun `line endpoints are preserved exactly`() {
        val result = ShapeRecognizer.recognise(line(10f, 20f, 410f, 220f))!!
        val e = result.endpoints
        assertEquals(10f, e[0], 0.01f)
        assertEquals(20f, e[1], 0.01f)
        assertEquals(410f, e[2], 0.01f)
        assertEquals(220f, e[3], 0.01f)
    }

    @Test
    fun `a deliberate curve is not straightened`() {
        val arc = strokeOf(
            (0..20).map { i ->
                val t = i / 20f
                val x = t * 400f
                // A pronounced bow — clearly meant as a curve.
                val y = 120f * kotlin.math.sin(t * PI).toFloat()
                x to y
            },
        )
        val result = ShapeRecognizer.recognise(arc)
        assertTrue(
            "a bowed curve must not become a straight line",
            result == null || result.tool != DrawTool.LINE,
        )
    }

    // --- negatives: leaving ink alone is the safe failure ------------------

    @Test
    fun `a scribble is left alone`() {
        val rng = Random(7)
        val scribble = strokeOf(
            (0 until 60).map {
                (rng.nextFloat() * 300f) to (rng.nextFloat() * 300f)
            },
        )
        assertNull("random scribble must not be snapped", ShapeRecognizer.recognise(scribble))
    }

    @Test
    fun `handwriting-like strokes are left alone`() {
        // A cursive "e"-ish loop: doubles back, so path length far exceeds
        // the diagonal.
        val pts = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until 40) {
            val t = i / 40f
            pts += (t * 60f) to (20f * kotlin.math.sin(t * 6 * PI).toFloat())
        }
        assertNull(ShapeRecognizer.recognise(strokeOf(pts)))
    }

    @Test
    fun `a tiny stroke is left alone`() {
        val tiny = circle(10f, 10f, 5f)
        assertNull("small marks must not be snapped", ShapeRecognizer.recognise(tiny))
    }

    @Test
    fun `a stroke with too few points is left alone`() {
        val sparse = strokeOf(listOf(0f to 0f, 50f to 50f, 100f to 0f))
        assertNull(ShapeRecognizer.recognise(sparse))
    }

    @Test
    fun `an empty stroke is left alone`() {
        assertNull(
            ShapeRecognizer.recognise(
                Stroke("s", DrawTool.PEN, StrokeStyle(0, 4f), FloatArray(0)),
            ),
        )
    }

    @Test
    fun `a single point is left alone`() {
        assertNull(ShapeRecognizer.recognise(strokeOf(listOf(100f to 100f))))
    }

    // --- confidence --------------------------------------------------------

    @Test
    fun `a clean shape scores higher than a rough one`() {
        val clean = ShapeRecognizer.recognise(circle(200f, 200f, 100f))!!
        val rough = ShapeRecognizer.recognise(circle(200f, 200f, 100f, wobble = 14f))!!
        assertTrue(
            "cleaner drawing should be more confident",
            clean.confidence >= rough.confidence,
        )
    }

    @Test
    fun `every result clears the minimum confidence`() {
        listOf(
            circle(200f, 200f, 100f),
            rectangle(100f, 100f, 400f, 250f),
            line(0f, 0f, 400f, 100f),
        ).forEach { stroke ->
            val r = ShapeRecognizer.recognise(stroke)
            if (r != null) {
                assertTrue(
                    "confidence ${r.confidence} below threshold",
                    r.confidence >= ShapeRecognizer.MIN_CONFIDENCE,
                )
            }
        }
    }

    // --- metrics -----------------------------------------------------------

    @Test
    fun `metrics compute the bounding box`() {
        val m = ShapeRecognizer.Metrics.of(rectangle(10f, 20f, 110f, 220f))
        assertEquals(10f, m.minX, 1f)
        assertEquals(20f, m.minY, 1f)
        assertEquals(110f, m.maxX, 1f)
        assertEquals(220f, m.maxY, 1f)
        assertEquals(100f, m.width, 1f)
        assertEquals(200f, m.height, 1f)
    }

    @Test
    fun `a closed shape is detected as closed`() {
        assertTrue(ShapeRecognizer.Metrics.of(circle(200f, 200f, 100f)).isClosed)
    }

    @Test
    fun `an open line is detected as open`() {
        assertTrue(!ShapeRecognizer.Metrics.of(line(0f, 0f, 400f, 0f)).isClosed)
    }

    @Test
    fun `aspect ratio is orientation independent`() {
        val wide = ShapeRecognizer.Metrics.of(rectangle(0f, 0f, 400f, 100f))
        val tall = ShapeRecognizer.Metrics.of(rectangle(0f, 0f, 100f, 400f))
        assertEquals(wide.aspectRatio, tall.aspectRatio, 0.1f)
    }
}
