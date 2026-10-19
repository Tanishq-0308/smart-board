package com.smartboard.teach.feature.whiteboard.instruments

import com.smartboard.teach.feature.whiteboard.BoardState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the rotate and resize handles shared by the ruler, set squares and
 * protractor. If the hit-test and the drawn badge disagree, or the edge band
 * wins over a handle, the instrument silently stops turning or stretching —
 * it still moves, so nothing looks broken.
 */
class InstrumentHandleTest {

    private fun state(instrument: Instrument) = BoardState().apply {
        instruments.add(instrument)
    }

    private val kinds = listOf(
        InstrumentKind.RULER,
        InstrumentKind.SET_SQUARE_45,
        InstrumentKind.SET_SQUARE_30,
        InstrumentKind.PROTRACTOR,
    )

    @Test
    fun `every instrument but the compass moves, rotates and resizes from its handles`() {
        kinds.forEach { kind ->
            listOf(0f, 1.2f).forEach { rotation ->
                val s = state(Instrument(kind, 300f, 400f, rotation = rotation))
                val (move, rotate, resize) = instrumentHandles(s.instruments[0], s.camera)!!
                assertEquals("$kind move", InstrumentDrag.MOVE, dragModeFor(s, s.instruments[0], move))
                assertEquals("$kind rotate", InstrumentDrag.ROTATE, dragModeFor(s, s.instruments[0], rotate))
                // The protractor has no stretch, as on the reference board.
                resize?.let {
                    assertEquals("$kind resize", InstrumentDrag.RESIZE, dragModeFor(s, s.instruments[0], it))
                }
            }
        }
    }

    @Test
    fun `dragging the resize handle puts the handle under the finger`() {
        kinds.filter { it != InstrumentKind.PROTRACTOR }.forEach { kind ->
            val s = state(Instrument(kind, 300f, 400f, rotation = 0.7f))
            val original = s.instruments[0]
            val bigger = original.copy(lengthCm = 15f)
            val target = instrumentHandles(bigger, s.camera)!!.resize!!
            assertEquals("$kind", 15f, resizedLengthCm(original, s.camera, target), 0.01f)
        }
    }

    @Test
    fun `resize is clamped`() {
        val s = state(Instrument(InstrumentKind.RULER, 0f, 0f))
        val far = androidx.compose.ui.geometry.Offset(1e6f, 50f)
        assertEquals(MAX_LENGTH_CM, resizedLengthCm(s.instruments[0], s.camera, far), 0f)
    }

    @Test
    fun `the compass has no handles of its own`() {
        val s = state(Instrument(InstrumentKind.COMPASS, 0f, 0f))
        assertEquals(null, instrumentHandles(s.instruments[0], s.camera))
    }

    @Test
    fun `rotation snaps to 15 degrees only when close`() {
        val deg = (Math.PI / 180).toFloat()
        assertEquals(45 * deg, InstrumentGeometry.snapRotation(46.5f * deg), 1e-5f)
        assertEquals(40 * deg, InstrumentGeometry.snapRotation(40f * deg), 1e-5f)
    }

    @Test
    fun `the angle shown reads anticlockwise like a maths diagram`() {
        assertEquals(30, InstrumentGeometry.displayDegrees(-(Math.PI / 6).toFloat()))
        assertEquals(330, InstrumentGeometry.displayDegrees((Math.PI / 6).toFloat()))
    }

    @Test
    fun `ink near the protractor rim lands on the rim`() {
        val p = Instrument(InstrumentKind.PROTRACTOR, 500f, 500f, rotation = 0.6f)
        val r = p.protractorRadiusPx
        // A point a little outside the rim, at 50 degrees up from the right end.
        val a = 0.6f - Math.toRadians(50.0).toFloat()
        val px = 500f + kotlin.math.cos(a) * (r + 20f)
        val py = 500f + kotlin.math.sin(a) * (r + 20f)
        val on = InstrumentGeometry.projectOntoGuide(p, 1f, px, py)!!
        assertEquals(r, kotlin.math.hypot(on[0] - 500f, on[1] - 500f), 0.5f)
        assertEquals(20f, InstrumentGeometry.distanceToGuide(p, 1f, px, py)!!, 0.5f)
        // And the band along the rim belongs to the pen, not to a move.
        val s = state(p)
        assertEquals(InstrumentDrag.NONE, dragModeFor(s, s.instruments[0], androidx.compose.ui.geometry.Offset(px, py)))
    }

    @Test
    fun `the compass guides nothing`() {
        assertEquals(null, InstrumentGeometry.projectOntoGuide(Instrument(InstrumentKind.COMPASS, 0f, 0f), 1f, 5f, 5f))
    }

    @Test
    fun `the ruler's close button puts it away`() {
        val s = state(Instrument(InstrumentKind.RULER, 300f, 400f, rotation = 0.5f))
        val close = instrumentHandles(s.instruments[0], s.camera)!!.close!!
        assertEquals(InstrumentDrag.CLOSE, dragModeFor(s, s.instruments[0], close))
    }

    @Test
    fun `rotating turns an instrument about its middle, not its corner`() {
        listOf(InstrumentKind.RULER, InstrumentKind.SET_SQUARE_45, InstrumentKind.PROTRACTOR).forEach { kind ->
            val s = state(Instrument(kind, 300f, 400f, rotation = 0.2f))
            val before = instrumentPivot(s.instruments[0], s.camera)
            val turned = rotatedAboutPivot(s.instruments[0], 1.4f, s.camera)
            val after = instrumentPivot(turned, s.camera)
            assertEquals("$kind x", before.x, after.x, 0.01f)
            assertEquals("$kind y", before.y, after.y, 0.01f)
            assertEquals(1.4f, turned.rotation, 0f)
        }
    }

    @Test
    fun `both of the ruler's long edges rule`() {
        val ruler = Instrument(InstrumentKind.RULER, 0f, 0f, lengthCm = 10f)
        val body = InstrumentGeometry.rulerBodyPx()
        val nearTop = InstrumentGeometry.projectOntoGuide(ruler, 1f, 50f, 5f)!!
        val nearBottom = InstrumentGeometry.projectOntoGuide(ruler, 1f, 50f, body - 5f)!!
        assertEquals(0f, nearTop[1], 0.01f)
        assertEquals(body, nearBottom[1], 0.01f)
    }

    @Test
    fun `set squares close from their own button`() {
        listOf(InstrumentKind.SET_SQUARE_45, InstrumentKind.SET_SQUARE_30).forEach { kind ->
            val s = state(Instrument(kind, 300f, 400f, rotation = 0.5f))
            val close = instrumentHandles(s.instruments[0], s.camera)!!.close!!
            assertEquals("$kind", InstrumentDrag.CLOSE, dragModeFor(s, s.instruments[0], close))
        }
    }

    @Test
    fun `all three sides of a set square rule`() {
        val square = Instrument(InstrumentKind.SET_SQUARE_45, 0f, 0f, lengthCm = 10f)
        val size = 10f * InstrumentGeometry.pxPerCm
        // Just inside the top leg, the side leg, and the hypotenuse.
        assertEquals(0f, InstrumentGeometry.projectOntoGuide(square, 1f, size * 0.4f, 6f)!![1], 0.01f)
        assertEquals(0f, InstrumentGeometry.projectOntoGuide(square, 1f, 6f, size * 0.4f)!![0], 0.01f)
        val onHyp = InstrumentGeometry.projectOntoGuide(square, 1f, size * 0.48f, size * 0.48f)!!
        assertEquals(size, onHyp[0] + onHyp[1], 0.5f)
    }

    @Test
    fun `ink can be drawn inside a set square's window`() {
        val s = state(Instrument(InstrumentKind.SET_SQUARE_45, 0f, 0f, lengthCm = 10f))
        val size = 10f * InstrumentGeometry.pxPerCm
        // A point well inside the window, away from every edge band.
        val inside = androidx.compose.ui.geometry.Offset(size * 0.3f, size * 0.3f)
        assertEquals(InstrumentDrag.NONE, dragModeFor(s, s.instruments[0], inside))
    }

    @Test
    fun `every protractor button is reachable`() {
        val s = state(Instrument(InstrumentKind.PROTRACTOR, 600f, 600f, lengthCm = 20f, rotation = 0.3f, armRad = 0.9f))
        instrumentHandles(s.instruments[0], s.camera)!!.extras.forEach { (mode, at) ->
            assertEquals("$mode", mode, dragModeFor(s, s.instruments[0], at))
        }
    }

    @Test
    fun `the protractor arm reads whole degrees above its base line`() {
        val deg = (Math.PI / 180).toFloat()
        // 50 degrees up and to the right (up is -y).
        val a = 50.3 * Math.PI / 180
        assertEquals(50 * deg, InstrumentGeometry.armAngle(kotlin.math.cos(a).toFloat(), -kotlin.math.sin(a).toFloat()), 1e-5f)
        // Below the base line it holds at the nearer end.
        assertEquals(0f, InstrumentGeometry.armAngle(5f, 1f), 0f)
        assertEquals(180 * deg, InstrumentGeometry.armAngle(-5f, 1f), 1e-5f)
    }

    @Test
    fun `the drawn angle runs from the arm's tip through the centre to the rim`() {
        val p = Instrument(InstrumentKind.PROTRACTOR, 100f, 100f, lengthCm = 10f, armRad = (Math.PI / 2).toFloat())
        val r = p.protractorRadiusPx
        val xy = InstrumentGeometry.protractorInk(p, 1f, InstrumentGeometry.ProtractorInk.ANGLE)
        // Starts at the arm's rim point (straight up), ends on the base line's rim point.
        assertEquals(100f, xy[0], 0.01f)
        assertEquals(100f - r, xy[1], 0.01f)
        assertEquals(100f + r, xy[xy.size - 2], 0.01f)
        assertEquals(100f, xy[xy.size - 1], 0.01f)
    }
}
