package com.smartboard.teach.feature.whiteboard

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import java.net.URLEncoder

/**
 * In-app web search, docked beside the board.
 *
 * A side panel rather than full-screen, matching the reference: the board
 * stays live next to it, so a teacher can drop a diagram and start annotating
 * without closing the search and losing their place in the results.
 *
 * **Long-press any image to place it on the board.** [WebView.HitTestResult]
 * reports the image under a long-press directly, so this needs no JavaScript
 * injected into the page — which would break the moment a site changed its
 * markup, and is exactly the kind of thing that quietly stops working.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebSearchPane(
    onClose: () -> Unit,
    onImagePicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
) {
    var query by remember { mutableStateOf(initialQuery) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    fun search(text: String) {
        val term = text.trim()
        if (term.isEmpty()) return
        // Images tab by default: a teacher searching from a whiteboard is
        // nearly always after a picture to teach on, and the long-press is
        // what this pane exists for.
        val encoded = URLEncoder.encode(term, "UTF-8")
        webView?.loadUrl("https://www.google.com/search?q=$encoded&tbm=isch")
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(WEB_PANE_WIDTH)
            // OPAQUE, not the translucent island fill: this docks against the
            // board rather than floating on it, and the board clock showed
            // straight through a see-through header.
            .background(PANE_BACKGROUND)
            // Swallows taps that miss a control, so a stray press on the
            // pane's own chrome cannot land on the canvas behind it.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = { webView?.takeIf { it.canGoBack() }?.goBack() },
                enabled = canGoBack,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (canGoBack) TextOnChrome else TextOnChromeMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(SEARCH_FIELD, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (query.isEmpty()) {
                    Text("Search the web", color = TextOnChromeMuted, fontSize = 14.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextOnChrome, fontSize = 14.sp),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search(query) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            IconButton(onClick = { search(query) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = TextOnChrome,
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close search",
                    tint = TextOnChrome,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Text(
            text = "Press and hold an image to place it on the board",
            color = TextOnChromeMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color.White)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Desktop-width layout would render results at a size
                        // no one can read in a 420dp column.
                        settings.useWideViewPort = false
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        webViewClient = object : WebViewClient() {
                            // Keeps navigation INSIDE the pane. Without this,
                            // tapping a result can hand the URL to the system
                            // browser and dump the teacher out of the app
                            // mid-lesson.
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean = false

                            override fun doUpdateVisitedHistory(
                                view: WebView?,
                                url: String?,
                                isReload: Boolean,
                            ) {
                                canGoBack = view?.canGoBack() == true
                            }
                        }

                        setOnLongClickListener { view ->
                            val hit = (view as WebView).hitTestResult
                            val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                                hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                            val src = hit.extra
                            if (isImage && !src.isNullOrBlank()) {
                                onImagePicked(src)
                                true
                            } else {
                                // Not an image: let the page have its own
                                // long-press (text selection) rather than
                                // swallowing the gesture everywhere.
                                false
                            }
                        }

                        webView = this
                        if (initialQuery.isNotBlank()) {
                            loadUrl(
                                "https://www.google.com/search?q=" +
                                    URLEncoder.encode(initialQuery, "UTF-8") + "&tbm=isch",
                            )
                        } else {
                            loadUrl("https://www.google.com/?tbm=isch")
                        }
                    }
                },
                onRelease = {
                    webView = null
                    // Stops in-flight loads and detaches; a WebView left alive
                    // keeps a page running behind a closed panel.
                    it.stopLoading()
                    it.destroy()
                },
            )
        }
    }
}

/** Wide enough for a readable results column, narrow enough to leave board. */
val WEB_PANE_WIDTH = 420.dp
private val SEARCH_FIELD = Color(0x22FFFFFF)

/** Fully opaque; see the note on the Column background. */
private val PANE_BACKGROUND = Color(0xFF1C2530)
