package com.smartboard.teach.data.repository

import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.core.util.parseIsoDate
import com.smartboard.teach.core.util.toIsoDate
import com.smartboard.teach.data.local.dao.AttendanceDao
import com.smartboard.teach.data.local.dao.SessionWithRecords
import com.smartboard.teach.data.local.entity.AttendanceRecordEntity
import com.smartboard.teach.data.local.entity.AttendanceSessionEntity
import com.smartboard.teach.data.remote.erp.AttendanceBulkRequest
import com.smartboard.teach.data.remote.erp.AttendanceRecordDto
import com.smartboard.teach.data.remote.erp.ErpApi
import com.smartboard.teach.data.session.SessionManager
import com.smartboard.teach.domain.model.AttendanceSession
import com.smartboard.teach.domain.model.AttendanceStatus
import com.smartboard.teach.domain.model.SyncState
import com.smartboard.teach.domain.repository.AttendanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.JsonElement
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attendance kept on the board and sent to the ERP.
 *
 * A save lands in Room first (PENDING_SYNC) so a register taken with no network
 * is never lost, then goes up with `POST /api/erp/attendance/bulk`, an upsert per
 * student and day. Opening a day pulls what the ERP already holds for it, so a
 * register taken on the staff portal shows here too.
 */
@Singleton
class ErpAttendanceRepository @Inject constructor(
    private val api: ErpApi,
    private val attendanceDao: AttendanceDao,
    private val sessionManager: SessionManager,
) : AttendanceRepository {

    override fun sessionFor(classId: String, date: LocalDate): Flow<AttendanceSession?> =
        attendanceDao.observeSession(classId, date.toIsoDate())
            .onStart { pull(classId, date) }
            .map { it?.toDomain() }

    override suspend fun save(
        classId: String,
        date: LocalDate,
        teacherId: String,
        marks: Map<String, AttendanceStatus>,
    ): AppResult<Unit> {
        val sessionId = writeLocal(classId, date, teacherId, marks, SyncState.PENDING_SYNC)
        return push(sessionId, classId, date.toIsoDate(), marks)
    }

    /** Sends every register still waiting for the ERP. */
    suspend fun pushPending() {
        for (pending in attendanceDao.getPendingSessions()) {
            val domain = pending.toDomain()
            push(pending.session.id, domain.classId, pending.session.date, domain.marks)
        }
    }

    private suspend fun push(sessionId: String, classId: String, isoDate: String, marks: Map<String, AttendanceStatus>): AppResult<Unit> {
        if (marks.isEmpty()) {
            attendanceDao.setSyncState(sessionId, SyncState.SYNCED.name)
            return AppResult.Success(Unit)
        }
        val body = AttendanceBulkRequest(marks.map { (studentId, status) ->
            AttendanceRecordDto(personId = studentId, date = isoDate, status = status.name.lowercase(), sectionId = classId)
        })
        return when (val r = api.post("/api/erp/attendance/bulk", body, AttendanceBulkRequest.serializer(), JsonElement.serializer())) {
            is AppResult.Success -> {
                attendanceDao.setSyncState(sessionId, SyncState.SYNCED.name)
                AppResult.Success(Unit)
            }
            // Kept on the board as PENDING_SYNC; the next roster refresh retries.
            is AppResult.Failure -> r
        }
    }

    private suspend fun pull(classId: String, date: LocalDate) {
        if (sessionManager.erpSession() == null) return
        val local = attendanceDao.getSession(classId, date.toIsoDate())
        if (local?.session?.syncState == SyncState.PENDING_SYNC.name) return // ours is newer
        val path = "/api/erp/attendance?date=${date.toIsoDate()}&section_id=${enc(classId)}&person_type=student&limit=200"
        val remote = (api.getList(path, AttendanceRecordDto.serializer()) as? AppResult.Success)?.data ?: return
        if (remote.isEmpty()) return
        val marks = remote.mapNotNull { r ->
            runCatching { AttendanceStatus.valueOf(r.status.uppercase()) }.getOrNull()?.let { r.personId to it }
        }.toMap()
        val teacherId = local?.session?.takenByTeacherId ?: sessionManager.currentTeacherId().orEmpty()
        writeLocal(classId, date, teacherId, marks, SyncState.SYNCED)
    }

    private suspend fun writeLocal(
        classId: String, date: LocalDate, teacherId: String,
        marks: Map<String, AttendanceStatus>, state: SyncState,
    ): String {
        val isoDate = date.toIsoDate()
        val now = System.currentTimeMillis()
        val existing = attendanceDao.getSession(classId, isoDate)
        val sessionId = existing?.session?.id ?: UUID.randomUUID().toString()
        attendanceDao.saveSession(
            session = AttendanceSessionEntity(
                id = sessionId, classId = classId, date = isoDate, takenByTeacherId = teacherId,
                createdAt = existing?.session?.createdAt ?: now, updatedAt = now, syncState = state.name,
            ),
            records = marks.map { (studentId, status) -> AttendanceRecordEntity(sessionId, studentId, status.name) },
        )
        return sessionId
    }
}

internal fun SessionWithRecords.toDomain() = AttendanceSession(
    id = session.id,
    classId = session.classId,
    date = parseIsoDate(session.date),
    takenByTeacherId = session.takenByTeacherId,
    marks = records.mapNotNull { record ->
        runCatching { AttendanceStatus.valueOf(record.status) }.getOrNull()?.let { record.studentId to it }
    }.toMap(),
    syncState = runCatching { SyncState.valueOf(session.syncState) }.getOrDefault(SyncState.LOCAL_ONLY),
)
