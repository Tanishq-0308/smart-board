package com.smartboard.teach.feature.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import com.smartboard.teach.domain.model.AuthState

/**
 * Navigation as a temporary OVERLAY rather than a permanent column.
 *
 * The old fixed sidebar cost ~354dp of width at the board's 1.70x scale and
 * never gave it back. Here it slides over the canvas on demand and dismisses
 * on a scrim tap, so the whiteboard opens at full width and stays there for
 * the whole lesson unless the teacher deliberately navigates.
 */
@Composable
fun SidebarOverlay(
    isOpen: Boolean,
    authState: AuthState,
    currentRoute: String?,
    onNavigate: (Dest) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens

    Box(modifier.fillMaxSize()) {
        // Scrim. Also swallows canvas input while the panel is open, so a
        // teacher reaching for a menu item never leaves a stray mark behind
        // it if they miss.
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
            enter = slideInHorizontally(tween(200)) { -it } + fadeIn(tween(120)),
            exit = slideOutHorizontally(tween(180)) { -it } + fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Box(
                Modifier
                    .padding(dimens.gutterSmall)
                    .fillMaxHeight()
                    .shadow(16.dp, RoundedCornerShape(dimens.cornerRadius), clip = false)
                    .clip(RoundedCornerShape(dimens.cornerRadius)),
            ) {
                Sidebar(
                    authState = authState,
                    currentRoute = currentRoute,
                    onNavigate = { dest ->
                        onNavigate(dest)
                        onDismiss()
                    },
                    onLogout = {
                        onLogout()
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** Hamburger that opens the navigation overlay. */
@Composable
fun MenuButton(
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens
    FloatingIsland(modifier = modifier) {
        Box(
            Modifier
                .size(dimens.touchTarget)
                .clip(RoundedCornerShape(dimens.cornerRadius))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isOpen) Icons.Filled.Close else Icons.Filled.Menu,
                contentDescription = if (isOpen) "Close menu" else "Open menu",
                tint = TextOnChromeMuted,
                modifier = Modifier.size(dimens.iconSize),
            )
        }
    }
}
