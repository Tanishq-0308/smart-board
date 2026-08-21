package com.smartboard.teach.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.smartboard.teach.data.local.entity.NoteDocumentEntity
import com.smartboard.teach.data.local.entity.StudyMaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {

    @Query("SELECT * FROM study_materials WHERE teacherId = :teacherId ORDER BY title ASC")
    fun observeForTeacher(teacherId: String): Flow<List<StudyMaterialEntity>>

    @Query("SELECT * FROM study_materials WHERE classId = :classId ORDER BY title ASC")
    fun observeForClass(classId: String): Flow<List<StudyMaterialEntity>>

    @Query("SELECT * FROM study_materials WHERE id = :id")
    suspend fun getById(id: String): StudyMaterialEntity?

    @Upsert
    suspend fun upsertAll(materials: List<StudyMaterialEntity>)

    @Query("UPDATE study_materials SET localPath = :path, sizeBytes = :size WHERE id = :id")
    suspend fun setLocalPath(id: String, path: String, size: Long)

    @Query("SELECT COUNT(*) FROM study_materials")
    suspend fun count(): Int
}

@Dao
interface NotesDao {

    @Query("SELECT * FROM note_documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteDocumentEntity>>

    @Query("SELECT * FROM note_documents WHERE id = :id")
    fun observeById(id: String): Flow<NoteDocumentEntity?>

    @Query("SELECT * FROM note_documents WHERE id = :id")
    suspend fun getById(id: String): NoteDocumentEntity?

    @Query("SELECT * FROM note_documents")
    suspend fun getAll(): List<NoteDocumentEntity>

    @Upsert
    suspend fun upsert(note: NoteDocumentEntity)

    @Query("DELETE FROM note_documents WHERE id = :id")
    suspend fun delete(id: String)

    /** Used by start-up reconciliation to drop rows whose files vanished. */
    @Query("DELETE FROM note_documents WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<String>)
}
