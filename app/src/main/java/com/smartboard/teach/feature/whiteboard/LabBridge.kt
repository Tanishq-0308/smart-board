package com.smartboard.teach.feature.whiteboard

import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/** A message from a lab, already confirmed to be one of ours. */
data class LabMessage(val type: String, val body: JSONObject) {
    fun text(key: String): String = body.optString(key)
    fun number(key: String, fallback: Int = 0): Int = body.optInt(key, fallback)
}

/**
 * The one way a lab can speak to the board.
 *
 * Bound as `addJavascriptInterface(LabBridge(::onMessage), LabBridge.NAME)`.
 * **Without that call the lab is mute** — it renders and is perfectly usable,
 * but no `ready` and no snapshot will ever arrive, and nothing says so.
 *
 * Android's `@JavascriptInterface` passes only primitives, which is why the
 * whole protocol is strings of JSON rather than objects.
 *
 * [postMessage] is called on a WebView binder thread, never the main one, so
 * everything it hands on must get itself back to the main thread before it
 * touches any UI.
 */
class LabBridge(private val onMessage: (LabMessage) -> Unit) {

    @JavascriptInterface
    fun postMessage(json: String) {
        val body = runCatching { JSONObject(json) }.getOrNull() ?: return
        // A WebView hears from more than the page it was pointed at; the stamp
        // is what tells a lab's message apart from everyone else's chatter.
        if (body.optString("source") != SOURCE) return
        val type = body.optString("type")
        if (type.isEmpty()) return
        onMessage(LabMessage(type, body))
    }

    companion object {
        /** The name the page looks for: `window.LabHost`. */
        const val NAME = "LabHost"
        const val SOURCE = "jahnavis-lab"
    }
}

/**
 * Ask the lab in this WebView for something. Main thread only.
 *
 * [JSONObject.quote] is what makes this safe: it turns the request into a
 * properly escaped JavaScript string literal, so a quote or a newline in a
 * payload cannot break out of the statement being evaluated.
 */
fun WebView.askLab(request: JSONObject) {
    evaluateJavascript(
        "window.jahnavisLab && window.jahnavisLab.receive(${JSONObject.quote(request.toString())})",
        null,
    )
}

/** The requests a lab understands. */
object LabRequest {

    /**
     * A picture of the apparatus, as a `data:image/png;base64,…` URL.
     *
     * [scale] is clamped to 1..3 by the lab. A board is a big display, so 2 is
     * the sensible floor; the reply is base64 through a single interface call,
     * and a full-board capture at 2 runs to a few hundred kilobytes.
     */
    fun snapshot(id: Int = 1, scale: Int = 2, transparent: Boolean = false): JSONObject =
        JSONObject()
            .put("type", "snapshot")
            .put("id", id)
            .put("format", "png")
            .put("scale", scale)
            .put("transparent", transparent)

    fun theme(dark: Boolean): JSONObject =
        JSONObject().put("type", "theme").put("theme", if (dark) "dark" else "light")

    /** Forgets what the lab remembered and starts it again — for the next class. */
    fun reset(id: Int = 1): JSONObject = JSONObject().put("type", "reset").put("id", id)
}
