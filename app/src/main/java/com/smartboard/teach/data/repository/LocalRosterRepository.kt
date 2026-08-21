package com.smartboard.teach.data.repository

import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.data.local.dao.ClassWithCount
import com.smartboard.teach.data.local.dao.RosterDao
import com.smartboard.teach.data.local.entity.StudentEntity
import com.smartboard.teach.domain.model.SchoolClass
import com.smartboard.teach.domain.model.Student
import com.smartboard.teach.domain.repository.RosterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalRosterRepository @Inject constructor(
    private val rosterDao: RosterDao,
) : RosterRepository {

    override fun classesForTeacher(teacherId: String): Flow<List<SchoolClass>> =
        rosterDao.observeClassesForTeacher(teacherId).map { rows -> rows.map { it.toDomain() } }

    override fun observeClass(classId: String): Flow<SchoolClass?> =
        rosterDao.observeClass(classId).map { it?.toDomain() }

    override fun studentsInClass(classId: String): Flow<List<Student>> =
        rosterDao.observeStudentsInClass(classId).map { rows -> rows.map { it.toDomain() } }

    /**
     * No-op in Phase 1 — the seeded roster is already the source of truth.
     *
     * It still returns Success so callers can wire their pull-to-refresh and
     * error handling now; Phase 2 fills in the ERP fetch behind this exact
     * signature.
     */
    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)
}

internal fun ClassWithCount.toDomain() = SchoolClass(
    id = id,
    name = name,
    section = section,
    subject = subject,
    teacherId = teacherId,
    studentCount = studentCount,
    remoteId = remoteId,
)

internal fun StudentEntity.toDomain() = Student(
    id = id,
    rollNumber = rollNumber,
    fullName = fullName,
    avatarPath = avatarPath,
    remoteId = remoteId,
)
