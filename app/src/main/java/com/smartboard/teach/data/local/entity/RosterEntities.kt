package com.smartboard.teach.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ERP-owned entities.
 *
 * Every one carries a `remoteId` from schema v1 even though Phase 1 has no
 * API. It costs nothing now and avoids a migration the day the ERP
 * integration lands. Seed data populates them with fake ERP ids so Phase 2
 * code paths keyed on remoteId are exercised from day one.
 */

@Entity(tableName = "teachers", indices = [Index(value = ["username"], unique = true)])
data class TeacherEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    /**
     * Phase 1 only: SHA-256 of the seeded demo password. Phase 2 authenticates
     * against the ERP and this column stops being read.
     */
    val passwordHash: String,
    val email: String? = null,
    val remoteId: String? = null,
)

@Entity(tableName = "classes", indices = [Index("teacherId")])
data class SchoolClassEntity(
    @PrimaryKey val id: String,
    val name: String,
    val section: String? = null,
    val subject: String? = null,
    val teacherId: String,
    val remoteId: String? = null,
)

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val id: String,
    val rollNumber: String,
    val fullName: String,
    val avatarPath: String? = null,
    val remoteId: String? = null,
)

@Entity(
    tableName = "enrollments",
    primaryKeys = ["classId", "studentId"],
    indices = [Index("studentId"), Index("classId")],
)
data class EnrollmentEntity(
    val classId: String,
    val studentId: String,
)

@Entity(
    tableName = "attendance_sessions",
    // One session per class per day. Re-opening a day EDITS the existing
    // session instead of silently creating a duplicate record.
    indices = [Index(value = ["classId", "date"], unique = true)],
)
data class AttendanceSessionEntity(
    @PrimaryKey val id: String,
    val classId: String,
    /** ISO yyyy-MM-dd. A string, never an epoch — see TimeFormat. */
    val date: String,
    val takenByTeacherId: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Phase 2 uses this to push unsynced sessions. */
    val syncState: String,
)

@Entity(
    tableName = "attendance_records",
    primaryKeys = ["sessionId", "studentId"],
    foreignKeys = [
        ForeignKey(
            entity = AttendanceSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class AttendanceRecordEntity(
    val sessionId: String,
    val studentId: String,
    val status: String,
    val note: String? = null,
)

@Entity(tableName = "study_materials", indices = [Index("teacherId"), Index("classId")])
data class StudyMaterialEntity(
    @PrimaryKey val id: String,
    val teacherId: String,
    val classId: String? = null,
    val title: String,
    val kind: String,
    /** Phase 1: copied out of assets on demand. */
    val localPath: String? = null,
    /** Phase 2: LMS download URL. */
    val remoteUrl: String? = null,
    /**
     * Phase 1 scaffolding: which file under assets/seed/files backs this
     * material. Phase 2 populates remoteUrl instead and leaves this null.
     */
    val seedAssetFile: String? = null,
    val sizeBytes: Long? = null,
    val remoteId: String? = null,
)

@Entity(tableName = "note_documents")
data class NoteDocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    /** Null while a snapshot is awaiting a successful AI call. */
    val markdownPath: String? = null,
    val snapshotPath: String,
    val sourcePageId: String? = null,
    val model: String? = null,
    val createdAt: Long,
    val status: String,
    val failureMessage: String? = null,
)
