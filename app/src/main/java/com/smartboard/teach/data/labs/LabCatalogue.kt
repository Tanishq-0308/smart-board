package com.smartboard.teach.data.labs

import android.content.Context
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where a catalogue was read from, and what it said. */
data class LoadedCatalogue(
    /** The site root every `embed` address is joined onto. */
    val base: String,
    val manifest: LabManifest,
    val fromNetwork: Boolean,
)

/**
 * Finds the labs, wherever they are.
 *
 * The hosted copy is preferred so a lab published to the site reaches every
 * board without an app release. The copy in `assets/labs/` answers when there
 * is no network, which in a school is not the exceptional case.
 */
@Singleton
class LabCatalogue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun load(): AppResult<LoadedCatalogue> = withContext(io) {
        fromNetwork()?.let { return@withContext AppResult.Success(it) }
        fromAssets()?.let { return@withContext AppResult.Success(it) }
        AppResult.Failure(
            AppError.Network("The labs could not be reached, and none are stored on this board."),
        )
    }

    private fun fromNetwork(): LoadedCatalogue? = try {
        val request = Request.Builder().url("$HOSTED$MANIFEST").build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful || body.isBlank()) {
                null
            } else {
                LoadedCatalogue(HOSTED, json.decodeFromString(body), fromNetwork = true)
            }
        }
    } catch (e: Exception) {
        // No network, bad DNS, a captive portal in the staff room — all of it
        // means the same thing here: try the copy on the board.
        null
    }

    private fun fromAssets(): LoadedCatalogue? = try {
        // Assets are read, not fetched: OkHttp cannot open an asset path, and
        // the WebView reaches these through LabAssetServer instead.
        val text = context.assets.open("$ASSET_ROOT/$MANIFEST")
            .bufferedReader()
            .use { it.readText() }
        LoadedCatalogue(LabAssetServer.BASE, json.decodeFromString(text), fromNetwork = false)
    } catch (e: Exception) {
        null
    }

    companion object {
        /**
         * Where the built site is published.
         *
         * Change this to your host. It must end in a slash: every address in
         * the manifest is a hash route joined straight onto it.
         */
        const val HOSTED = "https://labs.example.com/"

        const val MANIFEST = "labs.json"
        const val ASSET_ROOT = "labs"
    }
}
