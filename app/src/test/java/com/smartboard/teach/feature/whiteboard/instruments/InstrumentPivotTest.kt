package com.smartboard.teach.feature.whiteboard.instruments

import com.smartboard.teach.feature.whiteboard.BoardState
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the rotation grip.
 *
 * The pivot sits at the instrument's origin and is the only way to turn a
 * ruler or set square. Shrinking its claim, or letting the ruling edge take
 * precedence over it, silently makes the instrument unrotatable — it still
 * moves, so nothing looks broken.
 */
class InstrumentPivotTest {

    private fun state(instrument: Instrument) = BoardState().apply {
        instruments.add(instrument)
    }

    private fun ruler(rotation: Float = 0f) = Instrument(
        id = "r",
        kind = InstrumentKind.RULER,
        x = 100f,
        y = 200f,
        rotation = rotation,
    )

    @Test
    fun `a press on the pivot rotates`() {
        val s = state(ruler())
        // Camera is identity at construction, so world and screen coincide.
        assertEquals(
            InstrumentDrag.ROTATE,
            dragModeFor(s, s.instruments[0], Offset(100f, 200f)),
        )
    }

    @Test
    fun `the pivot claim beats the ruling edge`() {
        val s = state(ruler())
        // A point on the ruling edge but still inside the pivot. The edge
        // normally yields to ink; the pivot must win there or the corner of
        // every ruler becomes undraggable AND unrotatable.
        assertEquals(
            InstrumentDrag.ROTATE,
            dragModeFor(s, s.instruments[0], Offset(110f, 200f)),
        )
    }

    @Test
    fun `a press well away from the pivot does not rotate`() {
        val s = state(ruler())
        val mode = dragModeFor(s, s.instruments[0], Offset(600f, 260f))
        assert(mode != InstrumentDrag.ROTATE) { "expected no rotate, got $mode" }
    }

    @Test
    fun `the pivot still claims after the instrument is rotated`() {
        // The pivot is the centre of rotation, so it does not move when the
        // instrument turns — a teacher can keep adjusting the angle.
        val s = state(ruler(rotation = 1.2f))
        assertEquals(
            InstrumentDrag.ROTATE,
            dragModeFor(s, s.instruments[0], Offset(100f, 200f)),
        )
    }
}
