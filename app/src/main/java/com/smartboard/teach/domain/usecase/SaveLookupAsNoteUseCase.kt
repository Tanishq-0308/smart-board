package com.smartboard.teach.domain.usecase

import android.content.Context
import com.smartboard.teach.R
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.data.file.NotesFileStore
import com.smartboard.teach.domain.model.LessonNotes
import com.smartboard.teach.domain.model.NoteDocument
import com.smartboard.teach.domain.model.NoteStatus
import com.smartboard.teach.domain.model.VisualLookup
import com.smartboard.teach.domain.repository.NotesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * Keeps a Look up answer as a note, beside the snapshot-to-notes ones.
 *
 * Rendered through the same Markdown path as AI notes, so the notes list,
 * detail view and Markdown export treat it like any other note.
 */
class SaveLookupAsNoteUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notesRepository: NotesRepository,
    private val fileStore: NotesFileStore,
) {
    suspend operator fun invoke(lookup: VisualLookup, crop: File): AppResult<NoteDocument> =
        try {
            val noteId = UUID.randomUUID().toString()
            val snapshot = fileStore.copySnapshot(noteId, crop)
            val notes = LessonNotes(
                title = lookup.title,
                summary = lookup.explanation,
                topics = lookup.relatedTerms,
                keyPoints = listOfNotNull(
                    lookup.transcription.takeIf { it.isNotBlank() }
                        ?.let { context.getString(R.string.lookup_read_from_board, it) },
                ),
            )
            val markdown = fileStore.writeMarkdown(noteId, notes)
            val note = NoteDocument(
                id = noteId,
                title = lookup.title,
                summary = lookup.explanation,
                markdownPath = markdown.absolutePath,
                snapshotPath = snapshot.absolutePath,
                createdAt = LocalDateTime.now(),
                status = NoteStatus.COMPLETE,
            )
            notesRepository.upsert(note)
            AppResult.Success(note)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage("Could not save the note: ${t.message}"))
        }
}
