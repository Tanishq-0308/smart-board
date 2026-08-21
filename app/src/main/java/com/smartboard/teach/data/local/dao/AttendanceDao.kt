package com.smartboard.teach.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.smartboard.teach.data.local.entity.AttendanceRecordEntity
import com.smartboard.teach.data.local.entity.AttendanceSessionEntity
import kotlinx.coroutines.flow.Flow

data class SessionWithRecords(
    @Embedded val session: AttendanceSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val records: List<AttendanceRecordEntity>,
)

@Dao
interface AttendanceDao {

    @Transaction
    @Query("SELECT * FROM attendance_sessions WHERE classId = :classId AND date = :date LIMIT 1")
    fun observeSession(classId: String, date: String): Flow<SessionWithRecords?>

    @Transaction
    @Query("SELECT * FROM attendance_sessions WHERE classId = :classId AND date = :date LIMIT 1")
    suspend fun getSession(classId: String, date: String): SessionWithRecords?

    @Transaction
    @Query("SELECT * FROM attendance_sessions WHERE classId = :classId ORDER BY date DESC LIMIT :limit")
    fun observeRecentSessions(classId: String, limit: Int = 30): Flow<List<SessionWithRecords>>

    @Upsert
    suspend fun upsertSession(session: AttendanceSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<AttendanceRecordEntity>)

    @Query("DELETE FROM attendance_records WHERE sessionId = :sessionId")
    suspend fun clearRecords(sessionId: String)

    /**
     * Upserts a whole day's attendance atomically.
     *
     * The unique (classId, date) index means saving the same day twice edits
     * the existing session rather than creating a duplicate — so a teacher can
     * correct a mistake without producing two conflicting records.
     */
    @Transaction
    suspend fun saveSession(
        session: AttendanceSessionEntity,
        records: List<AttendanceRecordEntity>,
    ) {
        upsertSession(session)
        clearRecords(session.id)
        if (records.isNotEmpty()) insertRecords(records)
    }
}
