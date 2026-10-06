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

class ExportPiecesTest {
    private fun pic(id: String, y: Float, label: String? = "Book, page") = Container(
        id = id, kind = ContainerKind.IMAGE, x = 0f, y = y,
        cells = listOf(ContainerCell(0f, y, 100f, y + 140f)), mediaPath = "$id.jpg", label = label,
    )
    private fun ink(containerId: String?) = com.smartboard.teach.domain.model.Stroke(
        "s", com.smartboard.teach.domain.model.DrawTool.PEN,
        com.smartboard.teach.domain.model.StrokeStyle(0, 4f), floatArrayOf(0f, 0f, 1f), containerId,
    )
    private fun page(strokes: List<com.smartboard.teach.domain.model.Stroke>, containers: List<Container>) =
        com.smartboard.teach.domain.repository.PageContent(
            com.smartboard.teach.domain.model.BoardPage("p", "l", 0, 100, 100), strokes, emptyList(), null, containers)

    @Test fun aBookPageBecomesOnePdfPageEachWithItsInk() {
        val book = (1..281).map { pic("b$it", it * 200f) }
        val pieces = ExportPieces.split(page(listOf(ink("b2"), ink(null)), book))
        assertEquals(282, pieces.size) // 281 book pages + the free ink
        assertEquals(listOf("b2"), pieces[1].containers.map { it.id })
        assertEquals(1, pieces[1].strokes.size)
        assertEquals(emptyList<Container>(), pieces.last().containers)
        assertEquals(1, pieces.last().strokes.size)
    }

    @Test fun anOrdinaryBoardPageIsLeftWhole() {
        val one = page(listOf(ink(null)), listOf(pic("photo", 0f, label = null)))
        assertEquals(listOf(one), ExportPieces.split(one))
    }
}
