package com.smartboard.teach.domain.usecase

import android.graphics.Bitmap
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.data.file.NotesFileStore
import com.smartboard.teach.domain.model.NoteDocument
import com.smartboard.teach.domain.model.NoteStatus
import com.smartboard.teach.domain.repository.NotesAiService
import com.smartboard.teach.domain.repository.NotesRepository
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Board snapshot -> AI -> notes document on disk.
 *
 * The ordering here is the important part: **the snapshot image is written to
 * disk before the network call is made.** If the AI request then fails —
 * classroom Wi-Fi being what it is — the board content is already safe and the
 * note is recorded as [NoteStatus.FAILED_PENDING_RETRY] with a valid snapshot
 * path, so the notes list can offer a Retry that actually works.
 *
 * A teacher who snapshots a full board and gets "network error, nothing saved"
 * has lost a lesson. That must not be possible.
 */
@Singleton
class GenerateNotesFromSnapshotUseCase @Inject constructor(
    private val aiService: NotesAiService,
    private val notesRepository: NotesRepository,
    private val fileStore: NotesFileStore,
) {

    suspend operator fun invoke(
        snapshot: Bitmap,
        sourcePageId: String?,
    ): AppResult<NoteDocument> {
        val noteId = UUID.randomUUID().toString()

        // 1. Persist the image FIRST.
        val snapshotFile = try {
            fileStore.writeSnapshot(noteId, snapshot)
        } catch (t: Throwable) {
            return AppResult.Failure(
                AppError.Storage("Could not save the board snapshot: ${t.message}"),
            )
        }

        // 2. Ask the AI.
        return when (val aiResult = aiService.summarizeBoard(snapshot)) {
            is AppResult.Success -> {
                val notes = aiResult.data
                val markdown = fileStore.writeMarkdown(noteId, notes)
                val note = NoteDocument(
                    id = noteId,
                    title = notes.title,
                    summary = notes.summary,
                    markdownPath = markdown.absolutePath,
                    snapshotPath = snapshotFile.absolutePath,
                    sourcePageId = sourcePageId,
                    model = aiService.modelName,
                    createdAt = LocalDateTime.now(),
                    status = NoteStatus.COMPLETE,
                )
                notesRepository.upsert(note)
                AppResult.Success(note)
            }

            is AppResult.Failure -> {
                // The snapshot survives; only the summary is missing.
                val pending = NoteDocument(
                    id = noteId,
                    title = "Board snapshot",
                    summary = "Summary pending — ${aiResult.error.message}",
                    markdownPath = null,
                    snapshotPath = snapshotFile.absolutePath,
                    sourcePageId = sourcePageId,
                    model = null,
                    createdAt = LocalDateTime.now(),
                    status = NoteStatus.FAILED_PENDING_RETRY,
                    failureMessage = aiResult.error.message,
                )
                notesRepository.upsert(pending)
                AppResult.Failure(aiResult.error)
            }
        }
    }

    /**
     * Re-runs the AI step for a note whose snapshot is already on disk.
     * Used by the Retry action in the notes list.
     */
    suspend fun retry(noteId: String, snapshot: Bitmap): AppResult<NoteDocument> {
        val existing = notesRepository.getNote(noteId)
            ?: return AppResult.Failure(AppError.NotFound("That note no longer exists."))

        return when (val aiResult = aiService.summarizeBoard(snapshot)) {
            is AppResult.Success -> {
                val notes = aiResult.data
                val markdown = fileStore.writeMarkdown(noteId, notes)
                val updated = existing.copy(
                    title = notes.title,
                    summary = notes.summary,
                    markdownPath = markdown.absolutePath,
                    model = aiService.modelName,
                    status = NoteStatus.COMPLETE,
                    failureMessage = null,
                )
                notesRepository.upsert(updated)
                AppResult.Success(updated)
            }

            is AppResult.Failure -> {
                notesRepository.upsert(
                    existing.copy(
                        summary = "Summary pending — ${aiResult.error.message}",
                        failureMessage = aiResult.error.message,
                        status = NoteStatus.FAILED_PENDING_RETRY,
                    ),
                )
                AppResult.Failure(aiResult.error)
            }
        }
    }
}
