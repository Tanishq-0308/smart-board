package com.smartboard.teach.domain.repository

import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.domain.lessonpack.NcertPattern
import com.smartboard.teach.domain.model.AssignmentDraft
import com.smartboard.teach.domain.model.AssignmentSize
import com.smartboard.teach.domain.model.LessonNotes
import java.io.File

/** The school-side half of a lesson pack: keep the notes, write the assignment, share both. */
interface LessonShareService {

    /** Saves the notes to the class, not yet visible to students. Returns the material id. */
    suspend fun saveNotes(classId: String, subjectId: String?, notes: LessonNotes): AppResult<String>

    /** Waits (bounded) until the school has indexed the note for its AI. False if it never did. */
    suspend fun awaitIndexed(classId: String, materialId: String): Boolean

    /** An NCERT-style assignment grounded on the saved note only. */
    suspend fun generateAssignment(request: AssignmentRequest): AppResult<AssignmentDraft>

    /** Makes the saved note visible to the class. */
    suspend fun shareNotes(materialId: String): AppResult<Unit>

    /** Publishes the assignment to the class, which notifies students and parents. Returns its id. */
    suspend fun publish(
        classId: String,
        subjectId: String?,
        draft: AssignmentDraft,
        dueDate: String?,
        boardImage: File?,
    ): AppResult<String>
}

data class AssignmentRequest(
    val classId: String,
    val className: String,
    val subjectId: String?,
    val subjectName: String,
    val noteMaterialId: String,
    val topic: String,
    val size: AssignmentSize,
) {
    val questionCount: Int get() = NcertPattern.questionCount(size)
    val totalMarks: Int get() = NcertPattern.totalMarks(size)
}
