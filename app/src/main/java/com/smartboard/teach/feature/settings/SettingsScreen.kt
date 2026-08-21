package com.smartboard.teach.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.StatusPresent
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted
import com.smartboard.teach.core.ui.theme.WarningAmber

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val dimens = SmartBoardTheme.dimens
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = dimens.touchTarget + dimens.gutterLarge,
                    top = dimens.gutterLarge,
                    end = dimens.gutterLarge,
                    bottom = dimens.gutterLarge,
                ),
        ) {
            Column(Modifier.widthIn(max = 820.dp)) {
                SectionHeader("Pen and touch")
                Text(
                    text = "Interactive boards report pen input differently. If the pen " +
                        "behaves oddly, adjust these before assuming a fault.",
                    fontSize = dimens.labelSize,
                    color = TextOnSurfaceMuted,
                )
                Spacer(Modifier.height(dimens.gutter))

                SettingRow(
                    title = "Stylus only",
                    detail = "Ignore finger and palm touches entirely. The most reliable " +
                        "palm rejection on a board that has a pen.",
                    checked = settings.stylusOnlyMode,
                    onCheckedChange = viewModel::setStylusOnly,
                )
                SettingRow(
                    title = "Pressure sensitivity",
                    detail = "Vary stroke width with pen pressure. Turn off if your board " +
                        "reports a fixed pressure and strokes look uneven.",
                    checked = settings.pressureSensitivity,
                    onCheckedChange = viewModel::setPressure,
                )
                SettingRow(
                    title = "Pen eraser button",
                    detail = "Treat the eraser end of the pen as an eraser, whatever tool " +
                        "is selected.",
                    checked = settings.honourEraserButton,
                    onCheckedChange = viewModel::setEraserButton,
                )
                SettingRow(
                    title = "Snap shapes",
                    detail = "Turn rough freehand circles, rectangles and lines into clean " +
                        "shapes when you lift the pen. Turn off to keep your ink exactly " +
                        "as drawn.",
                    checked = settings.shapeRecognition,
                    onCheckedChange = viewModel::setShapeRecognition,
                )
                SettingRow(
                    title = "Pointer debug overlay",
                    detail = "Show live pointer type, pressure and contact count on the " +
                        "board. Use this when setting up new hardware.",
                    checked = settings.showPointerDebug,
                    onCheckedChange = viewModel::setPointerDebug,
                )

                Spacer(Modifier.height(dimens.gutterLarge))
                SectionHeader("Display")
                SettingRow(
                    title = "24-hour clock",
                    detail = "Show the header clock in 24-hour time.",
                    checked = settings.use24HourClock,
                    onCheckedChange = viewModel::set24HourClock,
                )

                Spacer(Modifier.height(dimens.gutterLarge))
                SectionHeader("AI notes")
                StatusLine(
                    ok = viewModel.isAiConfigured,
                    okText = "Configured — model ${viewModel.aiModel}",
                    notOkText = "No API key. Add OPENAI_API_KEY to local.properties and " +
                        "rebuild. Board snapshots are still saved and can be summarised later.",
                )

                Spacer(Modifier.height(dimens.gutterLarge))
                SectionHeader("Storage")
                Text(
                    text = "Removes saved board pages and imported backgrounds. " +
                        "Notes and class lists are kept.",
                    fontSize = dimens.labelSize,
                    color = TextOnSurfaceMuted,
                )
                Spacer(Modifier.height(dimens.gutterSmall))
                OutlinedButton(
                    onClick = viewModel::clearBoardData,
                    enabled = !uiState.isClearing,
                    shape = RoundedCornerShape(dimens.cornerRadius),
                    modifier = Modifier.height(dimens.touchTarget),
                ) {
                    Text(if (uiState.isClearing) "Clearing…" else "Clear board data")
                }

                Spacer(Modifier.height(dimens.gutterLarge))
                SectionHeader("About")
                Text(
                    text = "Smart Board ${viewModel.appVersion}",
                    fontSize = dimens.labelSize,
                    color = TextOnSurfaceMuted,
                )
                Text(
                    text = "Phase 1 — local data. ERP and LMS integration follows in Phase 2.",
                    fontSize = dimens.labelSize,
                    color = TextOnSurfaceMuted,
                )
                Spacer(Modifier.height(dimens.gutterLarge))
            }
        }

        uiState.message?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(dimens.gutter),
                action = { TextButton(onClick = viewModel::dismissMessage) { Text("Dismiss") } },
            ) { Text(message) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val dimens = SmartBoardTheme.dimens
    Text(
        text = text,
        fontSize = dimens.titleSize,
        fontWeight = FontWeight.SemiBold,
        color = TextOnSurface,
        modifier = Modifier.padding(bottom = dimens.gutterSmall),
    )
}

@Composable
private fun SettingRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = dimens.bodySize, color = TextOnSurface)
            Text(detail, fontSize = dimens.labelSize, color = TextOnSurfaceMuted)
        }
        Spacer(Modifier.width(dimens.gutter))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusLine(ok: Boolean, okText: String, notOkText: String) {
    val dimens = SmartBoardTheme.dimens
    val color = if (ok) StatusPresent else WarningAmber
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(dimens.cornerRadius))
            .padding(dimens.gutter),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = if (ok) "OK" else "!",
            fontSize = dimens.bodySize,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(Modifier.width(dimens.gutter))
        Text(
            text = if (ok) okText else notOkText,
            fontSize = dimens.labelSize,
            color = TextOnSurfaceMuted,
        )
    }
}
