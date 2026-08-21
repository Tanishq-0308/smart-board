package com.smartboard.teach.data.repository

import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.core.util.parseIsoDate
import com.smartboard.teach.core.util.toIsoDate
import com.smartboard.teach.data.local.dao.AttendanceDao
import com.smartboard.teach.data.local.dao.SessionWithRecords
import com.smartboard.teach.data.local.entity.AttendanceRecordEntity
import com.smartboard.teach.data.local.entity.AttendanceSessionEntity
import com.smartboard.teach.di.IoDispatcher
import com.smartboard.teach.domain.model.AttendanceSession
import com.smartboard.teach.domain.model.AttendanceStatus
import com.smartboard.teach.domain.model.SyncState
import com.smartboard.teach.domain.repository.AttendanceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAttendanceRepository @Inject constructor(
    private val attendanceDao: AttendanceDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AttendanceRepository {

    override fun sessionFor(classId: String, date: LocalDate): Flow<AttendanceSession?> =
        attendanceDao.observeSession(classId, date.toIsoDate()).map { it?.toDomain() }

    override suspend fun save(
        classId: String,
        date: LocalDate,
        teacherId: String,
        marks: Map<String, AttendanceStatus>,
    ): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            val isoDate = date.toIsoDate()
            val now = System.currentTimeMillis()

            // Reuse the existing session id for this class+day so a correction
            // edits the same record. The unique (classId, date) index would
            // otherwise reject a second insert.
            val existing = attendanceDao.getSession(classId, isoDate)
            val sessionId = existing?.session?.id ?: UUID.randomUUID().toString()

            val session = AttendanceSessionEntity(
                id = sessionId,
                classId = classId,
                date = isoDate,
                takenByTeacherId = teacherId,
                createdAt = existing?.session?.createdAt ?: now,
                updatedAt = now,
                // Phase 2 flips this to PENDING_SYNC and pushes to the ERP.
                syncState = SyncState.LOCAL_ONLY.name,
            )

            attendanceDao.saveSession(
                session = session,
                records = marks.map { (studentId, status) ->
                    AttendanceRecordEntity(
                        sessionId = sessionId,
                        studentId = studentId,
                        status = status.name,
                    )
                },
            )
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage("Could not save attendance: ${t.message}"))
        }
    }
}

internal fun SessionWithRecords.toDomain() = AttendanceSession(
    id = session.id,
    classId = session.classId,
    date = parseIsoDate(session.date),
    takenByTeacherId = session.takenByTeacherId,
    marks = records.mapNotNull { record ->
        runCatching { AttendanceStatus.valueOf(record.status) }
            .getOrNull()
            ?.let { record.studentId to it }
    }.toMap(),
    syncState = runCatching { SyncState.valueOf(session.syncState) }
        .getOrDefault(SyncState.LOCAL_ONLY),
)
