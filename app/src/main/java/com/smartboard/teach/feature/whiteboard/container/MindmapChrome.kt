package com.smartboard.teach.feature.whiteboard.container

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.domain.model.ContainerKind
import com.smartboard.teach.feature.whiteboard.BoardState

/**
 * The four buttons around a selected mindmap node.
 *
 * Matching the reference panel: `+` on the right adds a child, `x` on the left
 * deletes the node and its branch, and the two triangles above and below add a
 * sibling on that side.
 *
 * Deliberately NOT a full-screen layer. A Box that fills the board and hosts a
 * pointerInput wins every hit-test above the canvas and makes the board
 * undrawable — only these four small buttons are placed, and everything
 * between them stays the canvas's.
 */
@Composable
fun MindmapChrome(
    state: BoardState,
    onAddChild: (Int) -> Unit,
    onAddSibling: (Int, Boolean) -> Unit,
    onDeleteNode: (Int) -> Unit,
) {
    val id = state.selectedContainerId ?: return
    val container = state.containerById(id) ?: return
    if (container.kind != ContainerKind.MINDMAP) return

    val index = state.selectedCellIndex
    val cell = container.cellAt(index) ?: return

    // Read so the buttons follow the node when the board is panned or the tree
    // reflows; both bump the selection version.
    @Suppress("UNUSED_EXPRESSION")
    state.selectionVersion

    val camera = state.camera
    val density = LocalDensity.current

    // Outline echoing the selected node, so it is obvious which one the
    // buttons act on when several sit close together.
    with(density) {
        val left = camera.worldToScreenX(cell.left)
        val top = camera.worldToScreenY(cell.top)
        val right = camera.worldToScreenX(cell.right)
        val bottom = camera.worldToScreenY(cell.bottom)

        Box(
            modifier = Modifier
                .offset(left.toDp() - 4.dp, top.toDp() - 4.dp)
                .size((right - left).toDp() + 8.dp, (bottom - top).toDp() + 8.dp)
                .border(1.dp, Accent.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
        )

        val midX = (left + right) / 2f
        val midY = (top + bottom) / 2f

        NodeButton(Icons.Filled.Add, "Add child", right.toDp(), midY.toDp()) {
            onAddChild(index)
        }
        // The root has no parent, so it can have no sibling; offering the
        // buttons anyway would give a control that silently does nothing.
        if (cell.col != MindmapLayout.ROOT_PARENT) {
            NodeButton(Icons.Filled.ArrowDropUp, "Add sibling above", midX.toDp(), top.toDp()) {
                onAddSibling(index, true)
            }
            NodeButton(Icons.Filled.ArrowDropDown, "Add sibling below", midX.toDp(), bottom.toDp()) {
                onAddSibling(index, false)
            }
        }
        NodeButton(Icons.Filled.Close, "Delete node", left.toDp(), midY.toDp()) {
            onDeleteNode(index)
        }
    }
}

/** One round button, centred on the given screen point. */
@Composable
private fun NodeButton(
    icon: ImageVector,
    label: String,
    centerX: androidx.compose.ui.unit.Dp,
    centerY: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .offset(centerX - BUTTON / 2, centerY - BUTTON / 2)
            .size(BUTTON)
            .clip(CircleShape)
            .background(Accent),
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/**
 * Smaller than the 56dp touch target used elsewhere.
 *
 * Four of these sit around a node barely larger than they are; at full size
 * they would cover the handwriting they are meant to sit beside. Still well
 * above the 24dp floor for a stylus-and-finger panel.
 */
private val BUTTON = 32.dp
