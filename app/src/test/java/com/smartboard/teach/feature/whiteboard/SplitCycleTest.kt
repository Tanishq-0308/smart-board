package com.smartboard.teach.feature.whiteboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The split button's wrap-around, and the pane-slot budget it depends on.
 *
 * These guard the two ways the cycle breaks: stranding the board below the
 * maximum, or adding a pane past the number of state slots the screen
 * allocates — which would be an index crash mid-lesson, not a layout nit.
 */
class SplitCycleTest {

    @Test
    fun `tapping adds a pane until the maximum`() {
        for (count in 1 until MAX_PANES) {
            assertTrue("$count panes should still add", splitTapAddsPane(count))
        }
    }

    @Test
    fun `tapping at the maximum closes the split`() {
        assertFalse(splitTapAddsPane(MAX_PANES))
    }

    @Test
    fun `a full cycle returns to one pane`() {
        // Walk the button the way a teacher would: tap until it wraps.
        var panes = 1
        var taps = 0
        do {
            panes = if (splitTapAddsPane(panes)) panes + 1 else 1
            taps++
        } while (panes != 1 && taps < 100)

        assertEquals("cycle should visit every pane count once", MAX_PANES, taps)
        assertEquals(1, panes)
    }

    @Test
    fun `one page leaves no room for extra panes`() {
        // The reported bug: pages deleted under a six-way split left a pane
        // stranded on a page that no longer existed.
        assertEquals(0, panesForPages(1))
    }

    @Test
    fun `panes never outnumber the pages that feed them`() {
        for (pages in 1..20) {
            val panes = panesForPages(pages)
            assertTrue("$pages pages gave $panes extra panes", panes < pages)
            assertTrue("$pages pages exceeded the cap", panes <= MAX_PANES - 1)
        }
    }

    @Test
    fun `enough pages fill every pane slot`() {
        assertEquals(MAX_PANES - 1, panesForPages(MAX_PANES))
        assertEquals(MAX_PANES - 1, panesForPages(50))
    }

    @Test
    fun `every addable pane has a state slot`() {
        // WhiteboardScreen allocates MAX_PANES - 1 extra states and renderers,
        // indexed by the number of panes already open.
        val slots = MAX_PANES - 1
        var panes = 1
        while (splitTapAddsPane(panes)) {
            val nextIndex = panes - 1
            assertTrue("pane index $nextIndex outside $slots slots", nextIndex < slots)
            panes++
        }
        assertEquals(MAX_PANES, panes)
    }
}
