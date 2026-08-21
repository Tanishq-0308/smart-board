package com.smartboard.teach.feature.whiteboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InkSmoothingTest {

    @Test
    fun `rejects points closer than the minimum distance`() {
        assertFalse(InkSmoothing.shouldAcceptPoint(100f, 100f, 100.5f, 100.5f))
    }

    @Test
    fun `accepts points beyond the minimum distance`() {
        assertTrue(InkSmoothing.shouldAcceptPoint(100f, 100f, 103f, 100f))
    }

    @Test
    fun `accepts movement on a single axis`() {
        assertTrue(InkSmoothing.shouldAcceptPoint(0f, 0f, 0f, 5f))
        assertTrue(InkSmoothing.shouldAcceptPoint(0f, 0f, 5f, 0f))
    }

    @Test
    fun `rejects an identical repeated point`() {
        assertFalse(InkSmoothing.shouldAcceptPoint(42f, 42f, 42f, 42f))
    }

    @Test
    fun `distance filtering is direction agnostic`() {
        assertTrue(InkSmoothing.shouldAcceptPoint(0f, 0f, -5f, 0f))
        assertTrue(InkSmoothing.shouldAcceptPoint(0f, 0f, 0f, -5f))
    }

    @Test
    fun `ema moves toward the target`() {
        val smoothed = InkSmoothing.smooth(previous = 0f, current = 10f, alpha = 0.5f)
        assertEquals(5f, smoothed, 0.001f)
    }

    @Test
    fun `ema with alpha one returns the current value`() {
        assertEquals(10f, InkSmoothing.smooth(0f, 10f, 1f), 0.001f)
    }

    @Test
    fun `ema with alpha zero holds the previous value`() {
        assertEquals(0f, InkSmoothing.smooth(0f, 10f, 0f), 0.001f)
    }

    @Test
    fun `ema converges over repeated samples`() {
        var value = 0f
        repeat(20) { value = InkSmoothing.smooth(value, 100f) }
        assertTrue("expected convergence toward 100, got $value", value > 99f)
    }

    @Test
    fun `pressure scales width between forty and one hundred percent`() {
        val base = 10f
        assertEquals(4f, InkSmoothing.widthForPressure(base, 0f, true), 0.001f)
        assertEquals(7f, InkSmoothing.widthForPressure(base, 0.5f, true), 0.001f)
        assertEquals(10f, InkSmoothing.widthForPressure(base, 1f, true), 0.001f)
    }

    @Test
    fun `zero pressure never produces invisible ink`() {
        // Boards that report 0 pressure must still draw something.
        assertTrue(InkSmoothing.widthForPressure(10f, 0f, true) > 0f)
    }

    @Test
    fun `pressure is ignored when sensitivity is disabled`() {
        // This is the escape hatch for boards that pin pressure to a constant.
        assertEquals(10f, InkSmoothing.widthForPressure(10f, 0f, false), 0.001f)
        assertEquals(10f, InkSmoothing.widthForPressure(10f, 1f, false), 0.001f)
    }

    @Test
    fun `out of range pressure is clamped`() {
        assertEquals(10f, InkSmoothing.widthForPressure(10f, 5f, true), 0.001f)
        assertEquals(4f, InkSmoothing.widthForPressure(10f, -3f, true), 0.001f)
    }

    @Test
    fun `catmull rom control points sit between the segment endpoints`() {
        val cp = InkSmoothing.catmullRomControlPoints(
            p0x = 0f, p0y = 0f,
            p1x = 10f, p1y = 0f,
            p2x = 20f, p2y = 0f,
            p3x = 30f, p3y = 0f,
        )
        assertEquals(4, cp.size)
        // On a straight horizontal run the controls stay on the line...
        assertEquals(0f, cp[1], 0.001f)
        assertEquals(0f, cp[3], 0.001f)
        // ...and between p1 and p2.
        assertTrue(cp[0] in 10f..20f)
        assertTrue(cp[2] in 10f..20f)
    }

    @Test
    fun `catmull rom is symmetric for a symmetric input`() {
        val cp = InkSmoothing.catmullRomControlPoints(
            0f, 0f, 10f, 10f, 20f, 10f, 30f, 0f,
        )
        // Mirrored input should give mirrored control offsets.
        assertEquals(cp[0] - 10f, 20f - cp[2], 0.001f)
    }
}
