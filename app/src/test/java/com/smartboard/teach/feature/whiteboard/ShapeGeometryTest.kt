package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class ShapeGeometryTest {

    private val box = floatArrayOf(0f, 0f, 100f, 100f)

    private fun outline(tool: DrawTool) =
        ShapeGeometry.outlineFor(tool, box[0], box[1], box[2], box[3])

    private fun allPoints(o: ShapeGeometry.Outline): List<Pair<Float, Float>> =
        (o.visible + o.hidden).flatMap { line ->
            (0 until line.points.size / 2).map { i ->
                line.points[i * 2] to line.points[i * 2 + 1]
            }
        }

    @Test
    fun `every shape stays inside the box the teacher dragged`() {
        // A shape that overflowed its drag box would not match the preview,
        // and selection bounds are computed from the same box.
        DrawTool.entries
            .filter { it.isTwoPointShape }
            .forEach { tool ->
                val o = outline(tool)
                allPoints(o).forEach { (x, y) ->
                    assertTrue("$tool x=$x escaped", x >= -1f && x <= 101f)
                    assertTrue("$tool y=$y escaped", y >= -1f && y <= 101f)
                }
                (o.ovals + o.hiddenOvals).forEach {
                    assertTrue("$tool oval escaped", it.left >= -1f && it.right <= 101f)
                }
            }
    }

    @Test
    fun `every two-point shape produces something to draw`() {
        DrawTool.entries
            .filter { it.isTwoPointShape }
            .forEach { tool ->
                val o = outline(tool)
                val hasGeometry = o.visible.isNotEmpty() || o.ovals.isNotEmpty() ||
                    o.arcs.isNotEmpty()
                assertTrue("$tool draws nothing", hasGeometry)
            }
    }

    @Test
    fun `a shape dragged backwards is the same as one dragged forwards`() {
        // Dragging right-to-left must not mirror or invert the shape.
        val forward = ShapeGeometry.outlineFor(DrawTool.PENTAGON, 0f, 0f, 100f, 100f)
        val backward = ShapeGeometry.outlineFor(DrawTool.PENTAGON, 100f, 100f, 0f, 0f)
        assertEquals(forward.visible.first(), backward.visible.first())
    }

    @Test
    fun `lines keep their direction so an arrow points where it was dragged`() {
        // The one case that must NOT be normalised: normalising would flip the
        // arrowhead to the wrong end.
        val line = ShapeGeometry.outlineFor(DrawTool.ARROW, 100f, 100f, 0f, 0f)
            .visible.first().points
        assertEquals(100f, line[0], 0.01f)
        assertEquals(0f, line[2], 0.01f)
    }

    @Test
    fun `a regular polygon has one vertex per side and all of equal radius`() {
        listOf(5 to DrawTool.PENTAGON, 6 to DrawTool.HEXAGON).forEach { (sides, tool) ->
            val pts = outline(tool).visible.first().points
            assertEquals("$tool vertex count", sides * 2, pts.size)

            val radii = (0 until sides).map { i ->
                hypot(pts[i * 2] - 50f, pts[i * 2 + 1] - 50f)
            }
            radii.forEach {
                assertEquals("$tool is not regular", radii[0], it, 0.5f)
            }
        }
    }

    @Test
    fun `a regular polygon starts with a vertex at the top`() {
        // Conventional orientation: a pentagon drawn point-down reads as wrong
        // even though it is geometrically a pentagon.
        val pts = ShapeGeometry.regular(50f, 50f, 100f, 100f, 5).points
        assertEquals(50f, pts[0], 0.01f)
        assertEquals(0f, pts[1], 0.01f)
    }

    @Test
    fun `a star alternates long and short spokes`() {
        val pts = ShapeGeometry.star(50f, 50f, 100f, 100f).points
        assertEquals("five points means ten vertices", 20, pts.size)

        val radii = (0 until 10).map { i -> hypot(pts[i * 2] - 50f, pts[i * 2 + 1] - 50f) }
        radii.forEachIndexed { i, r ->
            if (i % 2 == 0) {
                assertEquals("outer spoke $i", radii[0], r, 0.5f)
            } else {
                assertTrue("inner spoke $i is not shorter", r < radii[0] - 1f)
            }
        }
    }

    @Test
    fun `solids carry hidden edges so they read as three-dimensional`() {
        // A wireframe with every edge weighted the same is an unreadable
        // tangle; the hidden set is what makes it look like a solid.
        listOf(DrawTool.CUBE, DrawTool.PYRAMID, DrawTool.PRISM, DrawTool.TETRAHEDRON)
            .forEach { tool ->
                assertTrue("$tool has no hidden edges", outline(tool).hidden.isNotEmpty())
            }
        listOf(DrawTool.CYLINDER, DrawTool.CONE, DrawTool.SPHERE).forEach { tool ->
            assertTrue("$tool has no hidden curve", outline(tool).hiddenOvals.isNotEmpty())
        }
    }

    @Test
    fun `flat shapes have no hidden edges`() {
        listOf(DrawTool.RECT, DrawTool.CIRCLE, DrawTool.PENTAGON, DrawTool.STAR)
            .forEach { tool ->
                val o = outline(tool)
                assertTrue("$tool should be flat", o.hidden.isEmpty() && o.hiddenOvals.isEmpty())
            }
    }

    @Test
    fun `a right triangle has a true right angle at its corner`() {
        val pts = outline(DrawTool.RIGHT_TRIANGLE).visible.first().points
        // Corner is the bottom-left vertex; the two legs from it must be
        // axis-aligned, or it is not a right triangle.
        val corner = 4
        val ax = pts[0] - pts[corner]
        val ay = pts[1] - pts[corner + 1]
        val bx = pts[2] - pts[corner]
        val by = pts[3] - pts[corner + 1]
        assertEquals("legs are not perpendicular", 0f, ax * bx + ay * by, 0.01f)
    }

    @Test
    fun `a diamond has its vertices on the box midpoints`() {
        val pts = outline(DrawTool.DIAMOND).visible.first().points
        assertEquals(50f, pts[0], 0.01f)   // top
        assertEquals(0f, pts[1], 0.01f)
        assertEquals(100f, pts[2], 0.01f)  // right
        assertEquals(50f, pts[3], 0.01f)
    }

    @Test
    fun `a rectangle is exactly its drag box`() {
        val pts = outline(DrawTool.RECT).visible.first().points
        assertEquals(8, pts.size)
        assertEquals(0f, pts.filterIndexed { i, _ -> i % 2 == 0 }.min(), 0.01f)
        assertEquals(100f, pts.filterIndexed { i, _ -> i % 2 == 0 }.max(), 0.01f)
    }

    @Test
    fun `a degenerate drag does not crash or produce NaN`() {
        // A tap rather than a drag: zero width and height.
        DrawTool.entries.filter { it.isTwoPointShape }.forEach { tool ->
            val o = ShapeGeometry.outlineFor(tool, 40f, 40f, 40f, 40f)
            allPoints(o).forEach { (x, y) ->
                assertTrue("$tool produced NaN", !x.isNaN() && !y.isNaN())
                assertTrue("$tool produced infinity", abs(x) < 1e6 && abs(y) < 1e6)
            }
        }
    }
}
