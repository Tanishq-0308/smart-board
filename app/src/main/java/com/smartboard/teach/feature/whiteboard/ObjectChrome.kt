package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.IslandSurface
import com.smartboard.teach.domain.model.ContainerKind
import com.smartboard.teach.feature.whiteboard.container.TableGrid

/**
 * Move / duplicate / delete for whatever is selected, floating above it.
 *
 * Anything on the board can be grabbed this way — a table, an image, a
 * mindmap, a handful of strokes — so there is one place to look rather than a
 * different gesture per kind of object.
 *
 * Move is a DRAG HANDLE rather than a mode toggle: pressing the object itself
 * writes on it while the pen is out, so the handle gives a spot that always
 * means "move me" without taking a mode the teacher then has to leave.
 *
 * Deliberately NOT a full-screen layer — a Box filling the board would win
 * every hit-test above the canvas and make it undrawable. Only these few small
 * buttons are placed; everything between them stays the canvas's.
 */
@Composable
fun ObjectChrome(
    state: BoardState,
    onMoveStart: () -> Unit,
    onMove: (worldDx: Float, worldDy: Float) -> Unit,
    onMoveFinished: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onInsertRow: (at: Int) -> Unit,
    onInsertColumn: (at: Int) -> Unit,
) {
    if (!state.hasSelection) return

    // Read so the chrome follows the object when the board is panned, zoomed
    // or the selection moves; all of them bump the selection version.
    @Suppress("UNUSED_EXPRESSION")
    state.selectionVersion

    val bounds = state.selectionBounds()
    if (Selection.isEmpty(bounds)) return

    // A mindmap has its own node buttons, which would collide with these.
    val selected = state.selectedContainerId?.let(state::containerById)
    if (selected?.kind == ContainerKind.MINDMAP) return

    val camera = state.camera
    val density = LocalDensity.current

    with(density) {
        val left = camera.worldToScreenX(bounds[0])
        val top = camera.worldToScreenY(bounds[1])
        val right = camera.worldToScreenX(bounds[2])
        val bottom = camera.worldToScreenY(bounds[3])
        val midX = (left + right) / 2f
        val midY = (top + bottom) / 2f

        // A table gains a + on each edge, inserting a row or column THERE, so
        // the grid grows in the direction the teacher points at.
        if (selected?.kind == ContainerKind.TABLE) {
            EdgeButton(stringResource(R.string.board_add_row_above), midX.toDp(), top.toDp()) { onInsertRow(0) }
            EdgeButton(stringResource(R.string.board_add_row_below), midX.toDp(), bottom.toDp()) { onInsertRow(selected.rows) }
            EdgeButton(stringResource(R.string.board_add_column_left), left.toDp(), midY.toDp()) { onInsertColumn(0) }
            EdgeButton(stringResource(R.string.board_add_column_right), right.toDp(), midY.toDp()) {
                onInsertColumn(selected.cols)
            }
        }

        // The action bar sits ABOVE the object, clear of the edge buttons and
        // of the bottom toolbar a selection near the foot of the board would
        // otherwise hide behind.
        Box(
            modifier = Modifier
                .offset(x = midX.toDp() - BAR_WIDTH / 2, y = top.toDp() - BAR_OFFSET)
                .clip(RoundedCornerShape(10.dp))
                .background(IslandSurface)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Press and drag this to move. The world delta is derived from
                // the screen drag through the camera, so a move tracks the
                // finger at every zoom level rather than drifting when the
                // board is scaled.
                Box(
                    modifier = Modifier
                        .size(BUTTON)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onMoveStart() },
                                onDrag = { change, drag ->
                                    change.consume()
                                    onMove(drag.x / camera.zoom, drag.y / camera.zoom)
                                },
                                onDragEnd = onMoveFinished,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.OpenWith,
                        contentDescription = stringResource(R.string.board_move),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }

                BarButton(Icons.Filled.ContentCopy, stringResource(R.string.board_duplicate), onDuplicate)
                BarButton(Icons.Filled.DeleteOutline, stringResource(R.string.board_delete), onDelete)
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(BUTTON)) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** One round + on a table's edge, centred on the given screen point. */
@Composable
private fun EdgeButton(label: String, centerX: Dp, centerY: Dp, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .offset(centerX - EDGE_BUTTON / 2, centerY - EDGE_BUTTON / 2)
            .size(EDGE_BUTTON)
            .clip(CircleShape)
            .background(Accent),
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}

private val BUTTON = 36.dp
private val EDGE_BUTTON = 28.dp

/** Wide enough for the three actions, used to centre the bar on the object. */
private val BAR_WIDTH = 120.dp

/** Clears the object and its top edge buttons. */
private val BAR_OFFSET = 52.dp
