package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** What an export is currently doing, for the dialog to reflect. */
sealed interface ExportPhase {
    /** Waiting for the teacher to choose a format. */
    data object Choosing : ExportPhase
    data object Working : ExportPhase
    data class Done(val displayPath: String) : ExportPhase
    data class Failed(val message: String) : ExportPhase
}

/**
 * Format picker and result for saving a selection out of the app.
 *
 * One dialog for both formats rather than two menu entries: a teacher who has
 * just annotated a frame is deciding "how do I want this", and showing both
 * options together is the whole decision in one place. It also gives somewhere
 * to report WHERE the file landed, which matters — an export the teacher
 * cannot find afterwards may as well not have happened.
 */
@Composable
fun ExportDialog(
    phase: ExportPhase,
    onSavePng: () -> Unit,
    onSavePdf: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (phase) {
                    is ExportPhase.Choosing -> "Save selection"
                    is ExportPhase.Working -> "Saving…"
                    is ExportPhase.Done -> "Saved"
                    is ExportPhase.Failed -> "Could not save"
                },
            )
        },
        text = {
            when (phase) {
                is ExportPhase.Choosing -> Column(Modifier.fillMaxWidth()) {
                    Text("Choose a format.")
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = onSavePng) {
                            Icon(Icons.Filled.Image, contentDescription = null)
                            Text("  Image (PNG)")
                        }
                        OutlinedButton(
                            onClick = onSavePdf,
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                            Text("  PDF")
                        }
                    }
                }

                is ExportPhase.Working -> Text("Writing the file…")
                is ExportPhase.Done -> Text("Saved to ${phase.displayPath}")
                is ExportPhase.Failed -> Text(phase.message)
            }
        },
        confirmButton = {
            // Nothing to confirm while choosing — the format buttons ARE the
            // action, and a greyed-out OK beside them is just noise.
            if (phase !is ExportPhase.Choosing) {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (phase is ExportPhase.Choosing) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
