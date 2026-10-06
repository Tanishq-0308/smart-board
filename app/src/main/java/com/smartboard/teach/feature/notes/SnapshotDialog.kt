package com.smartboard.teach.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.StatusPresent
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted
import com.smartboard.teach.core.ui.theme.WarningAmber
import com.smartboard.teach.domain.lessonpack.NcertPattern
import com.smartboard.teach.domain.lessonpack.PackDefaults
import com.smartboard.teach.domain.model.AssignmentSize
import com.smartboard.teach.domain.model.SchoolClass
import com.smartboard.teach.domain.usecase.PackProgress

sealed interface SnapshotPhase {
    data object Capturing : SnapshotPhase

    /** Notes, saving, assignment; null before the first step reports. */
    data class Working(val progress: PackProgress?) : SnapshotPhase

    data class Done(
        val noteId: String,
        val title: String,
        val leftOut: Int,
        val hasAssignment: Boolean,
        val notesOnly: Boolean = false,
    ) : SnapshotPhase

    /**
     * When [noteId] is set the lesson's images are already saved and the pack
     * resumes from Notes; the message says so rather than reading as a loss.
     */
    data class Failed(val message: String, val noteId: String?) : SnapshotPhase
}

/** The class, subject and length a signed-in teacher picks before a snapshot. */
data class PackSetup(
    val classes: List<SchoolClass>,
    val subjects: Map<String, List<PackDefaults.Subject>>,
    val classId: String?,
    val subjectId: String?,
    val size: AssignmentSize,
    /** False for notes only. */
    val withAssignment: Boolean = true,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PackSetupDialog(
    setup: PackSetup,
    onChange: (PackSetup) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = setup.classId != null || setup.classes.isEmpty()) {
                Text(stringResource(R.string.pack_setup_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.panel_cancel)) } },
        title = { Text(stringResource(R.string.pack_setup_title), fontSize = dimens.titleSize, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.widthIn(min = 420.dp, max = 720.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.pack_setup_intro), fontSize = dimens.labelSize, color = TextOnSurfaceMuted)
                Spacer(Modifier.height(dimens.gutter))
                Label(stringResource(R.string.pack_setup_make))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !setup.withAssignment,
                        onClick = { onChange(setup.copy(withAssignment = false)) },
                        label = { Text(stringResource(R.string.pack_setup_notes_only)) },
                    )
                    FilterChip(
                        selected = setup.withAssignment,
                        onClick = { onChange(setup.copy(withAssignment = true)) },
                        label = { Text(stringResource(R.string.pack_setup_notes_and_assignment)) },
                    )
                }
                Spacer(Modifier.height(dimens.gutter))
                Label(stringResource(R.string.pack_setup_class))
                if (setup.classes.isEmpty()) {
                    Text(stringResource(R.string.pack_setup_no_classes), fontSize = dimens.labelSize, color = TextOnSurfaceMuted)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    setup.classes.forEach { c ->
                        FilterChip(
                            selected = c.id == setup.classId,
                            onClick = { onChange(setup.copy(classId = c.id, subjectId = setup.subjects[c.id]?.firstOrNull()?.id)) },
                            label = { Text(c.displayName) },
                        )
                    }
                }
                val subjects = setup.classId?.let { setup.subjects[it] }.orEmpty()
                if (subjects.size > 1) {
                    Spacer(Modifier.height(dimens.gutter))
                    Label(stringResource(R.string.pack_setup_subject))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        subjects.forEach { s ->
                            FilterChip(
                                selected = s.id == setup.subjectId || (setup.subjectId == null && s == subjects.first()),
                                onClick = { onChange(setup.copy(subjectId = s.id)) },
                                label = { Text(s.name) },
                            )
                        }
                    }
                }
                if (setup.withAssignment) {
                Spacer(Modifier.height(dimens.gutter))
                Label(stringResource(R.string.pack_setup_size))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssignmentSize.entries.forEach { size ->
                        FilterChip(
                            selected = size == setup.size,
                            onClick = { onChange(setup.copy(size = size)) },
                            label = {
                                Text(stringResource(R.string.pack_setup_size_option, stringResource(sizeName(size)),
                                    NcertPattern.questionCount(size), NcertPattern.totalMarks(size)))
                            },
                        )
                    }
                }
                }
            }
        },
    )
}

@Composable
private fun Label(text: String) {
    val dimens = SmartBoardTheme.dimens
    Text(text, fontSize = dimens.bodySize, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(4.dp))
}

fun sizeName(size: AssignmentSize): Int = when (size) {
    AssignmentSize.SHORT -> R.string.pack_size_short
    AssignmentSize.STANDARD -> R.string.pack_size_standard
    AssignmentSize.LONG -> R.string.pack_size_long
}

@Composable
fun SnapshotDialog(
    phase: SnapshotPhase,
    onDismiss: () -> Unit,
    onOpenNote: (String?) -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    val busy = phase is SnapshotPhase.Capturing || phase is SnapshotPhase.Working

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        confirmButton = {
            when (phase) {
                is SnapshotPhase.Done -> TextButton(onClick = { onOpenNote(phase.noteId) }) {
                    Text(stringResource(R.string.pack_review))
                }
                is SnapshotPhase.Failed -> TextButton(onClick = { onOpenNote(phase.noteId) }) {
                    Text(stringResource(R.string.snapshot_view_in_notes))
                }
                else -> Unit
            }
        },
        dismissButton = if (!busy) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.snapshot_close)) } }
        } else {
            null
        },
        title = {
            Text(
                text = when (phase) {
                    SnapshotPhase.Capturing -> stringResource(R.string.snapshot_title_capturing)
                    is SnapshotPhase.Working -> stringResource(R.string.pack_title_working)
                    is SnapshotPhase.Done -> stringResource(R.string.pack_title_done)
                    is SnapshotPhase.Failed -> stringResource(
                        if (phase.noteId != null) R.string.snapshot_title_failed else R.string.pack_title_nothing,
                    )
                },
                fontSize = dimens.titleSize,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(Modifier.widthIn(min = 360.dp)) {
                when (phase) {
                    SnapshotPhase.Capturing -> BusyRow(stringResource(R.string.pack_capturing))

                    is SnapshotPhase.Working -> BusyRow(
                        when (val p = phase.progress) {
                            is PackProgress.Notes -> stringResource(R.string.pack_progress_notes, p.done, p.total)
                            PackProgress.SavingNotes -> stringResource(R.string.pack_progress_saving)
                            PackProgress.WritingAssignment -> stringResource(R.string.pack_progress_assignment)
                            null -> stringResource(R.string.snapshot_summarizing_detail)
                        },
                    )

                    is SnapshotPhase.Done -> Column {
                        IconRow(
                            icon = Icons.Filled.CheckCircle,
                            tint = StatusPresent,
                            text = stringResource(
                                when {
                                    phase.hasAssignment -> R.string.pack_done_detail
                                    phase.notesOnly -> R.string.pack_done_notes_only
                                    else -> R.string.snapshot_saved_to_notes
                                },
                                phase.title,
                            ),
                        )
                        if (phase.leftOut > 0) {
                            Spacer(Modifier.height(dimens.gutterSmall))
                            Text(stringResource(R.string.pack_left_out, phase.leftOut), fontSize = dimens.labelSize,
                                color = TextOnSurfaceMuted)
                        }
                    }

                    is SnapshotPhase.Failed -> Column {
                        if (phase.noteId != null) {
                            IconRow(
                                icon = Icons.Filled.CloudOff,
                                tint = WarningAmber,
                                text = stringResource(R.string.pack_failed_detail),
                            )
                            Spacer(Modifier.height(dimens.gutterSmall))
                        }
                        Text(phase.message, fontSize = dimens.labelSize, color = TextOnSurfaceMuted)
                        if (phase.noteId != null) {
                            Spacer(Modifier.height(dimens.gutterSmall))
                            Text(stringResource(R.string.snapshot_retry_hint), fontSize = dimens.labelSize,
                                color = TextOnSurfaceMuted)
                        }
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
