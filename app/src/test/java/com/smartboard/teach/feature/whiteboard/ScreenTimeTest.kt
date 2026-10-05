package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.ContainerCell
import com.smartboard.teach.domain.model.ContainerKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ScreenTimeTest {

    private fun image(id: String, l: Float, t: Float, r: Float, b: Float, kind: ContainerKind = ContainerKind.IMAGE) =
        Container(id = id, kind = kind, x = l, y = t, cells = listOf(ContainerCell(l, t, r, b)), mediaPath = "x.jpg")

    private val screen = floatArrayOf(0f, 0f, 1000f, 600f)

    @Test fun countsPicturesMostlyOnScreen() {
        val pages = listOf(
            image("full", 100f, 100f, 400f, 400f),
            image("half", 800f, 100f, 1200f, 400f),     // exactly half shown
            image("sliver", 950f, 100f, 1350f, 400f),   // an eighth shown
            image("off", 2000f, 0f, 2400f, 400f),
        )
        assertEquals(listOf("full", "half"), ScreenTime.onScreen(pages, screen))
    }

    @Test fun aPageZoomedInOnCountsEvenThoughMostOfItIsOffScreen() {
        val tall = image("tall", -500f, -2000f, 1500f, 3000f) // fills the screen
        assertEquals(listOf("tall"), ScreenTime.onScreen(listOf(tall), screen))
    }

    @Test fun ignoresTablesAndOtherContainers() {
        val table = image("table", 0f, 0f, 500f, 500f, kind = ContainerKind.TABLE)
        assertEquals(emptyList<String>(), ScreenTime.onScreen(listOf(table), screen))
    }

    @Test fun documentTitleDropsTheMaterialIdPrefix() {
        assertEquals("Chapter 4 Linear Equations",
            documentTitle(File("/x/0b1c2d3e-0000-4000-8000-123456789abc_Chapter_4_Linear_Equations.pdf")))
        assertEquals("worksheet", documentTitle(File("/x/worksheet.pdf")))
        assertEquals("PDF", documentTitle(File("/x/fd6ae006-3144-41b6-b83f-dc67d5e09048.pdf")))
        assertEquals("Board 23 Sept 2026", documentTitle(File("/x/Board__23_Sept_2026.pdf")))
    }
}
