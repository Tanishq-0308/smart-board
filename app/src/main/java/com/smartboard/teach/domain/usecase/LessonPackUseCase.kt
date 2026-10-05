package com.smartboard.teach.domain.usecase

import android.graphics.BitmapFactory
import com.smartboard.teach.R
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.core.util.AppText
import com.smartboard.teach.data.file.NotesFileStore
import com.smartboard.teach.domain.lessonpack.NcertPattern
import com.smartboard.teach.domain.lessonpack.NotesMerge
import com.smartboard.teach.domain.model.AssignmentDraft
import com.smartboard.teach.domain.model.AssignmentSize
import com.smartboard.teach.domain.model.LessonPack
import com.smartboard.teach.domain.model.LessonPart
import com.smartboard.teach.domain.model.NoteDocument
import com.smartboard.teach.domain.model.NoteStatus
import com.smartboard.teach.domain.repository.AssignmentRequest
import com.smartboard.teach.domain.repository.LessonShareService
import com.smartboard.teach.domain.repository.NotesAiService
import com.smartboard.teach.domain.repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Where a running lesson pack is, for the snapshot dialog. */
sealed interface PackProgress {
    data class Notes(val done: Int, val total: Int) : PackProgress
    data object SavingNotes : PackProgress
    data object WritingAssignment : PackProgress
}

/**
 * A lesson snapshot -> notes for each taught part -> one set of notes kept by
 * the class in the ERP -> an NCERT-style assignment grounded on those notes
 * only -> (after the teacher's review) shared with the class.
 *
 * Every step's result is saved on the note before the next starts, and the
 * part images are on disk before any network call. A failure (no network, the
 * school's AI allowance used up, the board switched off) leaves the note
 * FAILED_PENDING_RETRY, and [resume] carries on from the step that failed. A
 * guest's pack waits there until a teacher signs in.
 */
@Singleton
class LessonPackUseCase @Inject constructor(
    private val aiService: NotesAiService,
    private val share: LessonShareService,
    private val notesRepository: NotesRepository,
    private val fileStore: NotesFileStore,
) {

    fun newNoteId(): String = UUID.randomUUID().toString()

    /** Where part [index] of note [noteId] is written. */
    fun partFile(noteId: String, index: Int): File =
        File(File(fileStore.noteDir(noteId), "parts").apply { mkdirs() }, "part_$index.jpg")

    /** Records a pack whose part images are already written, then runs it. */
    suspend fun start(noteId: String, lessonId: String, pack: LessonPack, onProgress: (PackProgress) -> Unit): AppResult<NoteDocument> {
        val note = NoteDocument(
            id = noteId,
            title = AppText.get(R.string.status_title_board_snapshot),
            summary = AppText.get(R.string.pack_summary_waiting),
            markdownPath = null,
            snapshotPath = pack.parts.first().path,
            model = aiService.modelName,
            createdAt = LocalDateTime.now(),
            status = NoteStatus.FAILED_PENDING_RETRY,
            lessonId = lessonId,
            pack = pack,
        )
        notesRepository.upsert(note)
        return run(note, onProgress)
    }

    /** Carries on from the step that failed. */
    suspend fun resume(noteId: String, onProgress: (PackProgress) -> Unit = {}): AppResult<NoteDocument> {
        val note = notesRepository.getNote(noteId)
            ?: return AppResult.Failure(AppError.NotFound(AppText.get(R.string.error_note_missing)))
        return run(note, onProgress)
    }

    /** Sets (or changes) the class and size, e.g. for a pack made as a guest. Clears an unpublished assignment. */
    suspend fun chooseClass(
        noteId: String, classId: String, className: String, subjectId: String?, subjectName: String, size: AssignmentSize,
    ): AppResult<NoteDocument> = edit(noteId) { pack ->
        if (pack.isShared) return@edit pack
        val sameClass = pack.classId == classId && pack.subjectId == subjectId
        pack.copy(classId = classId, className = className, subjectId = subjectId, subjectName = subjectName, size = size,
            remoteNoteId = pack.remoteNoteId.takeIf { sameClass }, assignment = null)
    }

    /** Throws the assignment away and writes a new one. */
    suspend fun regenerate(noteId: String, onProgress: (PackProgress) -> Unit = {}): AppResult<NoteDocument> {
        val cleared = edit(noteId) { if (it.isShared) it else it.copy(assignment = null) }
        if (cleared is AppResult.Failure) return cleared
        return resume(noteId, onProgress)
    }

    /** The teacher's edits from review. */
    suspend fun saveReview(noteId: String, draft: AssignmentDraft, dueDate: String?): AppResult<NoteDocument> =
        edit(noteId) { pack ->
            if (pack.isShared) pack
            else pack.copy(assignment = draft.copy(questions = draft.questions.map(NcertPattern::recheck)), dueDate = dueDate)
        }

    /** Shares the notes with the class and publishes the assignment, which notifies them. */
    suspend fun publish(noteId: String): AppResult<NoteDocument> {
        val note = notesRepository.getNote(noteId)
            ?: return AppResult.Failure(AppError.NotFound(AppText.get(R.string.error_note_missing)))
        val pack = note.pack ?: return AppResult.Failure(AppError.NotFound())
        if (pack.isShared) return AppResult.Success(note)
        val classId = pack.classId ?: return AppResult.Failure(AppError.NotFound(AppText.get(R.string.pack_error_no_class)))
        val draft = pack.assignment ?: return AppResult.Failure(AppError.NotFound(AppText.get(R.string.pack_error_no_assignment)))
        val noteMaterial = pack.remoteNoteId ?: return AppResult.Failure(AppError.NotFound())

        (share.shareNotes(noteMaterial) as? AppResult.Failure)?.let { return it }
        val homeworkId = when (val r = share.publish(classId, pack.subjectId, draft, pack.dueDate, File(pack.parts.first().path))) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return r
        }
        val shared = note.copy(pack = pack.copy(homeworkId = homeworkId, sharedAt = System.currentTimeMillis()))
        notesRepository.upsert(shared)
        return AppResult.Success(shared)
    }

    private suspend fun run(start: NoteDocument, onProgress: (PackProgress) -> Unit): AppResult<NoteDocument> {
        var note = start
        var pack = note.pack ?: return AppResult.Failure(AppError.NotFound())

        suspend fun save(updated: LessonPack, status: NoteStatus = NoteStatus.FAILED_PENDING_RETRY, failure: String? = null) {
            pack = updated
            note = note.copy(pack = updated, status = status, failureMessage = failure)
            notesRepository.upsert(note)
        }
        suspend fun fail(error: AppError): AppResult<NoteDocument> {
            val summary = if (pack.notesReady) note.summary else AppText.get(R.string.status_summary_pending, error.message)
            note = note.copy(summary = summary)
            save(pack, NoteStatus.FAILED_PENDING_RETRY, error.message)
            return AppResult.Failure(error)
        }

        // 1. Notes for each taught part, two at a time (each is a metered call).
        if (!pack.notesReady) {
            val todo = pack.parts.withIndex().filter { it.value.notes == null }
            var done = pack.parts.size - todo.size
            onProgress(PackProgress.Notes(done, pack.parts.size))
            val gate = Semaphore(2)
            val results = coroutineScope {
                todo.map { (i, part) ->
                    async {
                        gate.withPermit {
                            val r = notesFor(part)
                            synchronized(this@LessonPackUseCase) { done++ }
                            onProgress(PackProgress.Notes(done, pack.parts.size))
                            i to r
                        }
                    }
                }.map { it.await() }
            }
            val parts = pack.parts.toMutableList()
            results.forEach { (i, r) -> (r as? AppResult.Success)?.let { parts[i] = parts[i].copy(notes = it.data) } }
            save(pack.copy(parts = parts))
            results.firstNotNullOfOrNull { (it.second as? AppResult.Failure)?.error }?.let { return fail(it) }
        }

        val merged = NotesMerge.merge(pack.parts)
        val markdown = fileStore.writeMarkdown(note.id, merged)
        note = note.copy(title = merged.title.ifBlank { note.title }, summary = merged.summary, markdownPath = markdown.absolutePath)

        // Without a class (a guest's snapshot) the notes are as far as it goes:
        // the review screen asks for the class.
        val classId = pack.classId ?: run {
            save(pack, NoteStatus.COMPLETE)
            return AppResult.Success(note)
        }

        // 2. The notes, kept by the class in the ERP: the assignment is grounded on them.
        if (pack.remoteNoteId == null) {
            onProgress(PackProgress.SavingNotes)
            when (val r = share.saveNotes(classId, pack.subjectId, merged)) {
                is AppResult.Success -> save(pack.copy(remoteNoteId = r.data))
                is AppResult.Failure -> return fail(r.error)
            }
        }

        // 3. The assignment.
        if (pack.assignment == null && !pack.isShared) {
            onProgress(PackProgress.WritingAssignment)
            // Grounding reads the note's index, which the ERP builds a moment
            // after the save. Generating before it would ground on nothing.
            if (!share.awaitIndexed(classId, pack.remoteNoteId!!)) {
                return fail(AppError.AiResponse(AppText.get(R.string.pack_error_not_indexed)))
            }
            val request = AssignmentRequest(
                classId = classId, className = pack.className, subjectId = pack.subjectId, subjectName = pack.subjectName,
                noteMaterialId = pack.remoteNoteId!!,
                topic = (listOf(merged.title) + merged.topics).filter { it.isNotBlank() }.joinToString(", "),
                size = pack.size,
            )
            when (val r = share.generateAssignment(request)) {
                is AppResult.Success -> save(pack.copy(assignment = r.data.copy(
                    title = AppText.get(R.string.pack_assignment_title, merged.title.ifBlank { r.data.title }),
                )))
                is AppResult.Failure -> return fail(r.error)
            }
        }

        save(pack, NoteStatus.COMPLETE)
        return AppResult.Success(note)
    }

    private suspend fun notesFor(part: LessonPart) = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeFile(part.path)
            ?: return@withContext AppResult.Failure(AppError.Storage(AppText.get(R.string.error_snapshot_open)))
        try {
            aiService.summarizeBoard(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun edit(noteId: String, change: (LessonPack) -> LessonPack): AppResult<NoteDocument> {
        val note = notesRepository.getNote(noteId)
            ?: return AppResult.Failure(AppError.NotFound(AppText.get(R.string.error_note_missing)))
        val pack = note.pack ?: return AppResult.Failure(AppError.NotFound())
        val updated = note.copy(pack = change(pack))
        notesRepository.upsert(updated)
        return AppResult.Success(updated)
    }
}
