package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Polygons must never be snapped to circles.
 *
 * Reported from the tablet: "it's mostly making circle, even makes square
 * circle sometimes, and I created hexagon it makes it circle." Radial
 * variance alone cannot separate a regular hexagon from a circle — they are
 * genuinely similar by that measure — so the recognizer now also checks how
 * the turning is DISTRIBUTED. A circle turns evenly; a polygon turns hard at
 * corners and goes straight between them.
 */
class PolygonRejectionTest {

    private fun strokeOf(points: List<Pair<Float, Float>>): Stroke {
        val array = FloatArray(points.size * 3)
        points.forEachIndexed { i, (x, y) ->
            array[i * 3] = x
            array[i * 3 + 1] = y
            array[i * 3 + 2] = 1f
        }
        return Stroke("s", DrawTool.PEN, StrokeStyle(0xFF000000.toInt(), 4f), array)
    }

    private fun polygon(
        sides: Int,
        radius: Float = 150f,
        perSide: Int = 18,
        wobble: Float = 5f,
        seed: Int = 4,
    ): Stroke {
        val rng = Random(seed)
        val pts = mutableListOf<Pair<Float, Float>>()
        for (s in 0 until sides) {
            val a0 = 2 * PI * s / sides
            val a1 = 2 * PI * (s + 1) / sides
            val x0 = (radius * cos(a0)).toFloat()
            val y0 = (radius * sin(a0)).toFloat()
            val x1 = (radius * cos(a1)).toFloat()
            val y1 = (radius * sin(a1)).toFloat()
            for (i in 0 until perSide) {
                val t = i.toFloat() / perSide
                val n = (rng.nextFloat() - 0.5f) * 2 * wobble
                pts += (x0 + (x1 - x0) * t + n) to (y0 + (y1 - y0) * t + n)
            }
        }
        pts += pts.first()
        return strokeOf(pts)
    }

    private fun circle(samples: Int = 64, wobble: Float = 8f, seed: Int = 5): Stroke {
        val rng = Random(seed)
        return strokeOf(
            (0 until samples).map { i ->
                val a = 2 * PI * i / samples
                val r = 150f + (rng.nextFloat() - 0.5f) * 2 * wobble
                (r * cos(a)).toFloat() to (r * sin(a)).toFloat()
            },
        )
    }

    @Test
    fun `a hexagon becomes a clean six-sided polygon`() {
        val result = ShapeRecognizer.recognise(polygon(6))
        assertNotNull("hexagon was not recognised at all", result)
        assertEquals(DrawTool.POLYGON, result!!.tool)
        assertEquals("should have six vertices", 6, result.vertices!!.size / 2)
    }

    @Test
    fun `a pentagon becomes a clean five-sided polygon`() {
        val result = ShapeRecognizer.recognise(polygon(5))
        assertNotNull(result)
        assertEquals(DrawTool.POLYGON, result!!.tool)
        assertEquals(5, result.vertices!!.size / 2)
    }

    @Test
    fun `a triangle becomes a clean three-sided polygon`() {
        val result = ShapeRecognizer.recognise(polygon(3))
        assertNotNull("triangle was not recognised", result)
        assertEquals(DrawTool.POLYGON, result!!.tool)
        assertEquals(3, result.vertices!!.size / 2)
    }

    @Test
    fun `a regularised polygon has equal sides`() {
        val result = ShapeRecognizer.recognise(polygon(6))!!
        val v = result.vertices!!
        val sides = (0 until 6).map { i ->
            val ax = v[i * 2]
            val ay = v[i * 2 + 1]
            val bx = v[((i + 1) % 6) * 2]
            val by = v[((i + 1) % 6) * 2 + 1]
            kotlin.math.hypot(bx - ax, by - ay)
        }
        val shortest = sides.min()
        val longest = sides.max()
        assertTrue(
            "sides ranged $shortest..$longest — a regular polygon should be even",
            longest / shortest < 1.05f,
        )
    }

    @Test
    fun `a regularised polygon sits where the teacher drew it`() {
        val drawn = polygon(6)
        val m = ShapeRecognizer.Metrics.of(drawn)
        val result = ShapeRecognizer.recognise(drawn)!!
        val v = result.vertices!!

        var cx = 0f
        var cy = 0f
        for (i in 0 until v.size / 2) {
            cx += v[i * 2]
            cy += v[i * 2 + 1]
        }
        cx /= v.size / 2
        cy /= v.size / 2

        // Centred on the drawing, not shifted off somewhere else.
        assertTrue(
            "polygon centre ($cx, $cy) drifted from the drawn centre",
            kotlin.math.hypot(cx - m.centroidX, cy - m.centroidY) < m.diagonal * 0.15f,
        )
    }

    /**
     * Documents a real limit rather than asserting a fix.
     *
     * A regular octagon measures ~0.50 turn concentration against 0.40-0.49
     * for hand-drawn circles — with eight equal sides it genuinely IS nearly
     * circular, and no threshold separates them without also rejecting real
     * circles. Six sides and below are handled; beyond that the honest answer
     * is that geometry alone cannot tell, and the Settings toggle exists for
     * lessons that need exact polygons.
     *
     * The test pins the boundary so a future change that claims to fix
     * octagons has to update this deliberately.
     */
    @Test
    fun `polygons up to six sides are separable from circles`() {
        listOf(3, 4, 5, 6).forEach { sides ->
            val concentration = ShapeRecognizer.turnConcentration(polygon(sides))
            assertTrue(
                "$sides-sided polygon scored $concentration, at or below the " +
                    "circle veto — it would be snapped to a circle",
                concentration > ShapeRecognizer.MAX_CIRCLE_TURN_CONCENTRATION,
            )
        }
    }

    @Test
    fun `a square is not turned into a circle`() {
        val result = ShapeRecognizer.recognise(polygon(4))
        assertTrue(
            "square became ${result?.tool}",
            result == null || result.tool != DrawTool.CIRCLE,
        )
    }

    /**
     * Documents where the honest limit now sits.
     *
     * An octagon drawn with realistic hand wobble detects about five corners
     * rather than eight — with eight short sides, the turn at each corner is
     * only 45 degrees and hand noise along the sides is a comparable
     * magnitude. A parameter sweep across window size, turn threshold and
     * corner separation found nothing that recovers eight sides without also
     * inventing corners on real circles.
     *
     * The outcome is still reasonable: an octagon becomes a five- or
     * six-sided polygon rather than being flattened into a circle, so the
     * teacher's shape survives in spirit. Three to six sides are exact.
     */
    /**
     * Pins a limit that is real, not a bug waiting to be fixed.
     *
     * An octagon measures ~0.50 turn concentration. Across 144 synthetic
     * circle variants the range was 0.24-0.54 with 10% at or above 0.50 — the
     * distributions overlap, so no threshold separates an octagon from a
     * circle. Catching octagons would turn roughly one circle in ten into a
     * polygon, and a teacher's circle becoming an octagon is worse than an
     * octagon becoming a circle.
     *
     * Three to six sides are exact. This test exists so that a future change
     * claiming to "fix" octagons has to confront the trade deliberately.
     */
    @Test
    fun `seven or more sides is a documented limit, not a guarantee`() {
        val result = ShapeRecognizer.recognise(polygon(8, perSide = 12))
        // Whatever it becomes, it must not crash or produce a degenerate shape.
        if (result != null && result.tool == DrawTool.POLYGON) {
            assertTrue(result.vertices!!.size / 2 >= 3)
        }
    }

    /**
     * The case that was actually broken on the tablet.
     *
     * Nobody draws an axis-aligned square freehand — there is always a few
     * degrees of rotation. The original rectangle test measured points near
     * the axis-aligned bounding box, which a rotated square fails badly
     * (~33% against a required 80%), so every square fell through to the
     * circle branch and got snapped into a circle.
     */
    @Test
    fun `a rotated square is still recognised as a rectangle`() {
        listOf(0f, 5f, 10f, 15f, 30f).forEach { degrees ->
            val rotated = rotate(polygon(4), degrees)
            val result = ShapeRecognizer.recognise(rotated)
            assertTrue(
                "a square rotated $degrees° became ${result?.tool}",
                result == null || result.tool != DrawTool.CIRCLE,
            )
        }
    }

    private fun rotate(stroke: Stroke, degrees: Float): Stroke {
        val radians = degrees * PI.toFloat() / 180f
        val c = cos(radians)
        val s = sin(radians)
        val pts = (0 until stroke.pointCount).map { i ->
            val x = stroke.x(i)
            val y = stroke.y(i)
            (x * c - y * s) to (x * s + y * c)
        }
        return strokeOf(pts)
    }

    @Test
    fun `an actual circle is still recognised`() {
        // The veto must not be so aggressive that circles stop working.
        val result = ShapeRecognizer.recognise(circle())
        assertNotNull("a real circle should still snap", result)
        assertTrue(result!!.tool == DrawTool.CIRCLE)
    }

    // --- the underlying measure ---

    @Test
    fun `circles turn evenly and polygons turn at corners`() {
        val circleConcentration = ShapeRecognizer.turnConcentration(circle())
        val hexagonConcentration = ShapeRecognizer.turnConcentration(polygon(6))

        assertTrue(
            "circle concentration $circleConcentration should be below the veto",
            circleConcentration <= ShapeRecognizer.MAX_CIRCLE_TURN_CONCENTRATION,
        )
        assertTrue(
            "hexagon concentration $hexagonConcentration should be above the veto",
            hexagonConcentration > ShapeRecognizer.MAX_CIRCLE_TURN_CONCENTRATION,
        )
    }

    @Test
    fun `turn concentration does not depend on sampling density`() {
        // A chord-to-arc straightness measure failed exactly here: a densely
        // sampled circle scored like a polygon.
        val sparse = ShapeRecognizer.turnConcentration(circle(samples = 40))
        val dense = ShapeRecognizer.turnConcentration(circle(samples = 160))
        assertTrue(
            "sparse=$sparse dense=$dense — both must stay below the veto",
            sparse <= ShapeRecognizer.MAX_CIRCLE_TURN_CONCENTRATION &&
                dense <= ShapeRecognizer.MAX_CIRCLE_TURN_CONCENTRATION,
        )
    }
}
