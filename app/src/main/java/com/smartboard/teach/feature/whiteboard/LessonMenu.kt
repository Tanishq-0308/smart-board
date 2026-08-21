package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import com.smartboard.teach.domain.model.Lesson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which face the menu is showing. */
private enum class MenuView { ACTIONS, OPEN, NAME }

/**
 * New / Open / Save / Save as, matching the reference panel's file column.
 *
 * A saved lesson is a NAMED SESSION, not an exported file: the board already
 * stores every page, stroke and object against a session id, so naming one
 * exposes what exists rather than inventing a file format that would need its
 * own writer, reader and migration story — and whose corruption would lose a
 * lesson outright.
 *
 * Cloud upload, Scan and Email are in the reference but need an account and a
 * backend decided first; they are deliberately absent rather than stubbed.
 */
@Composable
fun LessonMenu(
    currentLesson: Lesson?,
    lessons: List<Lesson>,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onSave: (String) -> Unit,
    onSaveAs: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var view by remember { mutableStateOf(MenuView.ACTIONS) }
    var draftName by remember { mutableStateOf("") }
    // True when the name being typed is for Save as rather than a first Save.
    var namingAsCopy by remember { mutableStateOf(false) }

    FloatingIsland(
        modifier = modifier.width(320.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (view) {
                        MenuView.ACTIONS -> currentLesson?.name ?: "Unsaved lesson"
                        MenuView.OPEN -> "Open lesson"
                        MenuView.NAME -> if (namingAsCopy) "Save a copy as" else "Save lesson as"
                    },
                    color = TextOnChrome,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (view == MenuView.ACTIONS) onClose() else view = MenuView.ACTIONS
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = if (view == MenuView.ACTIONS) "Close" else "Back",
                        tint = TextOnChrome,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            when (view) {
                MenuView.ACTIONS -> {
                    MenuRow(Icons.Filled.NoteAdd, "New") { onNew(); onClose() }
                    MenuRow(Icons.Filled.FolderOpen, "Open") { view = MenuView.OPEN }
                    MenuRow(Icons.Filled.Save, "Save") {
                        val existing = currentLesson
                        if (existing == null) {
                            // Never saved: Save must ask for a name rather than
                            // inventing one the teacher cannot find later.
                            namingAsCopy = false
                            draftName = ""
                            view = MenuView.NAME
                        } else {
                            onSave(existing.name)
                            onClose()
                        }
                    }
                    MenuRow(Icons.Filled.SaveAs, "Save as") {
                        namingAsCopy = currentLesson != null
                        draftName = currentLesson?.let { "${it.name} copy" }.orEmpty()
                        view = MenuView.NAME
                    }
                }

                MenuView.NAME -> {
                    NameField(
                        value = draftName,
                        onValueChange = { draftName = it },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("Cancel", filled = false) { view = MenuView.ACTIONS }
                        ActionButton("Save", filled = draftName.isNotBlank()) {
                            if (draftName.isBlank()) return@ActionButton
                            if (namingAsCopy) onSaveAs(draftName) else onSave(draftName)
                            onClose()
                        }
                    }
                }

                MenuView.OPEN -> {
                    if (lessons.isEmpty()) {
                        Text(
                            "No saved lessons yet.",
                            color = TextOnChromeMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        // Lazy and height-capped: a term of lessons must not
                        // grow the panel past the board.
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(lessons, key = { it.sessionId }) { lesson ->
                                LessonRow(
                                    lesson = lesson,
                                    isCurrent = lesson.sessionId == currentLesson?.sessionId,
                                    onOpen = { onOpen(lesson.sessionId); onClose() },
                                    onDelete = { onDelete(lesson.sessionId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TextOnChrome, modifier = Modifier.size(18.dp))
        Text(label, color = TextOnChrome, fontSize = 14.sp)
    }
}

@Composable
private fun LessonRow(
    lesson: Lesson,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) CURRENT_ROW else Color.Transparent)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            tint = if (isCurrent) Accent else TextOnChromeMuted,
            modifier = Modifier.size(16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(lesson.name, color = TextOnChrome, fontSize = 13.sp)
            Text(
                text = "${lesson.pageCount} page${if (lesson.pageCount == 1) "" else "s"} · " +
                    DATE_FORMAT.format(Date(lesson.updatedAt)),
                color = TextOnChromeMuted,
                fontSize = 11.sp,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "Delete ${lesson.name}",
                tint = TextOnChromeMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(FIELD_BACKGROUND, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
            Text("Lesson name", color = TextOnChromeMuted, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = TextOnChrome, fontSize = 14.sp),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActionButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (filled) Color.White else TextOnChrome,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled) Accent else FIELD_BACKGROUND)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
    )
}

private val FIELD_BACKGROUND = Color(0x22FFFFFF)
private val CURRENT_ROW = Color(0x222F6FED)
private val DATE_FORMAT = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
