package com.smartboard.teach.data.labs

import android.content.res.AssetManager
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.IOException

/**
 * Serves the copy of the labs site in `assets/labs/` to a WebView over a real
 * origin, so a classroom with no network still has every lab.
 *
 * Why not just load it from `file://`: the site's JavaScript is ES modules,
 * which a WebView fetches under CORS rules, and a `file://` page has no origin
 * to fetch with. The request is refused, nothing mounts, and the pane is blank
 * with only a console message to say why. Turning that check off WebView-wide
 * with `allowUniversalAccessFromFileURLs` works and is a bigger hammer than
 * this needs. Handing the same bytes back under an `https://` origin instead
 * means modules, `fetch` and `localStorage` all behave exactly as they do on
 * the hosted copy — one code path rather than two.
 *
 * [HOST] is AndroidX's reserved asset hostname: it is guaranteed not to
 * resolve, so a request that somehow escapes this fails fast rather than
 * reaching a real server.
 */
class LabAssetServer(
    private val assets: AssetManager,
    private val root: String = "labs",
) {

    /** The response for [url], or null to let the WebView handle it normally. */
    fun intercept(url: Uri): WebResourceResponse? {
        if (!url.host.equals(HOST, ignoreCase = true)) return null
        val path = url.path ?: return null
        if (!path.startsWith(PREFIX)) return null

        val relative = path.removePrefix(PREFIX).ifEmpty { "index.html" }
        // A path that climbs out of the asset root is not a typo to correct,
        // it is an attempt to read the app's other assets. Refuse it.
        if (relative.contains("..")) return null

        return try {
            WebResourceResponse(
                mimeOf(relative),
                "utf-8",
                assets.open("$root/$relative"),
            ).apply {
                // The manifest is fetched by the page itself; without this the
                // read is refused even though it came from the same origin.
                responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
            }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * The type matters more than usual here: a WebView refuses to run a module
     * script that does not arrive as JavaScript, so a wrong guess shows up as
     * a blank pane rather than as a mis-styled one.
     */
    private fun mimeOf(path: String): String = when (path.substringAfterLast('.', "")) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "woff2" -> "font/woff2"
        "woff" -> "font/woff"
        "ttf" -> "font/ttf"
        else -> "application/octet-stream"
    }

    companion object {
        /** Reserved by AndroidX for exactly this, and never resolvable. */
        const val HOST = "appassets.androidplatform.net"
        private const val PREFIX = "/labs/"

        /** The base a bundled copy is reached at. */
        const val BASE = "https://$HOST$PREFIX"
    }
}
