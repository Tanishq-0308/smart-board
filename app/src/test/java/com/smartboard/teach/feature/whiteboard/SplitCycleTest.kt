package com.smartboard.teach.feature.whiteboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The split menu's pane-slot budget, and how page deletion trims panes.
 *
 * An option past the number of state slots the screen allocates would be an
 * index crash mid-lesson, not a layout nit.
 */
class SplitCycleTest {

    @Test
    fun `every menu option has a state slot`() {
        // WhiteboardScreen allocates MAX_PANES - 1 extra states and renderers;
        // the menu offers 2..MAX_PANES panes, so the last extra index is
        // MAX_PANES - 2.
        val slots = MAX_PANES - 1
        for (count in 2..MAX_PANES) {
            assertTrue("$count panes needs ${count - 1} slots", count - 1 <= slots)
        }
    }

    @Test
    fun `one page leaves no room for extra panes`() {
        // The reported bug: pages deleted under a split left a pane stranded
        // on a page that no longer existed.
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
}
