package com.smartboard.teach.feature.whiteboard

import android.media.Ringtone
import android.media.RingtoneManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import kotlinx.coroutines.delay

/**
 * The lesson timer, floating over the board.
 *
 * Modelled on the reference panel: a small dark island with a title row, a
 * large HH:MM:SS readout and reset / play-pause / fullscreen beneath it. It is
 * chrome, not board content — never in the stroke list, undo stack or export,
 * so it does not become part of the lesson a teacher saves.
 *
 * Draggable, because the one place a timer must not sit is on top of the thing
 * the class is being timed on, and that is different every lesson.
 */
@Composable
fun TimerPanel(onClose: () -> Unit, modifier: Modifier = Modifier) {
    var state by remember { mutableStateOf(TimerState()) }
    var fullscreen by remember { mutableStateOf(false) }
    var alarmEnabled by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    /** The alarm currently sounding, or null. Held so it can be stopped. */
    var ringing by remember { mutableStateOf<Ringtone?>(null) }

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val context = LocalContext.current

    // The clock ticks HERE and the state only does arithmetic, so the timer
    // cannot drift because a recomposition happened to tick it twice.
    LaunchedEffect(state.isRunning) {
        if (!state.isRunning) return@LaunchedEffect
        var last = System.currentTimeMillis()
        while (state.isRunning) {
            delay(TICK_MS)
            val now = System.currentTimeMillis()
            // Elapsed WALL time, not the tick interval: a busy frame or a
            // sleeping panel would otherwise lose seconds off the lesson.
            state = state.tick(now - last)
            last = now
        }
    }

    // Rings once on the transition to finished, then clears the flag.
    LaunchedEffect(state.hasFinished) {
        if (!state.hasFinished) return@LaunchedEffect
        if (alarmEnabled) {
            // The Ringtone is HELD, not fired and forgotten. Without a
            // reference there is nothing to call stop() on, and an alarm a
            // teacher cannot silence in front of a class is worse than no
            // alarm at all.
            runCatching {
                RingtoneManager
                    .getRingtone(
                        context,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    )
                    ?.also { tone ->
                        // Loops on purpose: a default alarm tone is a few
                        // seconds long, and a countdown that pings once from
                        // across the room gets missed.
                        tone.isLooping = true
                        tone.play()
                        ringing = tone
                    }
            }
        }
        state = state.alarmAcknowledged()
    }

    /** Silences the alarm, if one is sounding. */
    fun stopAlarm() {
        ringing?.let { tone -> runCatching { tone.stop() } }
        ringing = null
    }

    // The alarm must never outlive the panel: a closed timer still ringing has
    // no UI left to stop it.
    DisposableEffect(Unit) {
        onDispose { ringing?.let { tone -> runCatching { tone.stop() } } }
    }

    Box(
        modifier = modifier
            .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()) }
            // Drag on the panel's own chrome only. This is a small island, not
            // a full-screen layer, so it cannot swallow the canvas's pointers.
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offsetX += drag.x
                    offsetY += drag.y
                }
            },
    ) {
        FloatingIsland(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (state.mode == TimerMode.COUNTDOWN) "Timer" else "Stopwatch",
                        color = TextOnChrome,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            // Tapping the title swaps mode: a second control
                            // for it would crowd a panel this small.
                            .clickable {
                                state = state.withMode(
                                    if (state.mode == TimerMode.COUNTDOWN) {
                                        TimerMode.STOPWATCH
                                    } else {
                                        TimerMode.COUNTDOWN
                                    },
                                )
                                editing = false
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )

                    IconButton(
                        onClick = {
                            alarmEnabled = !alarmEnabled
                            if (!alarmEnabled) stopAlarm()
                        },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = if (alarmEnabled) {
                                Icons.Filled.NotificationsActive
                            } else {
                                Icons.Filled.NotificationsOff
                            },
                            contentDescription = if (alarmEnabled) "Alarm on" else "Alarm off",
                            tint = if (alarmEnabled) ALARM_ON else TextOnChromeMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    IconButton(
                        onClick = { stopAlarm(); onClose() },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close timer",
                            tint = TextOnChrome,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Text(
                    text = if (editing) formatTimer(parseTimerDigits(typed)) else {
                        formatTimer(state.displayMs)
                    },
                    color = if (editing) Accent else TextOnChrome,
                    fontSize = if (fullscreen) 96.sp else 34.sp,
                    fontWeight = FontWeight.Light,
                    // Monospace so the readout does not jitter as digits change
                    // — a proportional 1 is narrower than an 8.
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        // Not editable while the alarm sounds: the keypad
                        // would cover the only control that silences it.
                        .clickable(
                            enabled = state.mode == TimerMode.COUNTDOWN && ringing == null,
                        ) {
                            if (editing) {
                                state = state.withPreset(parseTimerDigits(typed))
                                editing = false
                            } else {
                                editing = true
                                typed = ""
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )

                if (ringing != null) {
                    // While the alarm sounds, this REPLACES the transport row.
                    // A teacher whose class has just been interrupted should
                    // not have to find the right small icon; there is one
                    // thing to do and it fills the panel.
                    Text(
                        text = "Stop alarm",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Accent)
                            // Also RESETS: a countdown that stops at 00:00:00
                            // has no time left to start again, so the teacher
                            // would have to retype the duration to reuse it.
                            .clickable {
                                stopAlarm()
                                state = state.reset()
                            }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                } else if (editing) {
                    Keypad(
                        onDigit = { typed += it },
                        onClear = { typed = "" },
                        onDone = {
                            state = state.withPreset(parseTimerDigits(typed))
                            editing = false
                        },
                    )
                    Presets { millis ->
                        state = state.withPreset(millis)
                        editing = false
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlIcon(Icons.Filled.Replay, "Reset") { state = state.reset() }

                        ControlIcon(
                            icon = if (state.isRunning) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            label = if (state.isRunning) "Pause" else "Start",
                            tint = if (state.canStart) TextOnChrome else TextOnChromeMuted,
                        ) {
                            state = state.toggle()
                        }

                        ControlIcon(
                            icon = if (fullscreen) {
                                Icons.Filled.FullscreenExit
                            } else {
                                Icons.Filled.Fullscreen
                            },
                            label = if (fullscreen) "Shrink" else "Enlarge",
                        ) {
                            fullscreen = !fullscreen
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Presets(onPick: (Long) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        TimerState.PRESETS_MS.forEach { millis ->
            Text(
                text = "${millis / 60_000}m",
                color = TextOnChrome,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PRESET_BACKGROUND)
                    .clickable { onPick(millis) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * A numeric pad rather than the system keyboard.
 *
 * An IME on a wall-mounted panel covers half the board and takes a beat to
 * appear; ten fixed keys do not move and are reachable standing at the board.
 */
@Composable
private fun Keypad(onDigit: (Char) -> Unit, onClear: () -> Unit, onDone: () -> Unit) {
    val rows = listOf("123", "456", "789")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { digit -> Key(digit.toString()) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Key("C", onClick = onClear)
            Key("0") { onDigit('0') }
            Key("OK", accent = true, onClick = onDone)
        }
    }
}

@Composable
private fun Key(label: String, accent: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (accent) Color.White else TextOnChrome,
        fontSize = 15.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .size(width = 46.dp, height = 34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (accent) Accent else PRESET_BACKGROUND)
            .clickable(onClick = onClick)
            .padding(top = 7.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun ControlIcon(
    icon: ImageVector,
    label: String,
    tint: Color = TextOnChrome,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

private const val TICK_MS = 200L
private val ALARM_ON = Color(0xFF4ADE80)
private val PRESET_BACKGROUND = Color(0x22FFFFFF)
