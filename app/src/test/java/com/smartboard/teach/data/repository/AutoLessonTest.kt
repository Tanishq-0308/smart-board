package com.smartboard.teach.data.repository

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Work is always findable: any board with content is a lesson, saved or not. */
class AutoLessonTest {

    private val stroke = Stroke(
        "s1", DrawTool.PEN, StrokeStyle(0xFF000000.toInt(), 4f),
        floatArrayOf(0f, 0f, 1f, 10f, 10f, 1f),
    )

    @Test
    fun firstSaveWithContentCreatesAnUntitledLesson() = runTest {
        val repo = BoardRepositoryImpl(FakeBoardDao(), Dispatchers.Unconfined)
        val page = repo.createPage("s", 0, 100, 100)

        repo.savePage(page, emptyList(), emptyList())
        assertTrue("an empty board is not a lesson yet", repo.getLessons().isEmpty())

        repo.savePage(page, listOf(stroke), emptyList())
        val lesson = repo.getLessons().single()
        assertEquals("s", lesson.sessionId)
        assertTrue(lesson.autoNamed)
    }

    @Test
    fun namingItClearsAutoNamedAndLaterSavesMoveItsTime() = runTest {
        val dao = FakeBoardDao()
        val repo = BoardRepositoryImpl(dao, Dispatchers.Unconfined)
        val page = repo.createPage("s", 0, 100, 100)
        repo.savePage(page, listOf(stroke), emptyList())

        repo.saveLesson("s", "Fractions")
        val named = repo.getLesson("s")!!
        assertEquals("Fractions", named.name)
        assertFalse(named.autoNamed)

        dao.lessons["s"] = dao.lessons.getValue("s").copy(updatedAt = 0)
        repo.savePage(page, listOf(stroke), emptyList())
        assertTrue(repo.getLesson("s")!!.updatedAt > 0)
        assertEquals("Fractions", repo.getLesson("s")!!.name)
    }
}
