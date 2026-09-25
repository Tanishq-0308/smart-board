package com.smartboard.teach.feature.whiteboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.IslandSurface
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted

/**
 * The board's research tools, behind a handle on the RIGHT edge.
 *
 * Mirrors the navigation drawer on the left: same handle, same slide, same
 * scrim. Web search and Snapshot moved out of the insert tray because neither
 * one puts an object on the board — the tray is for things a teacher drops
 * onto the canvas, while these two go and fetch something from outside it.
 *
 * They sit on the right because that is the side the web pane already docks
 * to, so the panel opens next to where its result appears.
 */
@Composable
fun ToolsDrawer(
    isOpen: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onWebSearch: () -> Unit,
    onSnapshot: () -> Unit,
    onMaths3D: () -> Unit,
    modifier: Modifier = Modifier,
    webEnabled: Boolean = true,
    snapshotEnabled: Boolean = true,
) {
    val dimens = SmartBoardTheme.dimens

    Box(modifier.fillMaxSize()) {
        // Scrim, which also swallows canvas input while the panel is open so a
        // teacher who misses the panel does not leave a stray mark behind it.
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(tween(200)) { it } + fadeIn(tween(120)),
            exit = slideOutHorizontally(tween(180)) { it } + fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            FloatingIsland(
                modifier = Modifier.padding(dimens.gutterSmall),
                contentPadding = PaddingValues(dimens.gutterSmall),
            ) {
                // Stacked rather than side by side: the drawer comes off a
                // vertical edge, so a column keeps it narrow and leaves the
                // board beside it visible.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToolButton(Icons.Filled.Public, "Web", webEnabled) {
                        onDismiss()
                        onWebSearch()
                    }
                    ToolButton(Icons.Filled.PhotoCamera, "Snapshot", snapshotEnabled) {
                        onDismiss()
                        onSnapshot()
                    }
                    ToolButton(Icons.Filled.ViewInAr, "3D Maths", true) {
                        onDismiss()
                        onMaths3D()
                    }
                }
            }
        }

        // Always reachable, and hidden only while the panel itself is open.
        if (!isOpen) {
            ToolsHandle(
                onOpen = onOpen,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** Icon over label, matching the insert tray's cells so the two read alike. */
@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Column(
        modifier = Modifier
            .width(dimens.chromeButton * 1.9f)
            .clip(RoundedCornerShape(dimens.cornerRadius * 0.6f))
            .alpha(if (enabled) 1f else 0.3f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) TextOnChrome else TextOnChromeMuted,
            modifier = Modifier.size(dimens.chromeIcon),
        )
        Text(
            text = label,
            color = if (enabled) TextOnChrome else TextOnChromeMuted,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

/**
 * The grab handle on the right edge, mirroring the navigation one on the left.
 *
 * Opens on a tap or a deliberate LEFTWARD pull — the direction the panel
 * actually travels — so a stray brush along the edge does not throw it open
 * mid-sentence.
 */
@Composable
private fun ToolsHandle(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = 26.dp, height = 64.dp)
            .clip(RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp))
            .background(IslandSurface)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { },
                onDragStopped = { velocity -> if (velocity < -120f) onOpen() },
            )
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Open tools",
            tint = TextOnChrome,
            modifier = Modifier.size(20.dp),
        )
    }
}
