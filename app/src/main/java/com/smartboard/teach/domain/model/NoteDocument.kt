package com.smartboard.teach.domain.model

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
)

/** Structured lesson notes returned by the AI, before rendering to Markdown. */
data class LessonNotes(
    val title: String,
    val summary: String,
    val topics: List<String> = emptyList(),
    val keyPoints: List<String> = emptyList(),
    val definitions: List<Definition> = emptyList(),
    val formulas: List<String> = emptyList(),
    val followUpQuestions: List<String> = emptyList(),
) {
    data class Definition(val term: String, val meaning: String)
}
