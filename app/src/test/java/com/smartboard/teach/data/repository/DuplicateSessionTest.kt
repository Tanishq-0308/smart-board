package com.smartboard.teach.data.repository

import com.smartboard.teach.data.local.dao.BoardDao
import com.smartboard.teach.data.local.entity.BoardBackgroundEntity
import com.smartboard.teach.data.local.entity.BoardPageEntity
import com.smartboard.teach.data.local.entity.ContainerCellEntity
import com.smartboard.teach.data.local.entity.ContainerEntity
import com.smartboard.teach.data.local.entity.LessonEntity
import com.smartboard.teach.data.local.entity.StrokeEntity
import com.smartboard.teach.data.local.entity.TextBoxEntity
import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import com.smartboard.teach.domain.model.TextBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Save as" must leave the original lesson intact.
 *
 * It used to copy strokes and text boxes with their ORIGINAL ids. Room inserts
 * those with REPLACE / upsert semantics keyed on id, so each "copy" moved the
 * row to the new page and the original lesson came back with its tables and
 * images but no ink — the "page came back almost empty" report.
 */
class DuplicateSessionTest {

    @Test
    fun saveAsKeepsTheOriginalInk() = runTest {
        val dao = FakeBoardDao()
        val repo = BoardRepositoryImpl(dao, Dispatchers.Unconfined)
        val page = repo.createPage("original", 0, 1920, 1080)
        val stroke = Stroke(
            "s1", DrawTool.PEN, StrokeStyle(0xFF000000.toInt(), 4f),
            floatArrayOf(0f, 0f, 1f, 10f, 10f, 1f),
        )
        val box = TextBox("t1", 0f, 0f, 100f, "hello", 0xFF000000.toInt(), 20f)
        repo.savePage(page, listOf(stroke), listOf(box))

        val copy = repo.duplicateSession("original", "Copy")

        val original = repo.loadPage(page.id)!!
        assertEquals(1, original.strokes.size)
        assertEquals(1, original.textBoxes.size)
        val copied = repo.loadPage(repo.getPages(copy).single().id)!!
        assertEquals(1, copied.strokes.size)
        assertEquals(1, copied.textBoxes.size)
    }
}

/** In-memory BoardDao with Room's id-keyed REPLACE / upsert semantics. */
private class FakeBoardDao : BoardDao {
    val pages = linkedMapOf<String, BoardPageEntity>()
    val lessons = linkedMapOf<String, LessonEntity>()
    val strokes = linkedMapOf<String, StrokeEntity>()
    val boxes = linkedMapOf<String, TextBoxEntity>()
    val backgrounds = linkedMapOf<String, BoardBackgroundEntity>()
    val containers = linkedMapOf<String, ContainerEntity>()
    val cells = linkedMapOf<Pair<String, Int>, ContainerCellEntity>()

    override fun observePages(sessionId: String): Flow<List<BoardPageEntity>> =
        flowOf(pages.values.filter { it.sessionId == sessionId })
    override suspend fun getPages(sessionId: String) =
        pages.values.filter { it.sessionId == sessionId }.sortedBy { it.pageIndex }
    override suspend fun getPage(pageId: String) = pages[pageId]
    override suspend fun upsertPage(page: BoardPageEntity) { pages[page.id] = page }
    override suspend fun deletePage(pageId: String) { pages.remove(pageId) }
    override suspend fun pageCount(sessionId: String) = getPages(sessionId).size
    override suspend fun latestSessionId() = pages.values.maxByOrNull { it.updatedAt }?.sessionId
    override suspend fun upsertLesson(lesson: LessonEntity) { lessons[lesson.sessionId] = lesson }
    override suspend fun getLessons() = lessons.values.toList()
    override suspend fun getLesson(sessionId: String) = lessons[sessionId]
    override suspend fun deleteLesson(sessionId: String) { lessons.remove(sessionId) }
    override suspend fun deletePagesForSession(sessionId: String) {
        pages.values.removeAll { it.sessionId == sessionId }
    }
    override suspend fun getStrokes(pageId: String) =
        strokes.values.filter { it.pageId == pageId }.sortedBy { it.orderIndex }
    override suspend fun insertStrokes(strokes: List<StrokeEntity>) {
        strokes.forEach { this.strokes[it.id] = it }
    }
    override suspend fun deleteStrokes(strokeIds: List<String>) { strokeIds.forEach(strokes::remove) }
    override suspend fun clearStrokes(pageId: String) { strokes.values.removeAll { it.pageId == pageId } }
    override suspend fun strokeCount(pageId: String) = getStrokes(pageId).size
    override suspend fun getTextBoxes(pageId: String) = boxes.values.filter { it.pageId == pageId }
    override suspend fun upsertTextBoxes(boxes: List<TextBoxEntity>) {
        boxes.forEach { this.boxes[it.id] = it }
    }
    override suspend fun deleteTextBoxes(ids: List<String>) { ids.forEach(boxes::remove) }
    override suspend fun clearTextBoxes(pageId: String) { boxes.values.removeAll { it.pageId == pageId } }
    override suspend fun getBackground(id: String) = backgrounds[id]
    override suspend fun upsertBackground(background: BoardBackgroundEntity) {
        backgrounds[background.id] = background
    }
    override suspend fun deleteBackground(id: String) { backgrounds.remove(id) }
    override suspend fun allBackgrounds() = backgrounds.values.toList()
    override suspend fun getContainers(pageId: String) =
        containers.values.filter { it.pageId == pageId }.sortedBy { it.orderIndex }
    override suspend fun getContainerCells(containerIds: List<String>) =
        cells.values.filter { it.containerId in containerIds }
    override suspend fun insertContainers(containers: List<ContainerEntity>) {
        containers.forEach { this.containers[it.id] = it }
    }
    override suspend fun insertContainerCells(cells: List<ContainerCellEntity>) {
        cells.forEach { this.cells[it.containerId to it.cellIndex] = it }
    }
    override suspend fun clearContainers(pageId: String) {
        val gone = containers.values.filter { it.pageId == pageId }.map { it.id }.toSet()
        containers.keys.removeAll(gone)
        cells.keys.removeAll { it.first in gone }
    }
}
