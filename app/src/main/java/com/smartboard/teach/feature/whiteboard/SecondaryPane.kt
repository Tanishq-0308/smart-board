package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.ChromeBorder
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import com.smartboard.teach.domain.model.BoardPage

/**
 * The right-hand pane of a split board.
 *
 * A full [BoardCanvas] with its OWN state and renderer, showing a DIFFERENT
 * page of the same lesson — so ink drawn here is real page content that saves
 * and reopens, not a scratch area that evaporates when the split closes.
 *
 * The pane carries only a page pager. Pen, colour and tool selection stay with
 * the single toolbar: a second full toolbar would double the chrome and leave
 * a teacher wondering which pen the one on the left was setting.
 */
@Composable
fun SecondaryPane(
    state: BoardState,
    renderer: BoardRenderer,
    onPersist: () -> Unit,
    pages: List<BoardPage>,
    currentPageId: String?,
    onSelectPage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = pages.indexOfFirst { it.id == currentPageId }

    Row(modifier = modifier.fillMaxHeight().fillMaxWidth(0.5f)) {
        // A hairline divider, so the two panes read as separate surfaces
        // rather than one board with a seam in the ink.
        Box(
            Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(ChromeBorder),
        )

        Box(Modifier.fillMaxHeight().weight(1f)) {
            BoardCanvas(
                state = state,
                renderer = renderer,
                onSized = { w, h ->
                    state.viewportWidth = w.toFloat()
                    state.viewportHeight = h.toFloat()
                    renderer.ensureSurface(w, h)
                    renderer.rebuildCache(
                        state.strokes,
                        state.camera,
                        state.containers,
                        state.mediaBitmaps,
                    )
                    state.markCommittedDirty()
                },
                onStrokeFinished = { drawn ->
                    state.strokes.add(drawn)
                    state.history.record(BoardCommand.AddStroke(drawn))
                    state.refreshHistoryFlags()
                    renderer.rebuildCache(
                        state.strokes,
                        state.camera,
                        state.containers,
                        state.mediaBitmaps,
                    )
                    state.markCommittedDirty()
                    onPersist()
                },
                onStrokesErased = { erased ->
                    state.history.record(BoardCommand.EraseStrokes(erased))
                    state.refreshHistoryFlags()
                    renderer.rebuildCache(
                        state.strokes,
                        state.camera,
                        state.containers,
                        state.mediaBitmaps,
                    )
                    state.markCommittedDirty()
                    onPersist()
                },
                onSelectionMoved = { before, boxes ->
                    state.history.record(
                        BoardCommand.TransformSelection(
                            strokesBefore = before,
                            strokesAfter = state.selectedStrokes(),
                            boxesBefore = boxes,
                            boxesAfter = state.selectedTextBoxes(),
                        ),
                    )
                    state.refreshHistoryFlags()
                    onPersist()
                },
                onCameraSettled = {
                    renderer.rebuildCache(
                        state.strokes,
                        state.camera,
                        state.containers,
                        state.mediaBitmaps,
                    )
                    state.markCommittedDirty()
                    onPersist()
                },
            )

            // Its own pager, so each pane shows whichever page the teacher
            // wants — the whole point of a split.
            FloatingIsland(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(
                        onClick = { pages.getOrNull(index - 1)?.let { onSelectPage(it.id) } },
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous page",
                            tint = if (index > 0) TextOnChrome else TextOnChromeMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Text(
                        text = "%02d/%02d".format((index + 1).coerceAtLeast(1), pages.size),
                        color = TextOnChrome,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )

                    IconButton(
                        onClick = { pages.getOrNull(index + 1)?.let { onSelectPage(it.id) } },
                        enabled = index in 0 until pages.size - 1,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next page",
                            tint = if (index in 0 until pages.size - 1) {
                                TextOnChrome
                            } else {
                                TextOnChromeMuted
                            },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Loads a page snapshot into a pane's state and renderer. */
fun applyToPane(state: BoardState, renderer: BoardRenderer, snapshot: PageContentSnapshot) {
    applyLoadedPage(
        state,
        renderer,
        snapshot.strokes,
        snapshot.textBoxes,
        snapshot.background,
        snapshot.containers,
    )
    snapshot.camera?.let { state.camera.restore(it.offsetX, it.offsetY, it.zoom) }
    state.canvasStyle = snapshot.canvasStyle
    renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
    state.markCommittedDirty()
}
