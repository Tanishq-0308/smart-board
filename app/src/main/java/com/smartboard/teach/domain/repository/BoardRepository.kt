package com.smartboard.teach.domain.repository

import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.domain.model.BoardBackground
import com.smartboard.teach.domain.model.Lesson
import com.smartboard.teach.domain.model.BoardPage
import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.NoteDocument
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.TextBox
import kotlinx.coroutines.flow.Flow

/**
 * Board and notes storage.
 *
 * Unlike the ERP-backed repositories these are device-local by design and
 * never gain a remote variant, so they are bound permanently in
 * RepositoryModule rather than being part of the Phase 2 swap.
 */

data class PageContent(
    val page: BoardPage,
    val strokes: List<Stroke>,
    val textBoxes: List<TextBox>,
    val background: BoardBackground?,
    /** Tables and mindmaps. Their ink is in [strokes], tagged by container. */
    val containers: List<Container> = emptyList(),
)

interface BoardRepository {
    fun observePages(sessionId: String): Flow<List<BoardPage>>

    /** One-shot read, for restoring a session at start-up. */
    suspend fun getPages(sessionId: String): List<BoardPage>

    /** Most recent lesson, so the board resumes where it was left. */
    suspend fun latestSessionId(): String?

    // --- Named lessons ---

    /** Saved lessons, most recently updated first. */
    suspend fun getLessons(): List<Lesson>

    /** The lesson this session was saved as, or null if it is unsaved. */
    suspend fun getLesson(sessionId: String): Lesson?

    /** Names a session, or renames it. */
    suspend fun saveLesson(sessionId: String, name: String): Lesson

    /** Removes a lesson AND its pages. */
    suspend fun deleteLesson(sessionId: String)

    /**
     * Copies every page of [sessionId] into a new session, for "Save as".
     *
     * Returns the new session id. Duplicating rather than renaming keeps the
     * original lesson intact, which is the whole point of Save as.
     */
    suspend fun duplicateSession(sessionId: String, newName: String): String

    suspend fun loadPage(pageId: String): PageContent?

    suspend fun savePage(
        page: BoardPage,
        strokes: List<Stroke>,
        textBoxes: List<TextBox>,
        containers: List<Container> = emptyList(),
    ): AppResult<Unit>

    suspend fun createPage(sessionId: String, pageIndex: Int, widthPx: Int, heightPx: Int): BoardPage

    suspend fun deletePage(pageId: String): AppResult<Unit>

    suspend fun saveBackground(background: BoardBackground): AppResult<Unit>

    suspend fun getBackground(id: String): BoardBackground?

    suspend fun setPageThumbnail(pageId: String, path: String)
}

interface NotesRepository {
    fun observeNotes(): Flow<List<NoteDocument>>
    fun observeNote(id: String): Flow<NoteDocument?>
    suspend fun getNote(id: String): NoteDocument?
    suspend fun upsert(note: NoteDocument): AppResult<Unit>
    suspend fun delete(id: String): AppResult<Unit>

    /** Drops index rows whose backing files have disappeared. */
    suspend fun reconcileWithDisk()
}
