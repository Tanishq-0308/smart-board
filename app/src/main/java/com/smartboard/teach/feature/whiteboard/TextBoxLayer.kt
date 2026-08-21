package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.domain.model.TextBox
import java.util.UUID

/**
 * Text boxes rendered above the ink canvas.
 *
 * These are NOT strokes. Rasterizing text into the committed bitmap would make
 * it permanently uneditable, so each box stays a real composable positioned
 * absolutely over the board. They still participate in the same undo/redo
 * command stack as ink, so undo behaves consistently for a teacher who does
 * not care which kind of object they just added.
 */
@Composable
fun TextBoxLayer(
    state: BoardState,
    isPlacementMode: Boolean,
    onPlacementConsumed: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var editingId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Text boxes are world-positioned composables, so one panned off
            // the edge must be clipped rather than drawn over the sidebar.
            .clipToBounds()
            .then(
                if (isPlacementMode) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed && !change.isConsumed) {
                                    change.consume()
                                    val box = TextBox(
                                        id = UUID.randomUUID().toString(),
                                        // Stored in WORLD coords like every
                                        // other object on the infinite canvas.
                                        x = state.camera.screenToWorldX(change.position.x),
                                        y = state.camera.screenToWorldY(change.position.y),
                                        widthPx = with(density) { 320.dp.toPx() } /
                                            state.camera.zoom,
                                        text = "",
                                        colorArgb = state.penColor.toArgb(),
                                        fontSizeSp = DEFAULT_FONT_SIZE_SP,
                                    )
                                    state.textBoxes.add(box)
                                    state.history.record(BoardCommand.AddTextBox(box))
                                    state.refreshHistoryFlags()
                                    editingId = box.id
                                    onPlacementConsumed()
                                    onChanged()
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        state.textBoxes.forEach { box ->
            TextBoxItem(
                box = box,
                camera = state.camera,
                isEditing = editingId == box.id,
                onStartEdit = { if (!isPlacementMode) editingId = box.id },
                onTextChanged = { newText ->
                    val index = state.textBoxes.indexOfFirst { it.id == box.id }
                    if (index >= 0) state.textBoxes[index] = box.copy(text = newText)
                },
                onCommit = { before ->
                    editingId = null
                    val after = state.textBoxes.firstOrNull { it.id == box.id }
                    if (after != null && after.text != before.text) {
                        state.history.record(BoardCommand.EditTextBox(before, after))
                        state.refreshHistoryFlags()
                    }
                    // An empty box left behind is clutter, not content.
                    if (after != null && after.text.isBlank()) {
                        state.textBoxes.remove(after)
                    }
                    onChanged()
                },
                onDelete = {
                    state.textBoxes.remove(box)
                    state.history.record(BoardCommand.DeleteTextBox(box))
                    state.refreshHistoryFlags()
                    editingId = null
                    onChanged()
                },
            )
        }
    }
}

@Composable
private fun TextBoxItem(
    box: TextBox,
    camera: Camera,
    isEditing: Boolean,
    onStartEdit: () -> Unit,
    onTextChanged: (String) -> Unit,
    onCommit: (TextBox) -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val dimens = SmartBoardTheme.dimens
    val focusRequester = remember { FocusRequester() }
    // Captured when editing starts so the undo command has a real "before".
    val snapshotOnEdit = remember(isEditing) { box }

    // World -> screen, so a text box pans and zooms with the ink it annotates.
    val xDp = with(density) { camera.worldToScreenX(box.x).toDp() }
    val yDp = with(density) { camera.worldToScreenY(box.y).toDp() }
    val widthDp = with(density) { (box.widthPx * camera.zoom).toDp() }
    val fontSize = box.fontSizeSp * camera.zoom

    LaunchedEffect(isEditing) {
        if (isEditing) focusRequester.requestFocus()
    }

    Box(Modifier.offset(x = xDp, y = yDp)) {
        if (isEditing) {
            Box(
                Modifier
                    .width(widthDp)
                    .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                    .border(2.dp, Accent, RoundedCornerShape(6.dp))
                    .padding(8.dp),
            ) {
                BasicTextField(
                    value = box.text,
                    onValueChange = onTextChanged,
                    textStyle = TextStyle(
                        color = Color(box.colorArgb),
                        fontSize = fontSize.sp,
                    ),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester),
                )
            }

            // Confirm / delete sit just below the box so they never cover the
            // text being typed.
            Row(
                Modifier.offset(y = widthDp * 0f + 4.dp).padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onCommit(snapshotOnEdit) }) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Done editing",
                        tint = Accent,
                        modifier = Modifier.size(dimens.iconSize),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Delete text box",
                        tint = Color(0xFFC8382F),
                        modifier = Modifier.size(dimens.iconSize),
                    )
                }
            }
        } else if (box.text.isNotBlank()) {
            Text(
                text = box.text,
                color = Color(box.colorArgb),
                fontSize = fontSize.sp,
                modifier = Modifier
                    .width(widthDp)
                    .pointerInput(box.id) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed && !change.isConsumed) {
                                    change.consume()
                                    onStartEdit()
                                }
                            }
                        }
                    },
            )
        }
    }
}

private const val DEFAULT_FONT_SIZE_SP = 28f
