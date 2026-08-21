package com.smartboard.teach.feature.whiteboard

import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartboard.teach.core.ui.theme.Accent
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * Full-screen playback, with a scrubber and "capture this frame".
 *
 * The point of a video in a lesson is usually one MOMENT in it — a diagram, a
 * step in a process, a position in a demonstration. So the player's real job
 * is not playback but capture: scrub to the moment, take that frame onto the
 * board, and annotate it with the pen, shapes and geometry tools that already
 * exist there. The annotated frame is the artefact, not the video.
 *
 * Controls are drawn in COMPOSE rather than by [android.widget.MediaController].
 * MediaController anchors to a View and shows itself in its own popup window,
 * which has nothing dependable to attach to inside a Compose Dialog — and it
 * auto-hides after about three seconds, which is exactly wrong for a teacher
 * hunting for a frame while a class watches. These stay put.
 */
@Composable
fun VideoPlayerDialog(
    path: String,
    onDismiss: () -> Unit,
    onCaptureFrame: (positionMs: Int) -> Unit,
) {
    // Held so the controls can drive playback, and so Capture can ask where
    // playback has reached. A frame is NOT read off the screen: VideoView
    // draws on a SurfaceView, which screen capture reads as black. It is
    // decoded from the file at this position instead — exact, and at the
    // video's own resolution rather than the panel's.
    var player by remember { mutableStateOf<VideoView?>(null) }

    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }

    // While the teacher drags the scrubber, the thumb follows the FINGER, not
    // playback — otherwise the poll below fights the drag and the thumb jumps
    // back under their hand every quarter second.
    var scrubbingTo by remember { mutableStateOf<Float?>(null) }

    // Polling rather than a listener: MediaPlayer has no position callback.
    // 250ms is smooth enough for a scrubber and far cheaper than a frame tick.
    LaunchedEffect(player) {
        while (player != null) {
            player?.let { view ->
                if (scrubbingTo == null) positionMs = view.currentPosition
                isPlaying = view.isPlaying
                if (durationMs <= 0) durationMs = view.duration.coerceAtLeast(0)
            }
            delay(POLL_MS)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    VideoView(context).apply {
                        setVideoPath(path)
                        // Deliberately NO setMediaController: the Compose
                        // controls below replace it.
                        setOnPreparedListener { mp ->
                            durationMs = mp.duration.coerceAtLeast(0)
                            // Autoplay: the teacher has already tapped play
                            // once, on the board. Asking twice is a wasted
                            // beat in front of a class.
                            start()
                            isPlaying = true
                        }
                        setOnCompletionListener {
                            isPlaying = false
                            // Park the scrubber at the end rather than
                            // wherever the last poll left it.
                            positionMs = durationMs
                        }
                        // A file the panel cannot decode must not leave a
                        // black rectangle with no way out.
                        setOnErrorListener { _, _, _ ->
                            onDismiss()
                            true
                        }
                        player = this
                    }
                },
                onRelease = {
                    player = null
                    it.stopPlayback()
                },
            )

            // --- top bar: capture and close ---
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        val view = player ?: return@Button
                        // Paused first, so the frame captured is the one the
                        // teacher is looking at — a playing video would have
                        // moved on by the time the decode finishes.
                        view.pause()
                        isPlaying = false
                        onCaptureFrame(view.currentPosition)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        Icons.Filled.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("  Capture frame")
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close video",
                        tint = Color.White,
                    )
                }
            }

            // --- bottom bar: transport and scrubber ---
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(CONTROL_SCRIM)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ControlButton(
                        icon = Icons.Filled.Replay10,
                        label = "Back 10 seconds",
                    ) {
                        player?.let { it.seekTo((it.currentPosition - SKIP_MS).coerceAtLeast(0)) }
                    }

                    ControlButton(
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        label = if (isPlaying) "Pause" else "Play",
                    ) {
                        player?.let { view ->
                            if (view.isPlaying) {
                                view.pause()
                            } else {
                                // Play at the end REPLAYS. VideoView sits on
                                // the final frame after completion and start()
                                // there does nothing, so the button looks
                                // broken — which matters most for the short
                                // clips a teacher replays to make a point.
                                if (durationMs > 0 &&
                                    view.currentPosition >= durationMs - END_EPSILON_MS
                                ) {
                                    view.seekTo(0)
                                    positionMs = 0
                                }
                                view.start()
                            }
                            isPlaying = view.isPlaying
                        }
                    }

                    ControlButton(
                        icon = Icons.Filled.Forward10,
                        label = "Forward 10 seconds",
                    ) {
                        player?.let {
                            it.seekTo((it.currentPosition + SKIP_MS).coerceAtMost(durationMs))
                        }
                    }

                    TimeLabel(scrubbingTo?.toInt() ?: positionMs)

                    Slider(
                        value = scrubbingTo ?: positionMs.toFloat(),
                        onValueChange = { scrubbingTo = it },
                        onValueChangeFinished = {
                            scrubbingTo?.let { target ->
                                player?.seekTo(target.toInt())
                                positionMs = target.toInt()
                            }
                            scrubbingTo = null
                        },
                        // A zero range would make the track NaN before the
                        // video reports its duration.
                        valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )

                    TimeLabel(durationMs)
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

/** Fixed width and a monospace face, so digits changing never shift the slider. */
@Composable
private fun TimeLabel(millis: Int) {
    Text(
        text = formatDuration(millis),
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        modifier = Modifier.width(56.dp),
    )
}

/** m:ss, or h:mm:ss once a video runs past an hour. */
internal fun formatDuration(millis: Int): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** True when the video file is still on disk and worth opening. */
fun videoExists(path: String): Boolean = File(path).let { it.exists() && it.length() > 0 }

private const val POLL_MS = 250L
private const val SKIP_MS = 10_000

/** How near the end counts as finished, for the replay-on-play behaviour. */
private const val END_EPSILON_MS = 250
private val CONTROL_SCRIM = Color.Black.copy(alpha = 0.65f)
