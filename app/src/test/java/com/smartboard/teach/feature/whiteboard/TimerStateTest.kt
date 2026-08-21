package com.smartboard.teach.feature.whiteboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerStateTest {

    // --- countdown ---

    @Test
    fun `a countdown shows time remaining, not time elapsed`() {
        val state = TimerState(presetMs = 60_000L, elapsedMs = 20_000L)
        assertEquals(40_000L, state.displayMs)
    }

    @Test
    fun `a countdown stops at zero rather than going negative`() {
        // "-00:00:12" tells a class nothing, and a timer running past zero
        // would keep re-arming the alarm.
        val state = TimerState(presetMs = 5_000L, isRunning = true).tick(9_000L)
        assertEquals(0L, state.displayMs)
        assertFalse(state.isRunning)
    }

    @Test
    fun `reaching zero raises the finished flag exactly once`() {
        // Held as state rather than derived: a derived `remaining == 0` is
        // true on every later tick, so the alarm would ring forever.
        val finished = TimerState(presetMs = 1_000L, isRunning = true).tick(1_000L)
        assertTrue(finished.hasFinished)

        val acknowledged = finished.alarmAcknowledged()
        assertFalse(acknowledged.hasFinished)
        assertFalse("a stopped timer must not re-finish", acknowledged.tick(1_000L).hasFinished)
    }

    @Test
    fun `a finished countdown cannot be started again without a reset`() {
        val finished = TimerState(presetMs = 1_000L, isRunning = true).tick(1_000L)
        assertFalse(finished.start().isRunning)
        assertTrue(finished.reset().start().isRunning)
    }

    // --- stopwatch ---

    @Test
    fun `a stopwatch counts up from zero`() {
        val state = TimerState(mode = TimerMode.STOPWATCH, isRunning = true).tick(3_000L)
        assertEquals(3_000L, state.displayMs)
    }

    @Test
    fun `a stopwatch never finishes on its own`() {
        val state = TimerState(mode = TimerMode.STOPWATCH, presetMs = 1_000L, isRunning = true)
            .tick(90_000L)
        assertFalse(state.hasFinished)
        assertTrue(state.isRunning)
    }

    @Test
    fun `a stopwatch can always start, an empty countdown cannot`() {
        assertTrue(TimerState(mode = TimerMode.STOPWATCH).canStart)
        assertFalse(TimerState(presetMs = 0L).canStart)
    }

    // --- transitions ---

    @Test
    fun `a paused timer does not advance`() {
        val paused = TimerState(presetMs = 60_000L, elapsedMs = 10_000L, isRunning = false)
        assertEquals(paused, paused.tick(5_000L))
    }

    @Test
    fun `reset returns to the start but keeps the preset`() {
        val state = TimerState(presetMs = 60_000L, elapsedMs = 25_000L, isRunning = true).reset()
        assertEquals(60_000L, state.displayMs)
        assertEquals(60_000L, state.presetMs)
        assertFalse(state.isRunning)
    }

    @Test
    fun `changing the preset clears elapsed time`() {
        // Otherwise a freshly set timer starts part-way through.
        val state = TimerState(presetMs = 60_000L, elapsedMs = 30_000L).withPreset(120_000L)
        assertEquals(120_000L, state.displayMs)
    }

    @Test
    fun `switching mode starts clean`() {
        val running = TimerState(presetMs = 60_000L, elapsedMs = 30_000L, isRunning = true)
        val stopwatch = running.withMode(TimerMode.STOPWATCH)
        assertEquals(0L, stopwatch.displayMs)
        assertFalse(stopwatch.isRunning)
    }

    @Test
    fun `switching to the mode already selected changes nothing`() {
        val running = TimerState(elapsedMs = 30_000L, isRunning = true)
        assertEquals(running, running.withMode(TimerMode.COUNTDOWN))
    }

    @Test
    fun `a preset beyond the display range is clamped`() {
        val state = TimerState().withPreset(TimerState.MAX_PRESET_MS + 60_000L)
        assertEquals("99:59:59", formatTimer(state.displayMs))
    }

    // --- display ---

    @Test
    fun `the readout is always three fields`() {
        // A readout that changes shape crossing an hour jumps around on the
        // board, and a class watching the clock notices.
        assertEquals("00:00:07", formatTimer(7_000L))
        assertEquals("00:05:00", formatTimer(300_000L))
        assertEquals("01:00:00", formatTimer(3_600_000L))
    }

    @Test
    fun `a negative time reads as zero rather than a negative clock`() {
        assertEquals("00:00:00", formatTimer(-5_000L))
    }

    // --- typed entry ---

    @Test
    fun `typed digits fill the clock from the right`() {
        assertEquals(5_000L, parseTimerDigits("5"))
        assertEquals(90_000L, parseTimerDigits("130"))
        assertEquals((1 * 3600 + 23 * 60 + 45) * 1000L, parseTimerDigits("12345"))
    }

    @Test
    fun `extra digits drop from the left rather than being rejected`() {
        // A teacher who mistypes keeps going instead of hunting for backspace.
        assertEquals(parseTimerDigits("123456"), parseTimerDigits("9123456"))
    }

    @Test
    fun `out-of-range minutes and seconds are clamped, not carried`() {
        // "0:99" is a typo; silently turning it into 1:39 hides the mistake.
        assertEquals(59_000L, parseTimerDigits("99"))
        assertEquals((59 * 60 + 59) * 1000L, parseTimerDigits("9999"))
    }

    @Test
    fun `empty input is zero rather than a crash`() {
        assertEquals(0L, parseTimerDigits(""))
    }
}
