package com.smartboard.teach.data.repository

import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.core.util.epochMillisToDateTime
import com.smartboard.teach.data.file.NotesFileStore
import com.smartboard.teach.data.local.dao.NotesDao
import com.smartboard.teach.data.local.entity.NoteDocumentEntity
import com.smartboard.teach.di.IoDispatcher
import com.smartboard.teach.domain.model.NoteDocument
import com.smartboard.teach.domain.model.NoteStatus
import com.smartboard.teach.domain.repository.NotesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepositoryImpl @Inject constructor(
    private val notesDao: NotesDao,
    private val fileStore: NotesFileStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NotesRepository {

    override fun observeNotes(): Flow<List<NoteDocument>> =
        notesDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeNote(id: String): Flow<NoteDocument?> =
        notesDao.observeById(id).map { it?.toDomain() }

    override suspend fun getNote(id: String): NoteDocument? = withContext(ioDispatcher) {
        notesDao.getById(id)?.toDomain()
    }

    override suspend fun upsert(note: NoteDocument): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            notesDao.upsert(note.toEntity())
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage("Could not save the note: ${t.message}"))
        }
    }

    override suspend fun delete(id: String): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            notesDao.delete(id)
            fileStore.deleteNote(id)
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage("Could not delete the note: ${t.message}"))
        }
    }

    /**
     * The Room table is only an index; the filesystem holds the payload. If a
     * snapshot has been removed (storage cleaned, app data partially cleared)
     * the row would otherwise open onto nothing, so it is dropped at start-up.
     */
    override suspend fun reconcileWithDisk() = withContext(ioDispatcher) {
        val orphaned = notesDao.getAll()
            .filter { !fileStore.exists(it.snapshotPath) }
            .map { it.id }
        if (orphaned.isNotEmpty()) notesDao.deleteAll(orphaned)
        Unit
    }
}

private fun NoteDocumentEntity.toDomain() = NoteDocument(
    id = id,
    title = title,
    summary = summary,
    markdownPath = markdownPath,
    snapshotPath = snapshotPath,
    sourcePageId = sourcePageId,
    model = model,
    createdAt = epochMillisToDateTime(createdAt),
    status = runCatching { NoteStatus.valueOf(status) }.getOrDefault(NoteStatus.COMPLETE),
    failureMessage = failureMessage,
)

private fun NoteDocument.toEntity() = NoteDocumentEntity(
    id = id,
    title = title,
    summary = summary,
    markdownPath = markdownPath,
    snapshotPath = snapshotPath,
    sourcePageId = sourcePageId,
    model = model,
    createdAt = createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    status = status.name,
    failureMessage = failureMessage,
)
