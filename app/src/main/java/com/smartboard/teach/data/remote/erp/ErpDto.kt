package com.smartboard.teach.data.remote.erp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire shapes of the Skolar ERP routes the board uses. The ERP is the source of
 * truth for them (ERP_SMS/docs/smart-board-api.md and each route's Pydantic
 * model); every field defaults so an added server field never breaks a board.
 */

@Serializable
data class ErpErrorEnvelope(val error: ErpErrorBody? = null)

@Serializable
data class ErpErrorBody(val code: String = "", val message: String = "")

// --- Sign-in ---

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginByIdRequest(
    @SerialName("school_code") val schoolCode: String,
    @SerialName("user_id") val userId: String,
    val password: String,
)

@Serializable
data class TokenPair(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String = "",
)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class MembershipDto(
    @SerialName("school_id") val schoolId: String,
    @SerialName("school_name") val schoolName: String = "",
    val role: String = "",
    val status: String = "",
)

@Serializable
data class ContextSwitchRequest(@SerialName("school_id") val schoolId: String, val role: String)

@Serializable
data class ScopedToken(@SerialName("access_token") val accessToken: String)

@Serializable
data class StaffDto(
    val id: String,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val email: String? = null,
    @SerialName("user_id") val userId: String? = null,
)

// --- Classes, roster, timetable ---

@Serializable
data class BoardClassDto(
    val id: String,
    val name: String = "",
    val section: String? = null,
    val subject: String? = null,
)

@Serializable
data class BoardStudentDto(
    val id: String,
    val rollNumber: String = "",
    val fullName: String = "",
)

@Serializable
data class Page<T>(val items: List<T> = emptyList())

@Serializable
data class TimetableSlotDto(
    @SerialName("section_id") val sectionId: String,
    @SerialName("subject_id") val subjectId: String? = null,
    @SerialName("day_of_week") val dayOfWeek: Int = 0,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("is_break") val isBreak: Boolean = false,
)

@Serializable
data class SubjectDto(val id: String, val name: String = "")

// --- Attendance ---

@Serializable
data class AttendanceRecordDto(
    @SerialName("person_type") val personType: String = "student",
    @SerialName("person_id") val personId: String,
    val date: String,
    val status: String,
    @SerialName("section_id") val sectionId: String,
)

@Serializable
data class AttendanceBulkRequest(val records: List<AttendanceRecordDto>)

// --- Study material ---

@Serializable
data class BoardMaterialDto(
    val id: String,
    val title: String = "",
    val kind: String = "PDF",
    val remoteUrl: String? = null,
    val sizeBytes: Long? = null,
)

/** A row of `GET /api/erp/course-materials` (metadata only). */
@Serializable
data class CourseMaterialDto(
    val id: String,
    @SerialName("section_id") val sectionId: String? = null,
    val title: String = "",
    @SerialName("file_name") val fileName: String = "",
    @SerialName("file_type") val fileType: String = "",
    @SerialName("file_size") val fileSize: Long? = null,
)

@Serializable
data class MaterialFileDto(
    @SerialName("file_name") val fileName: String = "",
    @SerialName("file_type") val fileType: String = "",
    /** Base64 bytes, or an https URL when the school stores files in S3. */
    @SerialName("file_data") val fileData: String = "",
)

// --- AI ---

@Serializable
data class ImageRequest(val image: String)

@Serializable
data class NotesDto(
    val title: String = "",
    val summary: String = "",
    val topics: List<String> = emptyList(),
    val keyPoints: List<String> = emptyList(),
    val definitions: List<DefinitionDto> = emptyList(),
    val formulas: List<String> = emptyList(),
    val followUpQuestions: List<String> = emptyList(),
    val model: String = "",
)

@Serializable
data class DefinitionDto(val term: String = "", val meaning: String = "")

@Serializable
data class LookupDto(
    val title: String = "",
    val kind: String = "OTHER",
    val explanation: String = "",
    val transcription: String = "",
    val relatedTerms: List<String> = emptyList(),
    val searchQuery: String = "",
    val isUnreadable: Boolean = false,
)
