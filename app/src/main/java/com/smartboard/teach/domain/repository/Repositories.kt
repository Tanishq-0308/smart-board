package com.smartboard.teach.domain.repository

import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.domain.model.AttendanceSession
import com.smartboard.teach.domain.model.AttendanceStatus
import com.smartboard.teach.domain.model.AuthState
import com.smartboard.teach.domain.model.SchoolClass
import com.smartboard.teach.domain.model.Student
import com.smartboard.teach.domain.model.StudyMaterial
import com.smartboard.teach.domain.model.Teacher
import com.smartboard.teach.domain.model.TimetableSlot
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.LocalDate

/**
 * Repository contracts — the Phase 1 to Phase 2 seam.
 *
 * Two rules make the swap a binding change rather than a rewrite:
 *
 *  1. These interfaces speak ONLY domain types. No Room entities, no DTOs, no
 *     HTTP response wrappers leak through.
 *
 *  2. Reads return `Flow`, not `suspend fun` returning a list. This is
 *     load-bearing, not stylistic: it lets the Phase 2 implementations serve
 *     cached Room data instantly while a network refresh lands underneath,
 *     with no change to any ViewModel or screen. Do not "simplify" it away.
 *
 * `refresh()` and `ensureLocalFile()` are real signatures that happen to be
 * cheap no-ops in Phase 1. The UI already calls them and already renders their
 * loading and error states, so the ERP integration lights up without touching
 * the presentation layer.
 */

interface AuthRepository {
    val authState: Flow<AuthState>
    /**
     * [username] is the teacher's email, or their employee ID when [schoolCode]
     * (the school's short code) is given.
     */
    suspend fun login(username: String, password: String, schoolCode: String? = null): AppResult<Teacher>
    suspend fun logout()
    suspend fun currentTeacher(): Teacher?
}

interface RosterRepository {
    fun classesForTeacher(teacherId: String): Flow<List<SchoolClass>>
    fun observeClass(classId: String): Flow<SchoolClass?>
    fun studentsInClass(classId: String): Flow<List<Student>>

    /** Pulls the teacher's classes, rosters and materials from the ERP. */
    suspend fun refresh(): AppResult<Unit>

    /** The teacher's periods as last pulled; empty before the first refresh. */
    suspend fun timetable(): List<TimetableSlot>
}

interface AttendanceRepository {
    fun sessionFor(classId: String, date: LocalDate): Flow<AttendanceSession?>

    /**
     * Upserts a day's attendance. The unique (classId, date) index means
     * saving twice edits the same session rather than duplicating it.
     */
    suspend fun save(
        classId: String,
        date: LocalDate,
        teacherId: String,
        marks: Map<String, AttendanceStatus>,
    ): AppResult<Unit>
}

interface MaterialRepository {
    fun materialsForTeacher(teacherId: String): Flow<List<StudyMaterial>>
    fun materialsForClass(classId: String): Flow<List<StudyMaterial>>

    /** Downloads the file on first use and keeps it on the board. */
    suspend fun ensureLocalFile(materialId: String): AppResult<File>
}
