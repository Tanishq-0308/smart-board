package com.smartboard.teach.data.remote.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// --- Request ---------------------------------------------------------------

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
)

@Serializable
data class Message(
    val role: String,
    val content: List<ContentPart>,
)

@Serializable
sealed interface ContentPart {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentPart

    @Serializable
    @SerialName("image_url")
    data class Image(@SerialName("image_url") val imageUrl: ImageUrl) : ContentPart
}

@Serializable
data class ImageUrl(
    val url: String,
    /** "high" gives the model the full tile grid — needed to read handwriting. */
    val detail: String = "high",
)

@Serializable
data class ResponseFormat(
    val type: String,
    @SerialName("json_schema") val jsonSchema: JsonSchema? = null,
)

@Serializable
data class JsonSchema(
    val name: String,
    val strict: Boolean,
    val schema: JsonObject,
)

// --- Response --------------------------------------------------------------

@Serializable
data class ChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
    val error: ApiError? = null,
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ResponseMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ResponseMessage(
    val role: String? = null,
    /** JSON *string* when structured outputs are used; parsed separately. */
    val content: String? = null,
    val refusal: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)

// --- Structured output payload --------------------------------------------

/**
 * The shape the model is FORCED to return via `strict: true` json_schema.
 *
 * Using structured outputs rather than "please reply with JSON" removes the
 * entire class of parse failures — the API rejects a non-conforming
 * generation rather than handing us prose to guess at.
 */
@Serializable
data class LessonNotesDto(
    val title: String = "",
    val summary: String = "",
    val topics: List<String> = emptyList(),
    @SerialName("keyPoints") val keyPoints: List<String> = emptyList(),
    val definitions: List<DefinitionDto> = emptyList(),
    val formulas: List<String> = emptyList(),
    @SerialName("followUpQuestions") val followUpQuestions: List<String> = emptyList(),
)

@Serializable
data class DefinitionDto(
    val term: String = "",
    val meaning: String = "",
)

/**
 * Structured output payload for a region lookup.
 *
 * `kind` arrives as a string rather than the domain enum: an unrecognised
 * value from a future model revision must degrade to OTHER, not throw while
 * a teacher is waiting mid-lesson.
 */
@Serializable
data class VisualLookupDto(
    val title: String = "",
    val kind: String = "OTHER",
    val explanation: String = "",
    val transcription: String = "",
    @SerialName("relatedTerms") val relatedTerms: List<String> = emptyList(),
    @SerialName("searchQuery") val searchQuery: String = "",
    @SerialName("isUnreadable") val isUnreadable: Boolean = false,
)
