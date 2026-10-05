package com.smartboard.teach.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 * Status of a snapshot-to-notes job.
 *
 * The board snapshot is written to disk BEFORE the network call, so a failed
 * AI request still leaves a recoverable record rather than losing the lesson
 * content. [FAILED_PENDING_RETRY] rows show a Retry button in the notes list.
 */
enum class NoteStatus { COMPLETE, FAILED_PENDING_RETRY }

data class NoteDocument(
    val id: String,
    val title: String,
    val summary: String,
    val markdownPath: String?,
    val snapshotPath: String,
    val sourcePageId: String? = null,
    val model: String? = null,
    val createdAt: LocalDateTime,
    val status: NoteStatus,
    /** Why the AI call failed, retained so the retry UI can explain itself. */
    val failureMessage: String? = null,
    /** The lesson (board session) this note came from. */
    val lessonId: String? = null,
    /** Present for a lesson pack: notes per taught part, the assignment, sharing. */
    val pack: LessonPack? = null,
)

/** Structured lesson notes returned by the AI, before rendering to Markdown. */
@Serializable
data class LessonNotes(
    val title: String,
    val summary: String,
    val topics: List<String> = emptyList(),
    val keyPoints: List<String> = emptyList(),
    val definitions: List<Definition> = emptyList(),
    val formulas: List<String> = emptyList(),
    val followUpQuestions: List<String> = emptyList(),
) {
    @Serializable
    data class Definition(val term: String, val meaning: String)
}
