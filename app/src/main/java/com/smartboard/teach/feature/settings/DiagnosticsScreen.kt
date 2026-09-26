package com.smartboard.teach.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.component.chromeInset
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.StatusPresent
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted
import com.smartboard.teach.core.ui.theme.WarningAmber

/**
 * Device diagnostics, for installers at a school. The report is static; the
 * pad on the right measures what the panel ACTUALLY delivers — declared touch
 * features and real behaviour differ often enough on generic panels.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = SmartBoardTheme.dimens
    val context = LocalContext.current
    val items = remember { collectDiagnostics(context) }

    // Same left inset as Settings, so the floating menu button never covers the title.
    Column(
        modifier.fillMaxSize().padding(
            start = dimens.touchTarget + dimens.gutterLarge,
            top = dimens.gutterLarge,
            end = dimens.gutterLarge,
            bottom = dimens.gutterLarge,
        ),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.chromeInset()) {
            Text(stringResource(R.string.action_back))
        }
        Text(
            stringResource(R.string.diag_title),
            fontSize = dimens.titleSize,
            fontWeight = FontWeight.SemiBold,
            color = TextOnSurface,
        )
        Spacer(Modifier.height(dimens.gutter))

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(dimens.gutterLarge)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                items.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            when (item.ok) { true -> "✓"; false -> "!"; null -> "·" },
                            color = when (item.ok) { true -> StatusPresent; false -> WarningAmber; null -> TextOnSurfaceMuted },
                            fontWeight = FontWeight.Bold,
                            fontSize = dimens.bodySize,
                            modifier = Modifier.width(28.dp),
                        )
                        Text(
                            stringResource(item.label),
                            color = TextOnSurfaceMuted,
                            fontSize = dimens.bodySize,
                            modifier = Modifier.weight(1f),
                        )
                        Text(item.value, color = TextOnSurface, fontSize = dimens.bodySize, modifier = Modifier.weight(1.4f))
                    }
                }
            }
            TouchTestPad(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** Touch here with fingers and the pen: reports what the panel really sends. */
@Composable
private fun TouchTestPad(modifier: Modifier) {
    val dimens = SmartBoardTheme.dimens
    var active by remember { mutableIntStateOf(0) }
    var maxPoints by remember { mutableIntStateOf(0) }
    var tools by remember { mutableStateOf(emptySet<String>()) }
    val stylus = stringResource(R.string.status_pointer_stylus)
    val eraser = stringResource(R.string.board_eraser)
    val mouse = stringResource(R.string.status_pointer_mouse)
    val finger = stringResource(R.string.status_pointer_finger)
    var pressure by remember { mutableFloatStateOf(0f) }
    var minPressure by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var maxPressure by remember { mutableFloatStateOf(0f) }

    Box(
        modifier
            .border(1.dp, TextOnSurfaceMuted, RoundedCornerShape(dimens.cornerRadius))
            .background(TextOnSurfaceMuted.copy(alpha = 0.06f), RoundedCornerShape(dimens.cornerRadius))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val down = event.changes.filter { it.pressed }
                        active = down.size
                        maxPoints = maxOf(maxPoints, down.size)
                        down.forEach { change ->
                            tools = tools + when (change.type) {
                                PointerType.Stylus -> stylus
                                PointerType.Eraser -> eraser
                                PointerType.Mouse -> mouse
                                else -> finger
                            }
                            pressure = change.pressure
                            minPressure = minOf(minPressure, change.pressure)
                            maxPressure = maxOf(maxPressure, change.pressure)
                            change.consume()
                        }
                    }
                }
            }
            .padding(dimens.gutter),
    ) {
        val hasPressure = maxPoints > 0
        val varies = hasPressure && maxPressure - minPressure > PRESSURE_VARIES
        Column {
            Text(stringResource(R.string.diag_pad_title), fontWeight = FontWeight.SemiBold, color = TextOnSurface, fontSize = dimens.bodySize)
            Text(stringResource(R.string.diag_pad_hint), color = TextOnSurfaceMuted, fontSize = dimens.labelSize)
            Spacer(Modifier.height(dimens.gutter))
            val mono = Modifier.padding(vertical = 2.dp)
            Text(stringResource(R.string.diag_pad_active, active), fontFamily = FontFamily.Monospace, color = TextOnSurface, modifier = mono)
            Text(stringResource(R.string.diag_pad_max, maxPoints), fontFamily = FontFamily.Monospace, color = TextOnSurface, modifier = mono)
            Text(
                stringResource(R.string.diag_pad_tools, tools.sorted().joinToString().ifEmpty { "–" }),
                fontFamily = FontFamily.Monospace, color = TextOnSurface, modifier = mono,
            )
            Text(
                stringResource(R.string.diag_pad_pressure, "%.3f".format(pressure)),
                fontFamily = FontFamily.Monospace, color = TextOnSurface, modifier = mono,
            )
            if (hasPressure) {
                Text(
                    stringResource(
                        if (varies) R.string.diag_pad_pressure_varies else R.string.diag_pad_pressure_constant,
                    ),
                    color = if (varies) StatusPresent else WarningAmber,
                    fontSize = dimens.labelSize,
                    modifier = Modifier.align(Alignment.Start),
                )
            }
        }
    }
}

/** Spread of pressure readings that counts as real pressure support. */
private const val PRESSURE_VARIES = 0.05f
