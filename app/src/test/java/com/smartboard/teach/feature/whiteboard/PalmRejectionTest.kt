package com.smartboard.teach.feature.whiteboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PalmRejectionTest {

    private val PEN = true
    private val TOUCH = false

    @Test
    fun `a lone finger may draw`() {
        val pr = PalmRejection()
        assertTrue(pr.shouldAcceptDown(pointerId = 1, isStylus = TOUCH, nowMs = 1_000))
    }

    @Test
    fun `stylus always draws`() {
        val pr = PalmRejection()
        assertTrue(pr.shouldAcceptDown(1, PEN, 1_000))
    }

    @Test
    fun `touch is rejected while the stylus is in contact`() {
        val pr = PalmRejection()
        pr.shouldAcceptDown(1, PEN, 1_000)
        // The palm resting during writing.
        assertFalse(pr.shouldAcceptDown(2, TOUCH, 1_010))
    }

    @Test
    fun `touch stays rejected during the grace window after the pen lifts`() {
        val pr = PalmRejection()
        pr.shouldAcceptDown(1, PEN, 1_000)
        pr.onPointerUp(1, PEN, 1_100)
        // Pen skipped contact mid-word; the palm must not take over.
        assertFalse(pr.shouldAcceptDown(2, TOUCH, 1_200))
    }

    @Test
    fun `touch is accepted once the grace window has passed`() {
        val pr = PalmRejection()
        pr.shouldAcceptDown(1, PEN, 1_000)
        pr.onPointerUp(1, PEN, 1_100)
        assertTrue(pr.shouldAcceptDown(2, TOUCH, 1_100 + PalmRejection.STYLUS_GRACE_MS + 1))
    }

    @Test
    fun `the pen takes over a stroke a finger already started`() {
        val pr = PalmRejection()
        assertTrue(pr.shouldAcceptDown(1, TOUCH, 1_000))
        assertTrue(pr.shouldAcceptDown(2, PEN, 1_050))
        // The finger no longer owns the stroke.
        assertFalse(pr.shouldAcceptMove(1, TOUCH, 1_060))
        assertTrue(pr.shouldAcceptMove(2, PEN, 1_060))
    }

    @Test
    fun `only one pointer draws at a time`() {
        val pr = PalmRejection()
        assertTrue(pr.shouldAcceptDown(1, TOUCH, 1_000))
        // Well outside the palm window, so this is rejected by the
        // single-pointer rule rather than the proximity rule.
        assertFalse(pr.shouldAcceptDown(2, TOUCH, 5_000))
    }

    @Test
    fun `a second touch within the palm window is rejected`() {
        val pr = PalmRejection()
        assertTrue(pr.shouldAcceptDown(1, TOUCH, 1_000))
        pr.onPointerUp(1, TOUCH, 1_020)
        // Palm landed, then a finger arrives 50ms later.
        assertFalse(pr.shouldAcceptDown(2, TOUCH, 1_050))
    }

    @Test
    fun `a later touch after the palm window is accepted`() {
        val pr = PalmRejection()
        pr.shouldAcceptDown(1, TOUCH, 1_000)
        pr.onPointerUp(1, TOUCH, 1_020)
        assertTrue(pr.shouldAcceptDown(2, TOUCH, 1_000 + PalmRejection.PALM_WINDOW_MS + 1))
    }

    @Test
    fun `stylus only mode rejects every touch`() {
        val pr = PalmRejection(stylusOnlyMode = true)
        assertFalse(pr.shouldAcceptDown(1, TOUCH, 1_000))
        assertFalse(pr.shouldAcceptDown(2, TOUCH, 9_000))
        // ...but the pen still works.
        assertTrue(pr.shouldAcceptDown(3, PEN, 9_100))
    }

    @Test
    fun `stylus only mode can be toggled at runtime`() {
        val pr = PalmRejection(stylusOnlyMode = false)
        assertTrue(pr.shouldAcceptDown(1, TOUCH, 1_000))
        pr.onPointerUp(1, TOUCH, 1_100)

        pr.setStylusOnlyMode(true)
        assertFalse(pr.shouldAcceptDown(2, TOUCH, 5_000))
    }

    @Test
    fun `move events from an unaccepted pointer are ignored`() {
        val pr = PalmRejection()
        pr.shouldAcceptDown(1, TOUCH, 1_000)
        assertFalse(pr.shouldAcceptMove(99, TOUCH, 1_010))
    }

    @Test
    fun `pointer up releases the drawing slot`() {
        val pr = PalmRejection()
        assertTrue(pr.shouldAcceptDown(1, TOUCH, 1_000))
        assertTrue(pr.onPointerUp(1, TOUCH, 1_100))
        assertTrue(pr.shouldAcceptDown(2, TOUCH, 5_000))
    }

    @Test
    fun `pointer up from a rejected pointer does not end the active stroke`() {
        val pr = PalmRejection()
        pr.shouldAcceptDown(1, PEN, 1_000)
        assertFalse(pr.onPointerUp(2, TOUCH, 1_050))
        // The pen still owns the stroke.
        assertTrue(pr.shouldAcceptMove(1, PEN, 1_060))
    }

    @Test
    fun `reset clears all state`() {
        val pr = PalmRejection()
        pr.shouldAcceptDown(1, PEN, 1_000)
        pr.reset()
        assertFalse(pr.isStylusActive())
        assertTrue(pr.shouldAcceptDown(2, TOUCH, 5_000))
    }

    @Test
    fun `isStylusActive tracks pen contact`() {
        val pr = PalmRejection()
        assertFalse(pr.isStylusActive())
        pr.shouldAcceptDown(1, PEN, 1_000)
        assertTrue(pr.isStylusActive())
        pr.onPointerUp(1, PEN, 1_100)
        assertFalse(pr.isStylusActive())
    }

    @Test
    fun `a realistic write-with-palm-down sequence produces one stroke`() {
        val pr = PalmRejection()
        // Palm lands first.
        val palmDrew = pr.shouldAcceptDown(10, TOUCH, 1_000)
        // Pen lands 80ms later and must win.
        val penDrew = pr.shouldAcceptDown(11, PEN, 1_080)

        assertTrue(palmDrew)   // nothing else was down yet
        assertTrue(penDrew)    // pen overrides
        // From here only the pen extends the stroke.
        assertFalse(pr.shouldAcceptMove(10, TOUCH, 1_100))
        assertTrue(pr.shouldAcceptMove(11, PEN, 1_100))
    }
}
