package com.smartboard.teach.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.ChromeDark
import com.smartboard.teach.core.ui.theme.ChromeDarkElevated
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import com.smartboard.teach.domain.model.AuthState

/**
 * Left navigation rail.
 *
 * Auth-gated items are shown DIMMED WITH A LOCK rather than hidden. A teacher
 * who has not signed in should still discover that attendance and class
 * material exist here — hiding them makes the app look like it lacks the
 * feature. Tapping a locked item routes to Login.
 */
@Composable
fun Sidebar(
    authState: AuthState,
    currentRoute: String?,
    onNavigate: (Dest) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens
    val isAuthed = authState is AuthState.Authenticated
    val current = Dest.fromRoute(currentRoute)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(dimens.sidebarWidth)
            .background(ChromeDark)
            .padding(vertical = dimens.gutterSmall),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Dest.sidebarItems.forEach { dest ->
                val locked = dest.requiresAuth && !isAuthed
                SidebarItem(
                    label = dest.label,
                    icon = dest.icon,
                    selected = current == dest,
                    locked = locked,
                    onClick = { onNavigate(if (locked) Dest.Login else dest) },
                )
            }
        }

        Spacer(Modifier.height(dimens.gutterSmall))
        HairlineDivider()
        Spacer(Modifier.height(dimens.gutterSmall))

        SidebarItem(
            label = Dest.Settings.label,
            icon = Icons.Filled.Settings,
            selected = current == Dest.Settings,
            locked = false,
            onClick = { onNavigate(Dest.Settings) },
        )

        when (authState) {
            is AuthState.Authenticated -> {
                AccountBlock(name = authState.teacher.displayName, subtitle = "Signed in")
                SidebarItem(
                    label = "Sign Out",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    selected = false,
                    locked = false,
                    onClick = onLogout,
                )
            }

            else -> {
                AccountBlock(name = "Guest", subtitle = "Board & notes available")
                SidebarItem(
                    label = "Sign In",
                    icon = Icons.AutoMirrored.Filled.Login,
                    selected = current == Dest.Login,
                    locked = false,
                    onClick = { onNavigate(Dest.Login) },
                )
            }
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    val contentColor = when {
        selected -> TextOnChrome
        locked -> TextOnChromeMuted.copy(alpha = 0.55f)
        else -> TextOnChromeMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutterSmall, vertical = 2.dp)
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .background(if (selected) Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .height(dimens.touchTarget)
            .padding(horizontal = dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(dimens.iconSize),
        )
        Spacer(Modifier.width(dimens.gutterSmall))
        Text(
            text = label,
            color = contentColor,
            fontSize = dimens.bodySize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (locked) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Sign in to use this",
                tint = contentColor,
                modifier = Modifier.size(dimens.iconSize * 0.7f),
            )
        }
    }
}

@Composable
private fun AccountBlock(name: String, subtitle: String) {
    val dimens = SmartBoardTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = name,
            color = TextOnChrome,
            fontSize = dimens.bodySize,
            fontWeight = FontWeight.Medium,
        )
        Text(text = subtitle, color = TextOnChromeMuted, fontSize = dimens.labelSize)
    }
}

@Composable
private fun HairlineDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SmartBoardTheme.dimens.gutterSmall)
            .height(1.dp)
            .background(ChromeDarkElevated),
    )
}
