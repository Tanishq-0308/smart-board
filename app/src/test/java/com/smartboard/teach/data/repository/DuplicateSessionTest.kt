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
