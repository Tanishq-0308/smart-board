package com.smartboard.teach.data.remote.erp

import android.util.Base64
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.core.util.map
import com.smartboard.teach.data.repository.enc
import com.smartboard.teach.di.IoDispatcher
import com.smartboard.teach.domain.lessonpack.NcertPattern
import com.smartboard.teach.domain.model.AssignmentDraft
import com.smartboard.teach.domain.model.LessonNotes
import com.smartboard.teach.domain.repository.AssignmentRequest
import com.smartboard.teach.domain.repository.LessonShareService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lesson packs through the ERP's existing routes, unchanged:
 *
 * - `POST /api/ai/board/notes/save`: the merged notes become a class note.
 * - `GET /api/erp/course-materials`: polled until the ERP has indexed that note.
 * - `POST /api/ai/teacher/generate`: the assignment, grounded on that note alone
 *   (`material_ids`), so it covers only what was taught.
 * - `PATCH /api/erp/course-materials/{id}` and `POST /api/erp/homework`: sharing.
 */
@Singleton
class ErpLessonShareService @Inject constructor(
    private val api: ErpApi,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : LessonShareService {

    override suspend fun saveNotes(classId: String, subjectId: String?, notes: LessonNotes): AppResult<String> =
        api.post("/api/ai/board/notes/save",
            SaveNotesRequest(classId, subjectId, notes.title.take(200), notes.toDto(), share = false),
            SaveNotesRequest.serializer(), MaterialRow.serializer()).map { it.id }

    override suspend fun awaitIndexed(classId: String, materialId: String): Boolean {
        repeat(INDEX_POLLS) {
            val rows = api.getList("/api/erp/course-materials?section_id=${enc(classId)}&category=note",
                MaterialRow.serializer())
            val status = (rows as? AppResult.Success)?.data?.firstOrNull { it.id == materialId }?.aiStatus
            when (status) {
                "indexed" -> return true
                // Not indexed at all (or it failed): waiting longer will not help.
                null, "failed" -> if (rows is AppResult.Success) return false
            }
            delay(INDEX_POLL_MS)
        }
        return false
    }

    override suspend fun generateAssignment(request: AssignmentRequest): AppResult<AssignmentDraft> =
        api.post("/api/ai/teacher/generate", GenerateRequest(
            topic = request.topic.take(300),
            className = request.className.take(120),
            subject = request.subjectName.take(120),
            sectionId = request.classId,
            subjectId = request.subjectId,
            materialIds = listOf(request.noteMaterialId),
            numQuestions = request.questionCount,
            totalMarks = request.totalMarks,
            instructions = NcertPattern.instructions(request.size, request.subjectName),
        ), GenerateRequest.serializer(), GenerateResponse.serializer()).map { res ->
            AssignmentDraft(
                title = res.draft.title,
                instructions = res.draft.instructions,
                markingGuide = res.markingGuide.orEmpty(),
                questions = NcertPattern.toQuestions(res.draft.questions.map {
                    NcertPattern.RawQuestion(it.question, it.type, it.options, it.marks, it.answerGuide)
                }),
            )
        }

    override suspend fun shareNotes(materialId: String): AppResult<Unit> =
        api.patchRaw("/api/erp/course-materials/${enc(materialId)}", """{"visible_to_students":true}""",
            JsonElement.serializer()).map { }

    override suspend fun publish(
        classId: String,
        subjectId: String?,
        draft: AssignmentDraft,
        dueDate: String?,
        boardImage: File?,
    ): AppResult<String> {
        val attachment = boardImage?.takeIf { it.exists() && it.length() <= MAX_ATTACHMENT_BYTES }?.let {
            withContext(io) {
                HomeworkAttachment(name = "Board.jpg", type = "image/jpeg",
                    url = "data:image/jpeg;base64," + Base64.encodeToString(it.readBytes(), Base64.NO_WRAP))
            }
        }
        val body = HomeworkCreate(
            sectionId = classId,
            subjectId = subjectId,
            title = draft.title.take(200),
            description = describe(draft),
            dueDate = dueDate,
            markingGuide = draft.markingGuide.ifBlank { null },
            attachments = listOfNotNull(attachment),
            questions = draft.questions.map { q ->
                HomeworkQuestion(text = "[${q.section}] ${q.text}", marks = q.marks.coerceIn(1, 1000),
                    answer = q.answer, options = q.options.take(10))
            },
        )
        return api.post("/api/erp/homework", body, HomeworkCreate.serializer(), HomeworkRow.serializer()).map { it.id }
    }

    /** What the class reads above the questions: the instructions and the sections. */
    private fun describe(draft: AssignmentDraft): String = buildString {
        if (draft.instructions.isNotBlank()) append(draft.instructions.trim()).append("\n\n")
        val present = draft.questions.map { it.section }.toSet()
        NcertPattern.SECTIONS.filter { it.letter in present }.forEach { s ->
            append("Section ${s.letter}: ${s.title} (${s.marks} mark${if (s.marks == 1) "" else "s"} each)\n")
        }
    }.trim()

    private companion object {
        const val INDEX_POLLS = 15
        const val INDEX_POLL_MS = 2_000L

        /** Kept small: it travels inline as a data: URL. */
        const val MAX_ATTACHMENT_BYTES = 1_000_000L
    }
}

private fun LessonNotes.toDto() = NotesDto(
    title = title, summary = summary, topics = topics, keyPoints = keyPoints,
    definitions = definitions.map { DefinitionDto(it.term, it.meaning) },
    formulas = formulas, followUpQuestions = followUpQuestions,
)

@Serializable
private data class SaveNotesRequest(
    @SerialName("class_id") val classId: String,
    @SerialName("subject_id") val subjectId: String?,
    val title: String,
    val notes: NotesDto,
    val share: Boolean,
)

@Serializable
private data class MaterialRow(
    val id: String,
    @SerialName("ai_status") val aiStatus: String? = null,
)

@Serializable
private data class GenerateRequest(
    val kind: String = "assignment",
    val topic: String,
    @SerialName("class_name") val className: String,
    val subject: String,
    @SerialName("section_id") val sectionId: String,
    @SerialName("subject_id") val subjectId: String?,
    @SerialName("material_ids") val materialIds: List<String>,
    @SerialName("num_questions") val numQuestions: Int,
    val difficulty: String = "mixed",
    @SerialName("question_type") val questionType: String = "mixed",
    @SerialName("total_marks") val totalMarks: Int,
    val instructions: String,
)

@Serializable
private data class GenerateResponse(
    val draft: Draft,
    @SerialName("marking_guide") val markingGuide: String? = null,
) {
    @Serializable
    data class Draft(
        val title: String = "",
        val instructions: String = "",
        val questions: List<Question> = emptyList(),
    )

    @Serializable
    data class Question(
        val question: String = "",
        val type: String = "short",
        val options: List<String> = emptyList(),
        val marks: Int = 1,
        @SerialName("answer_guide") val answerGuide: String = "",
    )
}

@Serializable
private data class HomeworkCreate(
    @SerialName("section_id") val sectionId: String,
    @SerialName("subject_id") val subjectId: String?,
    val title: String,
    val description: String,
    @SerialName("task_type") val taskType: String = "assignment",
    @SerialName("publish_status") val publishStatus: String = "published",
    val notify: Boolean = true,
    @SerialName("due_date") val dueDate: String?,
    @SerialName("marking_guide") val markingGuide: String?,
    val attachments: List<HomeworkAttachment>,
    val questions: List<HomeworkQuestion>,
)

@Serializable
private data class HomeworkAttachment(val name: String, val type: String, val url: String)

@Serializable
private data class HomeworkQuestion(val text: String, val marks: Int, val answer: String, val options: List<String>)

@Serializable
private data class HomeworkRow(val id: String)
