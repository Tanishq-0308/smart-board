package com.smartboard.teach.data.remote.erp

import com.smartboard.teach.BuildConfig
import com.smartboard.teach.R
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.core.util.AppText
import com.smartboard.teach.data.session.ErpSession
import com.smartboard.teach.data.session.SessionManager
import com.smartboard.teach.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
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
 * The one door to the school's Skolar ERP.
 *
 * Sign-in gives an account token that cannot call the ERP; it is exchanged for a
 * school-scoped token (15 minutes) by a context switch. When a call comes back
 * 401 the refresh token (7 days, rotating) is spent for a new pair, the context
 * is switched again and the call is retried once. A failed refresh means the
 * teacher has to sign in again, so the session is cleared and the board drops
 * to guest.
 */
@Singleton
class ErpApi @Inject constructor(
    private val client: OkHttpClient,
    baseJson: Json,
    private val session: SessionManager,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val refreshLock = Mutex()

    // The ERP's models require fields the board's DTOs give defaults to
    // (`person_type`), so defaults always go on the wire.
    private val json = Json(baseJson) { encodeDefaults = true }

    val baseUrl: String = BuildConfig.ERP_BASE_URL.trimEnd('/')

    // --- Unauthenticated (sign-in) ---

    suspend fun <B, T> postPublic(path: String, body: B, bodySer: KSerializer<B>, outSer: KSerializer<T>): AppResult<T> =
        send(request(path, "POST", json.encodeToString(bodySer, body), token = null), outSer)

    suspend fun <T> getWith(path: String, token: String, outSer: KSerializer<T>): AppResult<T> =
        send(request(path, "GET", null, token), outSer)

    suspend fun <B, T> postWith(path: String, token: String, body: B, bodySer: KSerializer<B>, outSer: KSerializer<T>): AppResult<T> =
        send(request(path, "POST", json.encodeToString(bodySer, body), token), outSer)

    // --- Signed-in calls, school-scoped ---

    suspend fun <T> get(path: String, outSer: KSerializer<T>): AppResult<T> = authed(outSer) { token ->
        request(path, "GET", null, token)
    }

    suspend fun <T> getList(path: String, itemSer: KSerializer<T>): AppResult<List<T>> =
        get(path, ListSerializer(itemSer))

    suspend fun <B, T> post(path: String, body: B, bodySer: KSerializer<B>, outSer: KSerializer<T>): AppResult<T> =
        authed(outSer) { token -> request(path, "POST", json.encodeToString(bodySer, body), token) }

    /** For a body built as raw JSON, e.g. one that embeds a server-shaped object unchanged. */
    suspend fun <T> postRaw(path: String, body: String, outSer: KSerializer<T>): AppResult<T> =
        authed(outSer) { token -> request(path, "POST", body, token) }

    suspend fun <T> patchRaw(path: String, body: String, outSer: KSerializer<T>): AppResult<T> =
        authed(outSer) { token -> request(path, "PATCH", body, token) }

    private suspend fun <T> authed(outSer: KSerializer<T>, build: (String) -> Request): AppResult<T> {
        val current = session.erpSession()
            ?: return AppResult.Failure(AppError.NotAuthenticated())
        val first = send(build(current.accessToken), outSer)
        if ((first as? AppResult.Failure)?.error.let { it !is AppError.Http || it.code != 401 }) return first

        val renewed = renew(current) ?: return AppResult.Failure(AppError.NotAuthenticated())
        return send(build(renewed.accessToken), outSer)
    }

    /** Spends the refresh token once, even when several calls hit 401 together. */
    private suspend fun renew(stale: ErpSession): ErpSession? = refreshLock.withLock {
        val now = session.erpSession() ?: return null
        if (now.accessToken != stale.accessToken) return now // someone else already renewed

        val pair = when (val r = postPublic("/api/auth/refresh", RefreshRequest(now.refreshToken),
            RefreshRequest.serializer(), TokenPair.serializer())) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> {
                // Refresh token expired or revoked: only a fresh sign-in helps.
                // Offline is not that, so the session is kept.
                if (r.error is AppError.Http) session.clear()
                return null
            }
        }
        // The old refresh token is spent now, so keep the new one even if the
        // switch below fails.
        session.saveErpSession(now.copy(refreshToken = pair.refreshToken.ifBlank { now.refreshToken }))
        val scoped = (postWith("/api/tenancy/context/switch", pair.accessToken,
            ContextSwitchRequest(now.schoolId, now.role), ContextSwitchRequest.serializer(),
            ScopedToken.serializer()) as? AppResult.Success)?.data ?: return null
        val updated = now.copy(accessToken = scoped.accessToken,
            refreshToken = pair.refreshToken.ifBlank { now.refreshToken })
        session.saveErpSession(updated)
        updated
    }

    private fun request(path: String, method: String, body: String?, token: String?): Request =
        Request.Builder()
            .url(baseUrl + path)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .method(method, body?.toRequestBody(JSON_TYPE))
            .build()

    private suspend fun <T> send(request: Request, outSer: KSerializer<T>): AppResult<T> = withContext(io) {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) return@withContext AppResult.Failure(httpError(response.code, text))
                val payload = text.ifBlank { "null" }
                AppResult.Success(json.decodeFromString(outSer, payload))
            }
        } catch (e: UnknownHostException) {
            AppResult.Failure(AppError.Network())
        } catch (e: SocketTimeoutException) {
            AppResult.Failure(AppError.Timeout())
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network(e.message ?: AppText.get(R.string.error_network_generic)))
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Unknown(AppText.get(R.string.error_erp_read, t.message.orEmpty()), t))
        }
    }

    /** The ERP writes `error.message` for the teacher, so it is shown as it stands. */
    private fun httpError(code: Int, body: String): AppError {
        val message = runCatching {
            json.decodeFromString(ErpErrorEnvelope.serializer(), body).error?.message
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return AppError.Http(code, message ?: when (code) {
            429 -> AppText.get(R.string.error_erp_busy)
            in 500..599 -> AppText.get(R.string.error_erp_unavailable)
            else -> AppText.get(R.string.error_erp_http, code)
        })
    }

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
