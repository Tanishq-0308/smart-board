package com.smartboard.teach.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.smartboard.teach.data.local.entity.BoardBackgroundEntity
import com.smartboard.teach.data.local.entity.BoardPageEntity
import com.smartboard.teach.data.local.entity.LessonEntity
import com.smartboard.teach.data.local.entity.ContainerCellEntity
import com.smartboard.teach.data.local.entity.ContainerEntity
import com.smartboard.teach.data.local.entity.StrokeEntity
import com.smartboard.teach.data.local.entity.TextBoxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {

    // --- Pages ---

    @Query("SELECT * FROM board_pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    fun observePages(sessionId: String): Flow<List<BoardPageEntity>>

    @Query("SELECT * FROM board_pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    suspend fun getPages(sessionId: String): List<BoardPageEntity>

    @Query("SELECT * FROM board_pages WHERE id = :pageId")
    suspend fun getPage(pageId: String): BoardPageEntity?

    @Upsert
    suspend fun upsertPage(page: BoardPageEntity)

    @Query("DELETE FROM board_pages WHERE id = :pageId")
    suspend fun deletePage(pageId: String)

    @Query("SELECT COUNT(*) FROM board_pages WHERE sessionId = :sessionId")
    suspend fun pageCount(sessionId: String): Int

    /** Most recent lesson session, so the board resumes where it left off. */
    @Query("SELECT sessionId FROM board_pages ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestSessionId(): String?

    // --- Lessons (named sessions) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLesson(lesson: LessonEntity)

    @Query("SELECT * FROM lessons ORDER BY updatedAt DESC")
    suspend fun getLessons(): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE sessionId = :sessionId")
    suspend fun getLesson(sessionId: String): LessonEntity?

    @Query("DELETE FROM lessons WHERE sessionId = :sessionId")
    suspend fun deleteLesson(sessionId: String)

    /** Pages cascade from nothing, so a lesson delete must remove them too. */
    @Query("DELETE FROM board_pages WHERE sessionId = :sessionId")
    suspend fun deletePagesForSession(sessionId: String)

    // --- Strokes ---

    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY orderIndex ASC")
    suspend fun getStrokes(pageId: String): List<StrokeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrokes(strokes: List<StrokeEntity>)

    @Query("DELETE FROM strokes WHERE id IN (:strokeIds)")
    suspend fun deleteStrokes(strokeIds: List<String>)

    @Query("DELETE FROM strokes WHERE pageId = :pageId")
    suspend fun clearStrokes(pageId: String)

    @Query("SELECT COUNT(*) FROM strokes WHERE pageId = :pageId")
    suspend fun strokeCount(pageId: String): Int

    // --- Text boxes ---

    @Query("SELECT * FROM text_boxes WHERE pageId = :pageId")
    suspend fun getTextBoxes(pageId: String): List<TextBoxEntity>

    @Upsert
    suspend fun upsertTextBoxes(boxes: List<TextBoxEntity>)

    @Query("DELETE FROM text_boxes WHERE id IN (:ids)")
    suspend fun deleteTextBoxes(ids: List<String>)

    @Query("DELETE FROM text_boxes WHERE pageId = :pageId")
    suspend fun clearTextBoxes(pageId: String)

    // --- Backgrounds ---

    @Query("SELECT * FROM board_backgrounds WHERE id = :id")
    suspend fun getBackground(id: String): BoardBackgroundEntity?

    @Upsert
    suspend fun upsertBackground(background: BoardBackgroundEntity)

    @Query("DELETE FROM board_backgrounds WHERE id = :id")
    suspend fun deleteBackground(id: String)

    @Query("SELECT * FROM board_backgrounds")
    suspend fun allBackgrounds(): List<BoardBackgroundEntity>

    // --- Containers (tables, mindmaps) ---

    @Query("SELECT * FROM containers WHERE pageId = :pageId ORDER BY orderIndex ASC")
    suspend fun getContainers(pageId: String): List<ContainerEntity>

    @Query(
        "SELECT * FROM container_cells WHERE containerId IN (:containerIds) " +
            "ORDER BY containerId ASC, cellIndex ASC",
    )
    suspend fun getContainerCells(containerIds: List<String>): List<ContainerCellEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContainers(containers: List<ContainerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContainerCells(cells: List<ContainerCellEntity>)

    /** Cells cascade; strokes do not (they are rewritten in the same transaction). */
    @Query("DELETE FROM containers WHERE pageId = :pageId")
    suspend fun clearContainers(pageId: String)

    /**
     * Persists one page's content atomically. Called from a debounced writer,
     * never on pointer-move.
     */
    @Transaction
    suspend fun savePageContent(
        page: BoardPageEntity,
        strokes: List<StrokeEntity>,
        textBoxes: List<TextBoxEntity>,
        containers: List<ContainerEntity> = emptyList(),
        cells: List<ContainerCellEntity> = emptyList(),
    ) {
        upsertPage(page)
        clearStrokes(page.id)
        if (strokes.isNotEmpty()) insertStrokes(strokes)
        clearTextBoxes(page.id)
        if (textBoxes.isNotEmpty()) upsertTextBoxes(textBoxes)
        // Containers are cleared and rewritten wholesale like strokes. Cells
        // cascade from the delete, so they must be re-inserted after their
        // parent rows exist.
        clearContainers(page.id)
        if (containers.isNotEmpty()) {
            insertContainers(containers)
            if (cells.isNotEmpty()) insertContainerCells(cells)
        }
    }
}
