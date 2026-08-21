package com.smartboard.teach.data.remote.openai

import android.graphics.Bitmap
import com.smartboard.teach.BuildConfig
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.core.util.BitmapUtils
import com.smartboard.teach.di.IoDispatcher
import com.smartboard.teach.domain.model.LookupKind
import com.smartboard.teach.domain.model.VisualLookup
import com.smartboard.teach.domain.repository.VisualLookupService
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
 * OpenAI vision client for explaining a lassoed board region.
 *
 * SECURITY: same caveat as [OpenAiClient] — the key is compiled into the APK
 * and Phase 2 must replace this with a server-proxy implementation of
 * [VisualLookupService].
 */
@Singleton
class OpenAiLookupClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : VisualLookupService {

    override val isConfigured: Boolean
        get() = BuildConfig.OPENAI_API_KEY.isNotBlank()

    override suspend fun explainRegion(region: Bitmap): AppResult<VisualLookup> =
        withContext(ioDispatcher) {
            if (!isConfigured) {
                return@withContext AppResult.Failure(AppError.AiNotConfigured())
            }

            var scaled: Bitmap? = null
            try {
                // NOTE the different ceiling from the notes flow. A lasso is
                // usually a few hundred pixels of handwriting; downscaling it
                // to the full-board budget would throw away exactly the detail
                // that makes an equation legible. Only genuinely huge
                // selections are scaled at all, and never below their own size.
                scaled = BitmapUtils.downscale(region, LOOKUP_MAX_EDGE_PX)
                val dataUrl = BitmapUtils.toDataUrl(
                    BitmapUtils.toJpegBytes(scaled, LOOKUP_JPEG_QUALITY),
                )

                val payload = ChatRequest(
                    model = LookupPrompt.DEFAULT_MODEL,
                    messages = listOf(
                        Message(
                            role = "system",
                            content = listOf(ContentPart.Text(LookupPrompt.SYSTEM_PROMPT)),
                        ),
                        Message(
                            role = "user",
                            content = listOf(
                                ContentPart.Text(LookupPrompt.USER_PROMPT),
                                ContentPart.Image(ImageUrl(url = dataUrl)),
                            ),
                        ),
                    ),
                    responseFormat = ResponseFormat(
                        type = "json_schema",
                        jsonSchema = JsonSchema(
                            name = "visual_lookup",
                            strict = true,
                            schema = LookupPrompt.schema(json),
                        ),
                    ),
                    maxTokens = LookupPrompt.MAX_TOKENS,
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
                    val body = response.body.string()

                    if (!response.isSuccessful) {
                        return@withContext AppResult.Failure(httpError(response.code, body))
                    }

                    val parsed = json.decodeFromString(ChatResponse.serializer(), body)

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

                    val dto = json.decodeFromString(VisualLookupDto.serializer(), content)
                    AppResult.Success(dto.toDomain())
                }
            } catch (e: UnknownHostException) {
                AppResult.Failure(AppError.Network())
            } catch (e: SocketTimeoutException) {
                AppResult.Failure(AppError.Timeout())
            } catch (e: IOException) {
                AppResult.Failure(AppError.Network(e.message ?: "Network error."))
            } catch (t: Throwable) {
                AppResult.Failure(
                    AppError.AiResponse("Could not read the AI response: ${t.message}"),
                )
            } finally {
                // downscale() returns the source unchanged when no scaling was
                // needed; only recycle a genuinely new bitmap. The caller owns
                // `region` itself.
                if (scaled != null && scaled !== region) scaled.recycle()
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

        /**
         * Higher than the notes ceiling because a crop is small to begin with.
         * This is an upper guard against a teacher lassoing the entire board,
         * not a target size.
         */
        const val LOOKUP_MAX_EDGE_PX = 2048

        /** Handwriting is thin, high-contrast line art; JPEG artefacts hurt it. */
        const val LOOKUP_JPEG_QUALITY = 92
    }
}

private fun VisualLookupDto.toDomain() = VisualLookup(
    title = title.ifBlank { "Selected region" },
    kind = runCatching { LookupKind.valueOf(kind) }.getOrDefault(LookupKind.OTHER),
    explanation = explanation,
    transcription = transcription,
    relatedTerms = relatedTerms,
    searchQuery = searchQuery.ifBlank { title },
    isUnreadable = isUnreadable,
)
