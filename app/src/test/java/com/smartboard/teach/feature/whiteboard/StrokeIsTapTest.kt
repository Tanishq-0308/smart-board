package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tap vs handwriting, inside a mindmap node.
 *
 * A press in a node has to do two incompatible things depending on what
 * follows it: focus the node so its +/x buttons appear, or write in it. This
 * is what decides, and getting it wrong either leaves a stray dot in every
 * node the teacher touches, or swallows the first mark they try to write.
 */
class StrokeIsTapTest {

    private fun stroke(vararg xy: Float): Stroke {
        val points = FloatArray(xy.size / 2 * Stroke.STRIDE)
        for (i in 0 until xy.size / 2) {
            points[i * Stroke.STRIDE] = xy[i * 2]
            points[i * Stroke.STRIDE + 1] = xy[i * 2 + 1]
            points[i * Stroke.STRIDE + 2] = 1f
        }
        return Stroke(
            id = "s",
            tool = DrawTool.PEN,
            style = StrokeStyle(colorArgb = 0xFF000000.toInt(), baseWidthPx = 4f),
            points = points,
        )
    }

    @Test
    fun `a single dot is a tap`() {
        assertTrue(strokeIsTap(stroke(100f, 100f)))
    }

    @Test
    fun `stylus jitter while resting on the glass is still a tap`() {
        // A stylus never reports a perfectly still press, so "did any move
        // event arrive" is not a usable test — it would make every tap write.
        assertTrue(strokeIsTap(stroke(100f, 100f, 101f, 100.5f, 100.5f, 101f)))
    }

    @Test
    fun `a written mark is not a tap`() {
        assertFalse(strokeIsTap(stroke(100f, 100f, 160f, 130f)))
    }

    @Test
    fun `a stroke that returns to its start is not a tap`() {
        // Measured from the FIRST point to each later one, not end-to-end: a
        // small circle starts and finishes in the same place, and end-to-end
        // distance would call it a tap and delete it.
        assertFalse(strokeIsTap(stroke(100f, 100f, 140f, 100f, 100f, 100f)))
    }

    @Test
    fun `travel just past the slop counts as writing`() {
        assertTrue(strokeIsTap(stroke(0f, 0f, TAP_SLOP_PX - 1f, 0f)))
        assertFalse(strokeIsTap(stroke(0f, 0f, TAP_SLOP_PX + 1f, 0f)))
    }

    @Test
    fun `an empty stroke is a tap rather than a crash`() {
        assertTrue(strokeIsTap(stroke()))
    }
}
