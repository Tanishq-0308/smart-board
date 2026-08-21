package com.smartboard.teach.feature.whiteboard

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDurationTest {

    @Test
    fun `seconds are always two digits`() {
        // "1:5" reads as one minute five... or one minute fifty.
        assertEquals("1:05", formatDuration(65_000))
    }

    @Test
    fun `under a minute still shows a zero minute`() {
        assertEquals("0:07", formatDuration(7_000))
    }

    @Test
    fun `an hour-long video gains an hours field`() {
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("2:05:09", formatDuration(7_509_000))
    }

    @Test
    fun `a short video does not show an empty hours field`() {
        assertEquals("9:59", formatDuration(599_000))
    }

    @Test
    fun `partial seconds round down, so the clock never reads past the end`() {
        assertEquals("0:01", formatDuration(1_999))
    }

    @Test
    fun `a negative position clamps rather than printing a negative clock`() {
        // MediaPlayer can report -1 before it is prepared.
        assertEquals("0:00", formatDuration(-1))
    }
}
