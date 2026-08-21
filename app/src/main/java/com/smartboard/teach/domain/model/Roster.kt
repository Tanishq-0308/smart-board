package com.smartboard.teach.domain.model

import java.time.LocalDate

data class SchoolClass(
    val id: String,
    val name: String,
    val section: String? = null,
    val subject: String? = null,
    val teacherId: String,
    val studentCount: Int = 0,
    val remoteId: String? = null,
) {
    /** e.g. "Grade 8 - B" */
    val displayName: String get() = if (section.isNullOrBlank()) name else "$name - $section"
}

data class Student(
    val id: String,
    val rollNumber: String,
    val fullName: String,
    val avatarPath: String? = null,
    val remoteId: String? = null,
) {
    /** Initials for the avatar circle when no photo is available. */
    val initials: String
        get() = fullName.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .let { parts ->
                when {
                    parts.isEmpty() -> "?"
                    parts.size == 1 -> parts[0].take(2).uppercase()
                    else -> "${parts.first().first()}${parts.last().first()}".uppercase()
                }
            }
}

enum class AttendanceStatus { PRESENT, ABSENT, LATE;

    /** Single letter shown on the button — colour is never the only signal. */
    val letter: String get() = name.first().toString()
}

/**
 * Sync state exists in Phase 1 even though nothing syncs yet, so Phase 2 can
 * push pending sessions without a schema migration.
 */
enum class SyncState { LOCAL_ONLY, PENDING_SYNC, SYNCED }

data class AttendanceSession(
    val id: String,
    val classId: String,
    val date: LocalDate,
    val takenByTeacherId: String,
    val marks: Map<String, AttendanceStatus>,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
) {
    val presentCount: Int get() = marks.values.count { it == AttendanceStatus.PRESENT }
    val absentCount: Int get() = marks.values.count { it == AttendanceStatus.ABSENT }
    val lateCount: Int get() = marks.values.count { it == AttendanceStatus.LATE }
}

enum class MaterialKind { PDF, BOOK, IMAGE }

data class StudyMaterial(
    val id: String,
    val teacherId: String,
    val classId: String? = null,
    val title: String,
    val kind: MaterialKind,
    /** Present once the file exists on disk. Phase 1 copies from assets. */
    val localPath: String? = null,
    /** Phase 2: the LMS download URL. */
    val remoteUrl: String? = null,
    val sizeBytes: Long? = null,
    val remoteId: String? = null,
)
