package com.smartboard.teach.feature.whiteboard

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.ChromeDarkElevated
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import com.smartboard.teach.core.ui.theme.WarningAmber
import com.smartboard.teach.data.labs.LabAssetServer
import com.smartboard.teach.data.labs.LabEntry
import com.smartboard.teach.data.labs.LabSubject

private const val TAG = "LabsPane"

/**
 * Interactive labs, docked beside the board.
 *
 * The board contains no lab. It reads `labs.json`, shows what it finds, and
 * opens the address the entry gives it — so a lab published to the site is on
 * every board the next time this opens, with nothing here rebuilt or released.
 *
 * The same shape as [WebSearchPane], and different in one way that matters: a
 * lab has no `<img>` to long-press, so a picture comes off it by *asking*. The
 * camera button sends a snapshot request over [LabBridge] and the lab replies
 * with the apparatus as a PNG data URL — the drawing alone, without the
 * control panels, because that is what belongs on a board. That URL goes
 * straight to `insertWebImage`, which already decodes base64 `data:` URLs.
 *
 * Wider than the search pane on purpose: a column of image results reads
 * fine at 420dp, and an optical bench does not.
 */
@Composable
fun LabsPane(
    onClose: () -> Unit,
    onSnapshot: (String) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    viewModel: LabsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var open by remember { mutableStateOf<LabEntry?>(null) }
    // Held here rather than inside the WebView composable because the camera
    // button in the header is what uses them.
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var asked by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(LAB_PANE_WIDTH)
            // Opaque, and swallowing stray taps, for the reasons the search
            // pane is: it docks against the board rather than floating over
            // it, and a press on its own chrome must not reach the canvas.
            .background(PANE_BACKGROUND)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (open != null) {
                IconButton(
                    onClick = { open = null; ready = false },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "All labs",
                        tint = TextOnChrome,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Text(
                text = open?.title ?: "Labs",
                color = TextOnChrome,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            if (state.offline) {
                // Worth saying: the shelf is the copy on the board, so a lab
                // published this morning will not be on it.
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(WarningAmber, CircleShape),
                )
            }

            Spacer(Modifier.weight(1f))

            if (open != null) {
                IconButton(
                    // Grey until the lab says it can answer. Pressed earlier
                    // the request arrives before there is a drawing to take a
                    // picture of, and comes back an error.
                    enabled = ready,
                    onClick = {
                        asked += 1
                        webView?.askLab(LabRequest.snapshot(id = asked))
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = "Put it on the board",
                        tint = if (ready) Accent else TextOnChromeMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = TextOnChromeMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val lab = open
            when {
                state.loading -> CircularProgressIndicator(
                    color = Accent,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.error != null -> LabsProblem(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::load,
                    modifier = Modifier.align(Alignment.Center),
                )

                lab == null -> LabShelf(
                    labs = state.labs,
                    subjects = state.subjects,
                    onPick = { open = it; ready = false },
                )

                // Keyed on the slug so changing lab builds a clean WebView
                // rather than reusing one holding the last lab's state.
                else -> key(lab.slug) {
                    LabWebView(
                        url = state.base + lab.embed + "?theme=" + if (dark) "dark" else "light",
                        onReady = { ready = true },
                        onSnapshot = onSnapshot,
                        onAttached = { webView = it },
                        onReleased = { webView = null; ready = false },
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LabWebView(
    url: String,
    onReady: () -> Unit,
    onSnapshot: (String) -> Unit,
    onAttached: (WebView) -> Unit,
    onReleased: () -> Unit,
) {
    val context = LocalContext.current
    val assetServer = remember(context) { LabAssetServer(context.assets) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                // The labs remember the setup a class was left on. Without
                // this they forget it at every navigation, and `reset` has
                // nothing to clear.
                settings.domStorageEnabled = true
                // A lab is a drawing at the size of the pane, not a desktop
                // page scaled down.
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = true
                // The labs pan and zoom their own graph paper; leave the
                // WebView's zoom out of it or the two fight over one pinch.
                settings.builtInZoomControls = false
                overScrollMode = View.OVER_SCROLL_NEVER

                webViewClient = object : WebViewClient() {
                    // Serves the bundled copy when the URL points at it, and
                    // is inert for the hosted one.
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? = request?.url?.let(assetServer::intercept)

                    // Keeps navigation inside the pane, as the search pane
                    // does: a lab handing a URL to the system browser would
                    // dump the teacher out of the app mid-lesson.
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean = false
                }

                addJavascriptInterface(
                    LabBridge { message ->
                        // Arrives on a binder thread. `post` puts it back on
                        // the main one before it reaches any Compose state.
                        post {
                            when (message.type) {
                                "ready" -> onReady()
                                "snapshot" -> onSnapshot(message.text("data"))
                                "error" -> Log.w(
                                    TAG,
                                    "${message.text("code")}: ${message.text("message")}",
                                )
                            }
                        }
                    },
                    LabBridge.NAME,
                )

                onAttached(this)
                loadUrl(url)
            }
        },
        onRelease = { view ->
            onReleased()
            view.removeJavascriptInterface(LabBridge.NAME)
            // A WebView left alive keeps a lab running behind a closed pane.
            view.stopLoading()
            view.destroy()
        },
    )
}

/** One card per lab, sized for a finger on a board rather than a mouse. */
@Composable
private fun LabShelf(
    labs: List<LabEntry>,
    subjects: List<LabSubject>,
    onPick: (LabEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(labs, key = { it.slug }) { lab ->
            LabCard(
                lab = lab,
                accent = subjects.accentFor(lab.subject),
                onClick = { onPick(lab) },
            )
        }
    }
}

@Composable
private fun LabCard(lab: LabEntry, accent: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChromeDarkElevated, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = lab.topic.uppercase(),
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = lab.title,
            color = TextOnChrome,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text = lab.blurb, color = TextOnChromeMuted, fontSize = 13.sp)
        if (lab.level.isNotEmpty()) {
            Text(text = lab.level, color = TextOnChromeMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LabsProblem(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = message, color = TextOnChromeMuted, fontSize = 14.sp)
        Row(
            modifier = Modifier.clickable(onClick = onRetry).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp),
            )
            Text(text = "Try again", color = Accent, fontSize = 14.sp)
        }
    }
}

/** A subject's colour from the manifest, or the board's accent if it has none. */
private fun List<LabSubject>.accentFor(id: String): Color {
    val hex = firstOrNull { it.id == id }?.accent ?: return Accent
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Accent)
}

/** Wide enough for a ray diagram; the search pane's 420dp is not. */
val LAB_PANE_WIDTH = 560.dp

/** Fully opaque; see the note on the pane background in [WebSearchPane]. */
private val PANE_BACKGROUND = Color(0xFF1C2530)
