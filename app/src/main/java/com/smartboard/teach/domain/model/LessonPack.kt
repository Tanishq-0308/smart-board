package com.smartboard.teach.domain.model

import kotlinx.serialization.Serializable

/**
 * Everything a snapshot made of one lesson: the taught parts sent to the AI,
 * the notes for each, the NCERT-style assignment, and where it was shared.
 *
 * Saved on its note after every step, so a failure (no network, the school's
 * AI allowance used up, the app killed) resumes from the step that failed
 * rather than starting again.
 */
@Serializable
data class LessonPack(
    val classId: String? = null,
    val className: String = "",
    val subjectId: String? = null,
    val subjectName: String = "",
    val size: AssignmentSize = AssignmentSize.STANDARD,
    /** False when the teacher asked for notes only; an assignment can still be added later. */
    val withAssignment: Boolean = true,
    val parts: List<LessonPart> = emptyList(),
    /** Taught parts left out to stay under [com.smartboard.teach.domain.lessonpack.LessonPartPlanner.MAX_PARTS]. */
    val leftOut: Int = 0,
    /** The notes, saved to the class in the ERP (not yet visible to students). */
    val remoteNoteId: String? = null,
    val assignment: AssignmentDraft? = null,
    /** ISO date, chosen at review. */
    val dueDate: String? = null,
    /** The published homework, once the assignment is shared. */
    val homeworkId: String? = null,
    /** When the assignment was published. */
    val sharedAt: Long? = null,
    /** When the notes were made visible to the class. */
    val notesSharedAt: Long? = null,
) {
    /** The parts' notes merged into one document, once every part has some. */
    val notesReady: Boolean get() = parts.isNotEmpty() && parts.all { it.notes != null }
    val assignmentShared: Boolean get() = homeworkId != null
    /** Publishing the assignment shares the notes too (and did before notes could go alone). */
    val notesShared: Boolean get() = notesSharedAt != null || assignmentShared

    /** Once anything has gone to the class, its class can no longer change. */
    val classLocked: Boolean get() = assignmentShared || notesShared
}

@Serializable
data class LessonPart(
    /** Where it came from: "Board, page 1" or "Chapter 4, page 2". */
    val label: String,
    /** The JPEG sent to the AI, in the note's folder. */
    val path: String,
    val notes: LessonNotes? = null,
)

/** Assignment length: question counts per NCERT section (see NcertPattern). */
@Serializable
enum class AssignmentSize { SHORT, STANDARD, LONG }

@Serializable
data class AssignmentDraft(
    val title: String,
    /** Addressed to the student. */
    val instructions: String = "",
    /** Teacher only: the expected answers, read by the ERP's AI marker. */
    val markingGuide: String = "",
    val questions: List<AssignmentQuestion> = emptyList(),
) {
    val totalMarks: Int get() = questions.sumOf { it.marks }
}

@Serializable
data class AssignmentQuestion(
    val id: String,
    /** NCERT section letter, A to F. */
    val section: String,
    val text: String,
    /** "mcq", "short" or "long". */
    val type: String,
    val options: List<String> = emptyList(),
    val marks: Int,
    /** Teacher only: the expected answer. */
    val answer: String = "",
    /** Why the teacher should look at this one before publishing; empty when fine. */
    val problems: List<String> = emptyList(),
)
