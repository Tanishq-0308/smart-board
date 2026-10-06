package com.smartboard.teach.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.StatusPresent
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted
import com.smartboard.teach.core.ui.theme.WarningAmber
import com.smartboard.teach.domain.lessonpack.NcertPattern
import com.smartboard.teach.domain.model.AssignmentQuestion
import com.smartboard.teach.domain.model.LessonPack
import com.smartboard.teach.domain.model.NoteStatus
import com.smartboard.teach.domain.usecase.PackProgress
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Callbacks for the lesson pack review. */
class PackActions(
    val onEdit: (AssignmentQuestion) -> Unit,
    val onDelete: (String) -> Unit,
    val onDueDate: (LocalDate) -> Unit,
    val onRegenerate: () -> Unit,
    val onPublishAssignment: () -> Unit,
    val onPublishNotes: () -> Unit,
    val onResume: () -> Unit,
    val onChooseClass: () -> Unit,
)

enum class PackTab { NOTES, ASSIGNMENT }

/**
 * A lesson pack's review: who it is for and what is running, then the notes
 * and the assignment on separate tabs, each with its own publish button.
 */
@Composable
fun LessonPackReview(
    pack: LessonPack,
    status: NoteStatus,
    failure: String?,
    busy: PackBusy?,
    markdown: String?,
    actions: PackActions,
    modifier: Modifier = Modifier,
    notes: @Composable (String) -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    var tab by remember { mutableStateOf(PackTab.NOTES) }
    Column(modifier) {
        PackHeader(pack, status, failure, busy, actions)
        Spacer(Modifier.height(dimens.gutterSmall))
        TabRow(selectedTabIndex = tab.ordinal, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            Tab(selected = tab == PackTab.NOTES, onClick = { tab = PackTab.NOTES },
                text = { Text(stringResource(R.string.pack_tab_notes), fontSize = dimens.bodySize) })
            Tab(selected = tab == PackTab.ASSIGNMENT, onClick = { tab = PackTab.ASSIGNMENT },
                text = { Text(stringResource(R.string.pack_tab_assignment), fontSize = dimens.bodySize) })
        }
        Spacer(Modifier.height(dimens.gutter))
        when (tab) {
            PackTab.NOTES -> NotesTab(pack, status, busy, markdown, actions, notes)
            PackTab.ASSIGNMENT -> AssignmentTab(pack, status, busy, actions)
        }
    }
}

@Composable
private fun PackHeader(pack: LessonPack, status: NoteStatus, failure: String?, busy: PackBusy?, actions: PackActions) {
    val dimens = SmartBoardTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (pack.classId != null) {
                    listOf(pack.className, pack.subjectName).filter { it.isNotBlank() }.joinToString(" · ")
                } else {
                    stringResource(R.string.pack_review_no_class)
                },
                fontSize = dimens.titleSize, fontWeight = FontWeight.SemiBold, color = TextOnSurface,
            )
            if (!pack.classLocked) {
                Spacer(Modifier.width(dimens.gutter))
                TextButton(onClick = actions.onChooseClass, enabled = busy == null) {
                    Text(stringResource(if (pack.classId == null) R.string.pack_review_choose_class else R.string.pack_review_change_class), color = Accent)
                }
            }
        }
        when {
            busy is PackBusy.Working -> BusyLine(when (val p = busy.progress) {
                is PackProgress.Notes -> stringResource(R.string.pack_progress_notes, p.done, p.total)
                PackProgress.SavingNotes -> stringResource(R.string.pack_progress_saving)
                PackProgress.WritingAssignment -> stringResource(R.string.pack_progress_assignment)
                null -> stringResource(R.string.snapshot_summarizing_detail)
            })
            busy == PackBusy.Publishing -> BusyLine(stringResource(R.string.pack_review_publishing))
            status == NoteStatus.FAILED_PENDING_RETRY -> {
                Banner(Icons.Filled.WarningAmber, WarningAmber, failure ?: stringResource(R.string.pack_failed_detail))
                Button(onClick = actions.onResume) { Text(stringResource(R.string.notes_retry)) }
            }
        }
    }
}

@Composable
private fun NotesTab(
    pack: LessonPack,
    status: NoteStatus,
    busy: PackBusy?,
    markdown: String?,
    actions: PackActions,
    notes: @Composable (String) -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall)) {
        when {
            pack.notesShared -> Banner(Icons.Filled.CheckCircle, StatusPresent, stringResource(R.string.pack_notes_shared,
                pack.className, formatEpoch(pack.notesSharedAt ?: pack.sharedAt)))
            pack.classId != null && status == NoteStatus.COMPLETE && pack.remoteNoteId != null -> Row {
                Button(onClick = { confirm = true }, enabled = busy == null) {
                    Icon(Icons.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pack_notes_publish))
                }
            }
        }
        Spacer(Modifier.height(dimens.gutterSmall))
        if (markdown != null) notes(markdown)
        else Text(stringResource(R.string.notes_no_summary), color = TextOnSurfaceMuted, fontSize = dimens.bodySize)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.pack_notes_publish_title, pack.className)) },
            text = { Text(stringResource(R.string.pack_notes_publish_body)) },
            confirmButton = { TextButton(onClick = { confirm = false; actions.onPublishNotes() }) { Text(stringResource(R.string.pack_notes_publish)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.panel_cancel)) } },
        )
    }
}

/** Grouped by NCERT section, every question editable, nothing visible to the class until Publish. */
@Composable
private fun AssignmentTab(pack: LessonPack, status: NoteStatus, busy: PackBusy?, actions: PackActions) {
    val dimens = SmartBoardTheme.dimens
    var editing by remember { mutableStateOf<AssignmentQuestion?>(null) }
    var confirmPublish by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val locked = pack.assignmentShared || busy != null

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall)) {
        if (pack.assignmentShared) {
            Banner(Icons.Filled.CheckCircle, StatusPresent, stringResource(R.string.pack_review_shared, pack.className, formatEpoch(pack.sharedAt)))
        }

        val draft = pack.assignment
        if (draft == null) {
            Text(
                stringResource(if (pack.withAssignment) R.string.pack_assignment_none else R.string.pack_assignment_notes_only),
                fontSize = dimens.bodySize, color = TextOnSurfaceMuted,
            )
            if (pack.classId != null && busy == null && status != NoteStatus.FAILED_PENDING_RETRY) {
                OutlinedButton(onClick = actions.onRegenerate) { Text(stringResource(R.string.pack_review_write_assignment)) }
            }
            return@Column
        }

        Text(draft.title, fontSize = dimens.bodySize, fontWeight = FontWeight.SemiBold, color = TextOnSurface)
        Text(stringResource(R.string.pack_review_summary, draft.questions.size, draft.totalMarks),
            fontSize = dimens.labelSize, color = TextOnSurfaceMuted)
        if (draft.instructions.isNotBlank()) {
            Text(draft.instructions, fontSize = dimens.labelSize, color = TextOnSurfaceMuted)
        }

        // Due date.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Event, contentDescription = null, tint = TextOnSurfaceMuted, modifier = Modifier.size(dimens.iconSize))
            Spacer(Modifier.width(dimens.gutterSmall))
            Text(stringResource(R.string.pack_review_due, pack.dueDate?.let { formatDate(LocalDate.parse(it)) } ?: "-"),
                fontSize = dimens.bodySize, color = TextOnSurface)
            if (!locked) TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.pack_review_change), color = Accent) }
        }

        val flagged = draft.questions.count { it.problems.isNotEmpty() }
        if (flagged > 0 && !pack.assignmentShared) {
            Banner(Icons.Filled.WarningAmber, WarningAmber, stringResource(R.string.pack_review_flagged, flagged))
        }

        if (!pack.assignmentShared) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) {
                OutlinedButton(onClick = actions.onRegenerate, enabled = !locked) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pack_review_regenerate))
                }
                Button(onClick = { confirmPublish = true }, enabled = !locked && draft.questions.isNotEmpty()) {
                    Icon(Icons.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pack_review_publish))
                }
            }
        }

        NcertPattern.SECTIONS.forEach { section ->
            val qs = draft.questions.filter { it.section == section.letter }
            if (qs.isEmpty()) return@forEach
            Spacer(Modifier.height(dimens.gutterSmall))
            Text(stringResource(R.string.pack_review_section, section.letter, section.title, section.marks),
                fontSize = dimens.bodySize, fontWeight = FontWeight.SemiBold, color = Accent)
            qs.forEach { q ->
                QuestionCard(q, number = draft.questions.indexOf(q) + 1, locked = locked,
                    onEdit = { editing = q }, onDelete = { actions.onDelete(q.id) })
            }
        }
    }

    editing?.let { q ->
        QuestionEditor(q, onDismiss = { editing = null }, onSave = { actions.onEdit(it); editing = null })
    }
    if (confirmPublish) {
        AlertDialog(
            onDismissRequest = { confirmPublish = false },
            title = { Text(stringResource(R.string.pack_publish_title, pack.className)) },
            text = { Text(stringResource(R.string.pack_publish_body)) },
            confirmButton = { TextButton(onClick = { confirmPublish = false; actions.onPublishAssignment() }) { Text(stringResource(R.string.pack_review_publish)) } },
            dismissButton = { TextButton(onClick = { confirmPublish = false }) { Text(stringResource(R.string.panel_cancel)) } },
        )
    }
    if (pickDate) DuePicker(pack.dueDate, onDismiss = { pickDate = false }, onPick = { pickDate = false; actions.onDueDate(it) })
}

@Composable
private fun QuestionCard(q: AssignmentQuestion, number: Int, locked: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dimens = SmartBoardTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, if (q.problems.isNotEmpty()) WarningAmber else TextOnSurfaceMuted.copy(alpha = 0.25f), RoundedCornerShape(dimens.cornerRadius))
            .padding(dimens.gutterSmall),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text("$number. ${q.text}", fontSize = dimens.bodySize, color = TextOnSurface, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.pack_review_marks, q.marks), fontSize = dimens.labelSize, color = TextOnSurfaceMuted)
            if (!locked) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.pack_review_edit)) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.board_delete)) }
            }
        }
        q.options.forEachIndexed { i, o ->
            Text("(${'a' + i}) $o", fontSize = dimens.labelSize, color = TextOnSurface, modifier = Modifier.padding(start = dimens.gutter))
        }
        if (q.answer.isNotBlank()) {
            Text(stringResource(R.string.pack_review_answer, q.answer), fontSize = dimens.labelSize, color = TextOnSurfaceMuted,
                modifier = Modifier.padding(top = 4.dp))
        }
        q.problems.forEach { code ->
            Text(stringResource(problemText(code)), fontSize = dimens.labelSize, color = WarningAmber)
        }
    }
}

private fun problemText(code: String): Int = when (code) {
    NcertPattern.NO_ANSWER -> R.string.pack_problem_no_answer
    NcertPattern.MCQ_OPTIONS -> R.string.pack_problem_mcq_options
    NcertPattern.ANSWER_NOT_AN_OPTION -> R.string.pack_problem_answer_not_option
    NcertPattern.NO_BLANK -> R.string.pack_problem_no_blank
    else -> R.string.pack_problem_empty
}

@Composable
private fun QuestionEditor(q: AssignmentQuestion, onDismiss: () -> Unit, onSave: (AssignmentQuestion) -> Unit) {
    var text by remember { mutableStateOf(q.text) }
    var options by remember { mutableStateOf(q.options) }
    var answer by remember { mutableStateOf(q.answer) }
    var marks by remember { mutableStateOf(q.marks.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pack_review_edit)) },
        text = {
            Column(Modifier.widthIn(min = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.pack_edit_question)) }, modifier = Modifier.fillMaxWidth())
                options.forEachIndexed { i, o ->
                    OutlinedTextField(o, { v -> options = options.toMutableList().also { it[i] = v } },
                        label = { Text("(${'a' + i})") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(answer, { answer = it }, label = { Text(stringResource(R.string.pack_edit_answer)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(marks, { marks = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.pack_edit_marks)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(q.copy(text = text.trim(), options = options.map { it.trim() }, answer = answer.trim(),
                    marks = marks.toIntOrNull()?.coerceIn(1, 100) ?: q.marks))
            }) { Text(stringResource(R.string.panel_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.panel_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuePicker(current: String?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val initial = (current?.let(LocalDate::parse) ?: LocalDate.now().plusDays(7))
        .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text(stringResource(R.string.panel_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.panel_cancel)) } },
    ) { DatePicker(state = state) }
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, text: String) {
    val dimens = SmartBoardTheme.dimens
    Row(
        Modifier.fillMaxWidth().background(tint.copy(alpha = 0.08f), RoundedCornerShape(dimens.cornerRadius)).padding(dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(dimens.iconSize))
        Spacer(Modifier.width(dimens.gutterSmall))
        Text(text, fontSize = dimens.labelSize, color = TextOnSurface)
    }
}

@Composable
private fun BusyLine(text: String) {
    val dimens = SmartBoardTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(dimens.iconSize), strokeWidth = 2.dp)
        Spacer(Modifier.width(dimens.gutterSmall))
        Text(text, fontSize = dimens.bodySize, color = TextOnSurfaceMuted)
    }
}

private fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

private fun formatEpoch(ms: Long?): String =
    ms?.let { formatDate(Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()) }.orEmpty()
