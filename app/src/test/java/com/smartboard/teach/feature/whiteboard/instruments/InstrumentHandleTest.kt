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
                assertEquals("$kind resize", InstrumentDrag.RESIZE, dragModeFor(s, s.instruments[0], resize))
            }
        }
    }

    @Test
    fun `dragging the resize handle puts the handle under the finger`() {
        kinds.forEach { kind ->
            val s = state(Instrument(kind, 300f, 400f, rotation = 0.7f))
            val original = s.instruments[0]
            val bigger = original.copy(lengthCm = 15f)
            val target = instrumentHandles(bigger, s.camera)!!.resize
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
}
