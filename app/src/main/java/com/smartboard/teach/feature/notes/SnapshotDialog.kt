package com.smartboard.teach.feature.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.StatusPresent
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted
import com.smartboard.teach.core.ui.theme.WarningAmber

sealed interface SnapshotPhase {
    data object Capturing : SnapshotPhase
    data object Summarizing : SnapshotPhase
    data class Done(val title: String) : SnapshotPhase

    /**
     * The board image is already on disk at this point. The message says so
     * explicitly rather than reading as a generic failure, because the two
     * mean very different things to a teacher.
     */
    data class Failed(val message: String) : SnapshotPhase
}

@Composable
fun SnapshotDialog(
    phase: SnapshotPhase,
    onDismiss: () -> Unit,
    onOpenNotes: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    val busy = phase is SnapshotPhase.Capturing || phase is SnapshotPhase.Summarizing

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        confirmButton = {
            when (phase) {
                is SnapshotPhase.Done -> TextButton(onClick = onOpenNotes) { Text("Open notes") }
                is SnapshotPhase.Failed -> TextButton(onClick = onOpenNotes) { Text("View in Notes") }
                else -> Unit
            }
        },
        dismissButton = if (!busy) {
            { TextButton(onClick = onDismiss) { Text("Close") } }
        } else {
            null
        },
        title = {
            Text(
                text = when (phase) {
                    SnapshotPhase.Capturing -> "Capturing the board"
                    SnapshotPhase.Summarizing -> "Creating notes"
                    is SnapshotPhase.Done -> "Notes created"
                    is SnapshotPhase.Failed -> "Board saved"
                },
                fontSize = dimens.titleSize,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(Modifier.widthIn(min = 360.dp)) {
                when (phase) {
                    SnapshotPhase.Capturing -> BusyRow("Flattening the board…")

                    SnapshotPhase.Summarizing -> BusyRow(
                        "Reading the board and writing notes. This can take a few seconds.",
                    )

                    is SnapshotPhase.Done -> IconRow(
                        icon = Icons.Filled.CheckCircle,
                        tint = StatusPresent,
                        text = "\"${phase.title}\" was saved to Notes.",
                    )

                    is SnapshotPhase.Failed -> Column {
                        IconRow(
                            icon = Icons.Filled.CloudOff,
                            tint = WarningAmber,
                            text = "The board image was saved. The summary could not be created.",
                        )
                        Spacer(Modifier.height(dimens.gutterSmall))
                        Text(
                            phase.message,
                            fontSize = dimens.labelSize,
                            color = TextOnSurfaceMuted,
                        )
                        Spacer(Modifier.height(dimens.gutterSmall))
                        Text(
                            "You can retry from the Notes screen when you are back online.",
                            fontSize = dimens.labelSize,
                            color = TextOnSurfaceMuted,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun BusyRow(message: String) {
    val dimens = SmartBoardTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(dimens.iconSize), strokeWidth = 2.dp)
        Spacer(Modifier.width(dimens.gutter))
        Text(message, fontSize = dimens.bodySize, color = TextOnSurfaceMuted)
    }
}

@Composable
private fun IconRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    text: String,
) {
    val dimens = SmartBoardTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(dimens.iconSize))
        Spacer(Modifier.width(dimens.gutter))
        Text(text, fontSize = dimens.bodySize)
    }
}
