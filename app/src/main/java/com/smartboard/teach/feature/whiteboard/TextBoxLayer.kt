package com.smartboard.teach.feature.whiteboard

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                                    state.editingTextBoxId = box.id
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
            // Keyed by id, not list position. Without this a box edited into a
            // new instance is torn down and rebuilt, which disposes the open
            // editor and commits it the instant editing starts.
            key(box.id) {
                TextBoxItem(
                    box = box,
                    camera = state.camera,
                    isEditing = state.editingTextBoxId == box.id,
                    onStartEdit = { if (!isPlacementMode) state.editingTextBoxId = box.id },
                    onTextChanged = { newText ->
                        val index = state.textBoxes.indexOfFirst { it.id == box.id }
                        if (index >= 0) state.textBoxes[index] = box.copy(text = newText)
                    },
                    onCommit = { before ->
                        val after = state.textBoxes.firstOrNull { it.id == box.id }
                        when {
                            after == null -> Unit

                            // Emptying a box deletes it — that IS the delete
                            // gesture, so there is no trash button to aim at.
                            after.text.isBlank() -> {
                                state.textBoxes.remove(after)
                                state.history.record(BoardCommand.DeleteTextBox(after))
                                state.refreshHistoryFlags()
                            }

                            after.text != before.text -> {
                                state.history.record(BoardCommand.EditTextBox(before, after))
                                state.refreshHistoryFlags()
                            }
                        }
                        onChanged()
                    },
                    onClose = {
                        if (state.editingTextBoxId == box.id) state.editingTextBoxId = null
                    },
                )
            }
        }
    }
}

/**
 * One text box, editing in place.
 *
 * The editor is deliberately invisible — no card, no border, no buttons. It is
 * styled identically to the committed text and sits at the same offset, so
 * entering and leaving edit mode does not move or restyle a single glyph; the
 * only thing that appears is a caret. A bordered white field floating over a
 * whiteboard reads as a form, not as writing on the board.
 *
 * Width is a wrap point, not a frame: the box is as wide as its longest line
 * up to [TextBox.widthPx], so short text does not sit in a wide empty strip.
 */
@Composable
private fun TextBoxItem(
    box: TextBox,
    camera: Camera,
    isEditing: Boolean,
    onStartEdit: () -> Unit,
    onTextChanged: (String) -> Unit,
    onCommit: (TextBox) -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    // Captured when editing starts so the undo command has a real "before".
    val snapshotOnEdit = remember(isEditing) { box }

    // World -> screen, so a text box pans and zooms with the ink it annotates.
    val xDp = with(density) { camera.worldToScreenX(box.x).toDp() }
    val yDp = with(density) { camera.worldToScreenY(box.y).toDp() }
    val maxWidthDp = with(density) { (box.widthPx * camera.zoom).toDp() }
    val fontSize = box.fontSizeSp * camera.zoom

    // Identical for the editor and the committed text: any difference here
    // shows up as the text shifting the moment editing starts or ends.
    val textStyle = TextStyle(
        color = Color(box.colorArgb),
        fontSize = fontSize.sp,
    )

    // Commit when the editor CLOSES, whoever closed it — clicking away, the
    // canvas taking a press, Escape, or the box being deleted. Hanging it off
    // focus loss alone missed the canvas case, because the canvas consumes the
    // press before the field is ever told it lost focus.
    val commit by rememberUpdatedState { onCommit(snapshotOnEdit) }
    if (isEditing) {
        DisposableEffect(box.id) {
            focusRequester.requestFocus()
            onDispose { commit() }
        }
    }

    Box(Modifier.offset(x = xDp, y = yDp)) {
        if (isEditing) {
            BasicTextField(
                value = box.text,
                onValueChange = onTextChanged,
                textStyle = textStyle,
                cursorBrush = SolidColor(Color(box.colorArgb)),
                modifier = Modifier
                    // widthIn, not width: the field is as wide as the text and
                    // wraps at the box width, rather than always occupying it.
                    .widthIn(max = maxWidthDp)
                    .focusRequester(focusRequester)
                    // NOTE: deliberately no onFocusChanged listener. It fires
                    // once with isFocused=false before requestFocus lands,
                    // which closed the editor the instant it opened. Tapping
                    // away is handled by the canvas, which owns that press.
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                            onClose()
                            true
                        } else {
                            false
                        }
                    },
            )
        } else if (box.text.isNotBlank()) {
            Text(
                text = box.text,
                style = textStyle,
                modifier = Modifier
                    .widthIn(max = maxWidthDp)
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
