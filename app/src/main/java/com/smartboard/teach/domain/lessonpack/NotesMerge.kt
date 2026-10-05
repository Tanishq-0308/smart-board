package com.smartboard.teach.domain.lessonpack

import com.smartboard.teach.domain.model.LessonNotes
import com.smartboard.teach.domain.model.LessonPart

/**
 * One set of notes from the notes of each taught part (the school's notes
 * route reads one image at a time). The title is the first part's; each
 * part's summary keeps its source label so a reader can tell the board from
 * the textbook page; lists are joined without repeats.
 */
object NotesMerge {

    fun merge(parts: List<LessonPart>): LessonNotes {
        val notes = parts.mapNotNull { p -> p.notes?.let { p.label to it } }
        if (notes.size == 1) return notes.first().second
        fun <T> joined(pick: (LessonNotes) -> List<T>, key: (T) -> String): List<T> =
            notes.flatMap { pick(it.second) }.distinctBy { key(it).trim().lowercase() }
        return LessonNotes(
            title = notes.firstOrNull { it.second.title.isNotBlank() }?.second?.title.orEmpty(),
            summary = notes.filter { it.second.summary.isNotBlank() }
                .joinToString("\n\n") { (label, n) -> "$label: ${n.summary.trim()}" },
            topics = joined({ it.topics }) { it },
            keyPoints = joined({ it.keyPoints }) { it },
            definitions = joined({ it.definitions }) { it.term },
            formulas = joined({ it.formulas }) { it.replace(" ", "") },
            followUpQuestions = joined({ it.followUpQuestions }) { it },
        )
    }
}
