package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.smartboard.teach.core.ui.component.FloatingIsland
import androidx.compose.ui.graphics.Color
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.ChromeBorder
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import com.smartboard.teach.domain.model.BoardPage

/**
 * Page navigation, bottom-right, modelled on the reference panel's
 * `＋ ‹ 01/01 › ☰` bar.
 *
 * A prev/next pager with a counter rather than one card per page: a card list
 * grows without bound across a term of lesson pages and pushes the bar across
 * the board, while a counter stays the same width at page 1 or page 40.
 *
 * The navigation menu lives here too. Every control a teacher touches sits
 * along the bottom edge of the panel, within reach of someone standing at it.
 */
@Composable
fun PageStrip(
    pages: List<BoardPage>,
    currentPageId: String?,
    onSelectPage: (String) -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMenu: () -> Unit = {},
    isSplit: Boolean = false,
    onToggleSplit: () -> Unit = {},
) {
    val dimens = SmartBoardTheme.dimens
    val index = pages.indexOfFirst { it.id == currentPageId }
    val current = if (index >= 0) index + 1 else 1

    FloatingIsland(modifier = modifier, contentPadding = PaddingValues(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Split sits LEFT of add page, where the reference panel puts it.
            StripAction(
                icon = Icons.Filled.VerticalSplit,
                label = if (isSplit) "Close split view" else "Split view",
                selected = isSplit,
                onClick = onToggleSplit,
            )

            StripAction(Icons.Filled.Add, "Add page", onClick = onAddPage)

            StripAction(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                label = "Previous page",
                enabled = index > 0,
                onClick = { pages.getOrNull(index - 1)?.let { onSelectPage(it.id) } },
            )

            // Zero-padded like the reference, and monospaced so the bar does
            // not twitch as the count crosses 9 or 99.
            Text(
                text = "%02d/%02d".format(current, pages.size),
                color = TextOnChrome,
                fontSize = dimens.bodySize,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 6.dp),
            )

            StripAction(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                label = "Next page",
                enabled = index in 0 until pages.size - 1,
                onClick = { pages.getOrNull(index + 1)?.let { onSelectPage(it.id) } },
            )

            StripAction(
                icon = Icons.Filled.DeleteOutline,
                label = "Delete page",
                // The board must never end up with zero pages.
                enabled = pages.size > 1,
                onClick = onDeletePage,
            )

            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = 1.dp, height = dimens.chromeButton * 0.55f)
                    .background(ChromeBorder),
            )

            StripAction(Icons.Filled.Menu, "Menu", onClick = onOpenMenu)
        }
    }
}

@Composable
private fun StripAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .size(dimens.chromeButton)
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .background(if (selected) Accent else Color.Transparent)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color.White else TextOnChromeMuted,
            modifier = Modifier.size(dimens.chromeIcon),
        )
    }
}
