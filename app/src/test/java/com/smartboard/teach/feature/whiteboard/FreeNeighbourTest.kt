package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.BoardPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A split pane's pager must never land on a page open elsewhere. */
class FreeNeighbourTest {
    private val pages = (0..4).map { BoardPage(id = "p$it", sessionId = "s", pageIndex = it, widthPx = 1, heightPx = 1) }

    @Test fun skipsPagesOpenElsewhere() {
        assertEquals("p3", freeNeighbour(pages, 1, +1, setOf("p1", "p2"))?.id)
        assertEquals("p0", freeNeighbour(pages, 2, -1, setOf("p1", "p2"))?.id)
    }

    @Test fun nothingFreeMeansDisabled() {
        assertNull(freeNeighbour(pages, 3, +1, setOf("p4")))
        assertNull(freeNeighbour(pages, 0, -1, emptySet()))
        assertNull(freeNeighbour(pages, -1, +1, emptySet()))
    }
}
