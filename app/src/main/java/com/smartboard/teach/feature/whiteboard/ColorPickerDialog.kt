package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.ChromeBorder
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted

/**
 * Mix any pen colour: a saturation/brightness square over a hue bar.
 *
 * Hand-rolled HSV rather than a library: it is two gradients and two drag
 * handlers, and a dependency for that would outweigh the feature.
 */
@Composable
fun ColorPickerDialog(
    initial: Color,
    onPick: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val picked = Color.hsv(hue, sat, value)

    Dialog(onDismissRequest = onDismiss) {
        FloatingIsland {
            Column(
                Modifier.width(360.dp).padding(dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            ) {
                Text(stringResource(R.string.panel_colour_custom), color = TextOnChrome, fontSize = dimens.bodySize, fontWeight = FontWeight.Medium)

                // Saturation left→right, brightness top→bottom, for the chosen hue.
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            trackDrag { p ->
                                sat = (p.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                            }
                        },
                ) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val at = Offset(sat * size.width, (1f - value) * size.height)
                    drawCircle(Color.White, 10.dp.toPx(), at, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.Black, 12.dp.toPx(), at, style = Stroke(1.dp.toPx()))
                }

                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            trackDrag { p -> hue = (p.x / size.width).coerceIn(0f, 1f) * 360f }
                        },
                ) {
                    drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                    val x = hue / 360f * size.width
                    drawCircle(Color.White, size.height / 2 - 2.dp.toPx(), Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(picked)
                            .border(1.dp, ChromeBorder, CircleShape),
                    )
                    Text(
                        "#" + Integer.toHexString(picked.toArgb()).takeLast(6).uppercase(),
                        color = TextOnChromeMuted,
                        fontSize = dimens.labelSize,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = dimens.gutterSmall),
                    )
                    Spacer(Modifier.weight(1f))
                    DialogButton(stringResource(R.string.panel_cancel), Color.Transparent, TextOnChrome, onDismiss)
                    Spacer(Modifier.width(8.dp))
                    DialogButton(stringResource(R.string.panel_colour_add), Accent, Color.White) { onPick(picked) }
                }
            }
        }
    }
}

/** Reports every position of a press-and-drag, including the first touch. */
private suspend fun PointerInputScope.trackDrag(onPoint: (Offset) -> Unit) = awaitEachGesture {
    val down = awaitFirstDown()
    onPoint(down.position)
    down.consume()
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        onPoint(change.position)
        change.consume()
    }
}

@Composable
private fun DialogButton(text: String, background: Color, content: Color, onClick: () -> Unit) {
    Text(
        text,
        color = content,
        fontSize = SmartBoardTheme.dimens.labelSize,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
