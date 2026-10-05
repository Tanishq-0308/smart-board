package com.smartboard.teach.data.repository

import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.data.local.dao.ClassWithCount
import com.smartboard.teach.data.local.dao.MaterialDao
import com.smartboard.teach.data.local.dao.RosterDao
import com.smartboard.teach.data.local.entity.SchoolClassEntity
import com.smartboard.teach.data.local.entity.StudentEntity
import com.smartboard.teach.data.local.entity.StudyMaterialEntity
import com.smartboard.teach.data.local.entity.TimetableSlotEntity
import com.smartboard.teach.data.remote.erp.BoardClassDto
import com.smartboard.teach.data.remote.erp.BoardMaterialDto
import com.smartboard.teach.data.remote.erp.BoardStudentDto
import com.smartboard.teach.data.remote.erp.CourseMaterialDto
import com.smartboard.teach.data.remote.erp.ErpApi
import com.smartboard.teach.data.remote.erp.Page
import com.smartboard.teach.data.remote.erp.SubjectDto
import com.smartboard.teach.data.remote.erp.TimetableSlotDto
import com.smartboard.teach.data.session.SessionManager
import com.smartboard.teach.domain.model.SchoolClass
import com.smartboard.teach.domain.model.Student
import com.smartboard.teach.domain.model.TimetableSlot
import com.smartboard.teach.domain.repository.AttendanceRepository
import com.smartboard.teach.domain.repository.RosterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The teacher's classes, rosters and class materials, from the ERP.
 *
 * Screens read Room, so a board that lost its network keeps showing the last
 * roster; [refresh] replaces that cache with the ERP's lists (the teacher's
 * timetabled and class-teacher sections only, as the ERP scopes them).
 */
@Singleton
class ErpRosterRepository @Inject constructor(
    private val api: ErpApi,
    private val rosterDao: RosterDao,
    private val materialDao: MaterialDao,
    private val sessionManager: SessionManager,
    private val attendance: dagger.Lazy<AttendanceRepository>,
) : RosterRepository {

    private val refreshing = Mutex()

    override fun classesForTeacher(teacherId: String): Flow<List<SchoolClass>> =
        rosterDao.observeClassesForTeacher(teacherId).map { rows -> rows.map { it.toDomain() } }

    override fun observeClass(classId: String): Flow<SchoolClass?> =
        rosterDao.observeClass(classId).map { it?.toDomain() }

    override fun studentsInClass(classId: String): Flow<List<Student>> =
        rosterDao.observeStudentsInClass(classId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun timetable(): List<TimetableSlot> = rosterDao.getTimetable().map {
        TimetableSlot(it.classId, it.subjectId, it.subjectName, it.dayOfWeek,
            it.startTime?.let(::parseTime), it.endTime?.let(::parseTime))
    }

    override suspend fun refresh(): AppResult<Unit> = refreshing.withLock {
        val teacherId = sessionManager.currentTeacherId()
            ?: return AppResult.Failure(AppError.NotAuthenticated())

        val classes = when (val r = api.getList("/api/ai/board/classes", BoardClassDto.serializer())) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return r
        }
        rosterDao.upsertClasses(classes.map {
            SchoolClassEntity(id = it.id, name = it.name, section = it.section, subject = it.subject,
                teacherId = teacherId, remoteId = it.id)
        })
        rosterDao.deleteClassesExcept(teacherId, classes.map { it.id })

        for (c in classes) {
            val students = api.getList("/api/ai/board/classes/${c.id}/students", BoardStudentDto.serializer())
            if (students is AppResult.Success) {
                rosterDao.replaceRoster(c.id, students.data.map {
                    StudentEntity(id = it.id, rollNumber = it.rollNumber, fullName = it.fullName, remoteId = it.id)
                })
            }
            val materials = api.getList("/api/ai/board/materials?class_id=${enc(c.id)}", BoardMaterialDto.serializer())
            if (materials is AppResult.Success) {
                // Keep a material's downloaded copy across refreshes.
                val known = materialDao.getForClass(c.id).associateBy { it.id }
                materialDao.upsertAll(materials.data.map {
                    StudyMaterialEntity(id = it.id, teacherId = teacherId, classId = c.id, title = it.title,
                        kind = it.kind, localPath = known[it.id]?.localPath, remoteUrl = it.remoteUrl,
                        sizeBytes = it.sizeBytes, remoteId = it.id)
                })
                materialDao.deleteForClassExcept(c.id, materials.data.map { it.id })
            }
        }
        refreshPersonalMaterials(teacherId)
        refreshTimetable(teacherId)
        // Registers taken while offline go up now that the ERP answers.
        (attendance.get() as? ErpAttendanceRepository)?.pushPending()
        AppResult.Success(Unit)
    }
    /**
     * The teacher's own uploads that belong to no class ("for myself" on
     * Skolar). The board's materials route lists only class material, so these
     * come from the course-materials list, filtered to the teacher's uploads.
     */
    private suspend fun refreshPersonalMaterials(teacherId: String) {
        val rows = (api.getList("/api/erp/course-materials?mine_only=true", CourseMaterialDto.serializer())
            as? AppResult.Success)?.data ?: return
        val personal = rows.filter { it.sectionId == null }
        val known = materialDao.getPersonal(teacherId).associateBy { it.id }
        materialDao.upsertAll(personal.map {
            StudyMaterialEntity(id = it.id, teacherId = teacherId, classId = null, title = it.title,
                kind = materialKind(it.fileName, it.fileType), localPath = known[it.id]?.localPath,
                remoteUrl = "/api/erp/course-materials/${it.id}/file", sizeBytes = it.fileSize, remoteId = it.id)
        })
        materialDao.deletePersonalExcept(teacherId, personal.map { it.id })
    }

    /**
     * The teacher's periods, with subject names: where a class's subject ids
     * come from. ponytail: reads one 100-row page (the demo teacher has 66);
     * follow `meta.has_next` if a timetable ever outgrows it.
     */
    private suspend fun refreshTimetable(teacherId: String) {
        val slots = (api.get("/api/erp/timetable?teacher_id=${enc(teacherId)}&limit=100",
            Page.serializer(TimetableSlotDto.serializer())) as? AppResult.Success)?.data?.items ?: return
        val names = (api.get("/api/erp/subjects?limit=100", Page.serializer(SubjectDto.serializer()))
            as? AppResult.Success)?.data?.items.orEmpty().associate { it.id to it.name }
        rosterDao.replaceTimetable(slots.filter { !it.isBreak }.map {
            TimetableSlotEntity(classId = it.sectionId, subjectId = it.subjectId, subjectName = names[it.subjectId],
                dayOfWeek = it.dayOfWeek, startTime = it.startTime, endTime = it.endTime)
        })
    }
}

/** As the ERP's board route classifies files. */
private fun materialKind(name: String, mime: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext == "pdf" || "pdf" in mime -> "PDF"
        mime.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "webp") -> "IMAGE"
        else -> "BOOK"
    }
}

private fun parseTime(s: String): LocalTime? = runCatching { LocalTime.parse(s) }.getOrNull()

internal fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

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
