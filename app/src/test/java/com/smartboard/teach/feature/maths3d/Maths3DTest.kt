package com.smartboard.teach.feature.maths3d

import com.smartboard.teach.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Maths3DTest {

    private val rnd = Random(7)
    private fun jitter() = (rnd.nextDouble() - 0.5) * 0.08

    private fun circle(cx: Double, cy: Double, r: Double, n: Int = 80) =
        (0..n).map { val a = 2 * PI * it / n; P(cx + r * cos(a) + jitter(), cy + r * sin(a) + jitter()) }

    /** Walks the closed polygon's edges with [perEdge] points each. */
    private fun traced(corners: List<P>, perEdge: Int = 20) = buildList {
        for (i in corners.indices) {
            val a = corners[i]; val b = corners[(i + 1) % corners.size]
            for (k in 0 until perEdge) {
                val t = k.toDouble() / perEdge
                add(P(a.x + (b.x - a.x) * t + jitter(), a.y + (b.y - a.y) * t + jitter()))
            }
        }
        add(corners[0])
    }

    private fun regular(n: Int, r: Double) = (0 until n).map { P(r * cos(2 * PI * it / n - PI / 2), r * sin(2 * PI * it / n - PI / 2)) }

    @Test fun recognisesCircle() {
        val s = recognise(circle(1.0, -1.0, 3.0)) as Shape2D.Circle
        assertEquals(3.0, s.r, 0.01)
        assertEquals(1.0, s.cx, 0.01)
    }

    @Test fun recognisesSquare() {
        val s = recognise(traced(listOf(P(-2.0, -2.0), P(2.0, -2.0), P(2.0, 2.0), P(-2.0, 2.0)))) as Shape2D.Rect
        assertTrue(s.square)
        assertEquals(4.0, s.w, 0.01)
    }

    @Test fun recognisesEquilateralTriangleAndHexagon() {
        val tri = recognise(traced(regular(3, 3.0))) as Shape2D.Polygon
        assertEquals(3, tri.pts.size)
        assertTrue(tri.regular)
        val hex = recognise(traced(regular(6, 3.0))) as Shape2D.Polygon
        assertEquals(6, hex.pts.size)
        assertTrue(hex.regular)
    }

    @Test fun openStrokeIsAProfile() {
        val line = (0..30).map { P(1.0 + it * 0.1, -3.0 + it * 0.2) }
        assertTrue(recognise(line) is Shape2D.Profile)
    }

    @Test fun cylinderAndCubeMaths() {
        val cyl = mathsFor(Shape2D.Circle(0.0, 0.0, 3.0), Mode.CYLINDER, 6.0, PiValue.DECIMAL)
        assertEquals("169.56 cm³", cyl.cards.first { it.title == R.string.m3d_card_volume }.answer)
        val cube = PRESETS.first { it.label == R.string.m3d_solid_cube }
        val sheet = mathsFor(cube.shape, cube.mode, cube.h, PiValue.DECIMAL)
        assertEquals(R.string.m3d_solid_cube, (sheet.name as Txt.Res).id)
        assertEquals("64 cm³", sheet.cards.first { it.title == R.string.m3d_card_volume }.answer)
        assertEquals("96 cm²", sheet.cards.first { it.title == R.string.m3d_card_tsa }.answer)
    }

    @Test fun eulerHoldsForPrismsAndPyramids() {
        for (n in 3..8) {
            val shape = Shape2D.Polygon(regular(n, 3.0), R.string.m3d_shape_free, regular = true)
            for (mode in listOf(Mode.PRISM, Mode.PYRAMID)) {
                val e = mathsFor(shape, mode, 4.0, PiValue.DECIMAL).euler!!
                assertEquals("n=$n $mode", 2, e.f + e.v - e.e)
            }
        }
    }

    @Test fun diskMethodMatchesCylinder() {
        // Rectangle 0..3 wide, 0..5 tall, touching the axis → a cylinder.
        val st = revolveStats(revolveLoop(Shape2D.Rect(1.5, -2.5, 3.0, 5.0)))
        assertEquals(PI * 9 * 5, st.volume, 1e-6)
    }

    @Test fun pyramidMeshAndZeroGrowth() {
        for (n in 3..8) {
            val shape = Shape2D.Polygon(regular(n, 3.0), R.string.m3d_shape_free, regular = true)
            assertEquals((n - 2) + n, buildSolid(shape, Mode.PYRAMID, 4.0).triangleCount)
        }
        for (p in PRESETS) buildSolid(p.shape, p.mode, p.h, t = 0.0)
        // A cube shows exactly its 12 edges.
        val cube = PRESETS.first { it.label == R.string.m3d_solid_cube }
        assertEquals(12, buildSolid(cube.shape, cube.mode, cube.h).edges.size)
    }

    @Test fun multiKeepsShapesAndTapSelects() {
        val vm = Maths3DViewModel()
        vm.toggleMulti()
        vm.onStroke(circle(-5.0, 0.0, 2.0))
        vm.onStroke(traced(listOf(P(3.0, -2.0), P(7.0, -2.0), P(7.0, 2.0), P(3.0, 2.0))))
        assertTrue(vm.shape is Shape2D.Rect)
        assertEquals(1, vm.others.size)

        // A tap on the circle makes it the selected piece; the square is kept.
        vm.onStroke(listOf(P(-5.0, 0.0), P(-5.05, 0.02)))
        assertTrue(vm.shape is Shape2D.Circle)
        assertTrue(vm.others.single().shape is Shape2D.Rect)

        vm.undo()
        assertTrue(vm.shape is Shape2D.Rect)

        // Multi off: the next shape replaces everything.
        vm.toggleMulti()
        vm.onStroke(circle(0.0, 0.0, 2.0))
        assertTrue(vm.others.isEmpty())
    }

    @Test fun placedSolidsSitWhereDrawn() {
        val s = placedSolid(Shape2D.Circle(5.0, -3.0, 1.0), Mode.CYLINDER, 2.0)
        val xs = (s.tris.indices step 3).map { s.tris[it] }
        val zs = (s.tris.indices step 3).map { s.tris[it + 2] }
        assertEquals(5.0, (xs.max() + xs.min()) / 2.0, 0.01)
        assertEquals(-3.0, (zs.max() + zs.min()) / 2.0, 0.01)
        val cone = placedSolid(Shape2D.Circle(0.0, 0.0, 1.0), Mode.CONE, 2.0, overlays = false)
        assertTrue(cone.labels.isEmpty())
        assertEquals(s.triangleCount + cone.triangleCount, mergeSolids(listOf(s, cone)).triangleCount)
        assertTrue(contains(Shape2D.Circle(5.0, -3.0, 1.0), P(5.2, -3.1)))
    }
}
