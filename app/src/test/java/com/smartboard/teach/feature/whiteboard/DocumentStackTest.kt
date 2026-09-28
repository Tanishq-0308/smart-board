package com.smartboard.teach.feature.whiteboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentStackTest {

    private val pages = DocumentStack.layout(
        pageFiles = List(20) { "p$it.jpg" },
        pageSizes = List(20) { 1000 to 1414 }, // A4 portrait
        width = 1000f,
        left = 100f,
        top = 0f,
    )

    @Test
    fun pagesStackTopToBottomWithoutOverlap() {
        pages.zipWithNext().forEach { (a, b) ->
            assertEquals(a.bounds()[3] + DocumentStack.GAP, b.bounds()[1], 0.01f)
            assertEquals(a.x, b.x, 0f)
        }
        assertEquals(1414f, pages.first().bounds()[3], 0.01f)
    }

    @Test
    fun onlyPagesNearTheScreenStayDecoded() {
        // A viewport showing roughly the second page only.
        val visible = floatArrayOf(0f, 1500f, 1200f, 2600f)
        val wanted = MediaWindow.wanted(pages, visible)
        assertEquals(setOf(pages[1].id), wanted)
    }

    @Test
    fun aFewPicturesAreAlwaysAllKept() {
        val few = pages.take(MediaWindow.KEEP_ALL_UP_TO)
        assertEquals(few.map { it.id }.toSet(), MediaWindow.wanted(few, floatArrayOf(0f, 0f, 1f, 1f)))
    }
}
