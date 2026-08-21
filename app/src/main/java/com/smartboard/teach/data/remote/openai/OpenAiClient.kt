package com.smartboard.teach.data.remote.openai

import android.graphics.Bitmap
import com.smartboard.teach.BuildConfig
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.core.util.BitmapUtils
import com.smartboard.teach.di.IoDispatcher
import com.smartboard.teach.domain.model.LessonNotes
import com.smartboard.teach.domain.repository.NotesAiService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct OpenAI chat/completions client for board summarization.
 *
 * SECURITY: [BuildConfig.OPENAI_API_KEY] is compiled into the APK and is
 * trivially extractable — R8 does not hide string constants. Phase 1 ships
 * this consciously with a spend-capped dedicated key; Phase 2 replaces this
 * class with a server-proxy implementation of [NotesAiService].
 */
@Singleton
class OpenAiClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NotesAiService {

    override val isConfigured: Boolean
        get() = BuildConfig.OPENAI_API_KEY.isNotBlank()

    override val modelName: String = NotesPrompt.DEFAULT_MODEL

    override suspend fun summarizeBoard(snapshot: Bitmap): AppResult<LessonNotes> =
        withContext(ioDispatcher) {
            if (!isConfigured) {
                return@withContext AppResult.Failure(AppError.AiNotConfigured())
            }

            var scaled: Bitmap? = null
            try {
                scaled = BitmapUtils.downscale(snapshot)
                val jpegBytes = BitmapUtils.toJpegBytes(scaled)
                val dataUrl = BitmapUtils.toDataUrl(jpegBytes)

                val payload = ChatRequest(
                    model = NotesPrompt.DEFAULT_MODEL,
                    messages = listOf(
                        Message(
                            role = "system",
                            content = listOf(ContentPart.Text(NotesPrompt.SYSTEM_PROMPT)),
                        ),
                        Message(
                            role = "user",
                            content = listOf(
                                ContentPart.Text(NotesPrompt.USER_PROMPT),
                                ContentPart.Image(ImageUrl(url = dataUrl)),
                            ),
                        ),
                    ),
                    responseFormat = ResponseFormat(
                        type = "json_schema",
                        jsonSchema = JsonSchema(
                            name = "lesson_notes",
                            strict = true,
                            schema = NotesPrompt.schema(json),
                        ),
                    ),
                    maxTokens = NotesPrompt.MAX_TOKENS,
                )

                val request = Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                    .post(
                        json.encodeToString(ChatRequest.serializer(), payload)
                            .toRequestBody(JSON_MEDIA_TYPE),
                    )
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    // OkHttp 5: body is non-null.
                    val body = response.body.string()

                    if (!response.isSuccessful) {
                        return@withContext AppResult.Failure(httpError(response.code, body))
                    }

                    val parsed = json.decodeFromString(ChatResponse.serializer(), body)

                    // Real token counts, so image-size tuning is measured
                    // rather than guessed from tile arithmetic.
                    parsed.usage?.let { u ->
                        android.util.Log.i(
                            "SmartBoardCost",
                            "notes model=${parsed.model} " +
                                "img=${scaled.width}x${scaled.height} " +
                                "jpegKB=${jpegBytes.size / 1024} " +
                                "prompt=${u.promptTokens} completion=${u.completionTokens} " +
                                "total=${u.totalTokens}",
                        )
                    }

                    parsed.error?.message?.let {
                        return@withContext AppResult.Failure(AppError.AiResponse(it))
                    }

                    val message = parsed.choices.firstOrNull()?.message
                    message?.refusal?.let {
                        return@withContext AppResult.Failure(
                            AppError.AiResponse("The model declined: $it"),
                        )
                    }

                    val content = message?.content
                    if (content.isNullOrBlank()) {
                        return@withContext AppResult.Failure(
                            AppError.AiResponse("The AI returned an empty response."),
                        )
                    }

                    // Structured outputs put the JSON payload in `content` as a
                    // string, so it needs a second decode.
                    val dto = json.decodeFromString(LessonNotesDto.serializer(), content)
                    AppResult.Success(dto.toDomain())
                }
            } catch (e: UnknownHostException) {
                AppResult.Failure(AppError.Network())
            } catch (e: SocketTimeoutException) {
                AppResult.Failure(AppError.Timeout())
            } catch (e: IOException) {
                AppResult.Failure(AppError.Network(e.message ?: "Network error."))
            } catch (t: Throwable) {
                AppResult.Failure(AppError.AiResponse("Could not read the AI response: ${t.message}"))
            } finally {
                // downscale() returns the source unchanged when no scaling was
                // needed; only recycle a genuinely new bitmap.
                if (scaled != null && scaled !== snapshot) scaled.recycle()
            }
        }

    private fun httpError(code: Int, body: String): AppError {
        val detail = runCatching {
            json.decodeFromString(ChatResponse.serializer(), body).error?.message
        }.getOrNull()

        return when (code) {
            401 -> AppError.Http(code, "The OpenAI API key was rejected. Check local.properties.")
            429 -> AppError.Http(code, "Rate limit or quota exceeded on the OpenAI account.")
            in 500..599 -> AppError.Http(code, "OpenAI is unavailable right now. Try again shortly.")
            else -> AppError.Http(code, detail ?: "OpenAI request failed (HTTP $code).")
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private fun LessonNotesDto.toDomain() = LessonNotes(
    title = title.ifBlank { "Board notes" },
    summary = summary,
    topics = topics,
    keyPoints = keyPoints,
    definitions = definitions.map { LessonNotes.Definition(it.term, it.meaning) },
    formulas = formulas,
    followUpQuestions = followUpQuestions,
)
