package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.smartboard.teach.core.ui.theme.ChromeDark
import com.smartboard.teach.core.ui.theme.IslandSurface
import com.smartboard.teach.feature.shell.LocalBoardMenu
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.component.BoardSplitIcon
import com.smartboard.teach.core.ui.component.FloatingIsland
import androidx.compose.ui.graphics.Color
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import com.smartboard.teach.domain.model.BoardPage

/**
 * Most panes the board will show at once, primary included.
 *
 * The split menu offers 2, 3 or 4 panes; past four a landscape tablet leaves
 * each pane too narrow to write on.
 */
const val MAX_PANES = 4

/**
 * How many EXTRA panes a lesson of [pageCount] pages can show.
 *
 * Every pane needs a page of its own — a split showing the same page twice is
 * the thing the split exists to avoid — so deleting pages under an open split
 * has to close the panes that no longer have one. Pulled out so that rule is
 * testable on its own rather than only observable by deleting pages by hand.
 */
fun panesForPages(pageCount: Int, maxPanes: Int = MAX_PANES): Int =
    (pageCount - 1).coerceIn(0, maxPanes - 1)

/**
 * Page navigation, bottom-right, modelled on the reference panel's
 * `☰ ‹ 01/01 › ＋ ⧉` bar, mirrored when it sits bottom-right.
 *
 * A prev/next pager with a counter rather than one card per page: a card list
 * grows without bound across a term of lesson pages and pushes the bar across
 * the board, while a counter stays the same width at page 1 or page 40.
 *
 * The navigation menu (☰) lives here too. Every control a teacher touches sits
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
    paneCount: Int = 1,
    maxPanes: Int = MAX_PANES,
    onSetPaneCount: (Int) -> Unit = {},
    onCloseSplit: () -> Unit = {},
    /**
     * Which bottom corner the strip sits in. On the right the order runs
     * backwards, so ☰ is always at the screen edge and split innermost.
     */
    onRight: Boolean = true,
) {
    val dimens = SmartBoardTheme.dimens
    val index = pages.indexOfFirst { it.id == currentPageId }
    val current = if (index >= 0) index + 1 else 1
    val isSplit = paneCount > 1
    var splitMenuOpen by remember { mutableStateOf(false) }

    // ☰ first, as on the reference panel. It pops the navigation rail's items
    // up from here rather than sliding the drawer in from the far edge, away
    // from where the teacher is standing.
    var boardMenuOpen by remember { mutableStateOf(false) }
    var menuTopPx by remember { mutableStateOf(0f) }
    val menuButton: @Composable () -> Unit = {
        Box(Modifier.onGloballyPositioned { menuTopPx = it.positionInWindow().y }) {
            StripAction(
                icon = Icons.Filled.Menu,
                label = stringResource(R.string.shell_open_menu),
                selected = boardMenuOpen,
                onClick = { boardMenuOpen = true },
            )
            if (boardMenuOpen) {
                val menu = LocalBoardMenu.current
                // Capped to the room above the strip; the rail scrolls
                // inside that on a short screen.
                val room = with(LocalDensity.current) { menuTopPx.toDp() } - 48.dp
                // Hangs inward from the screen edge the button sits at.
                PopupAbove(atStart = !onRight, caretColor = ChromeDark, onDismiss = { boardMenuOpen = false }) {
                    Box(
                        Modifier
                            .heightIn(max = room)
                            .shadow(10.dp, RoundedCornerShape(dimens.cornerRadius), clip = false)
                            .clip(RoundedCornerShape(dimens.cornerRadius)),
                    ) {
                        menu { boardMenuOpen = false }
                    }
                }
            }
        }
    }

    val pager: @Composable () -> Unit = {
        StripAction(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            label = stringResource(R.string.board_previous_page),
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
            label = stringResource(R.string.board_next_page),
            enabled = index in 0 until pages.size - 1,
            onClick = { pages.getOrNull(index + 1)?.let { onSelectPage(it.id) } },
        )
    }

    val addPage: @Composable () -> Unit = {
        StripAction(Icons.Filled.Add, stringResource(R.string.board_add_page), onClick = onAddPage)
    }

    val deletePage: @Composable () -> Unit = {
        StripAction(
            icon = Icons.Filled.DeleteOutline,
            label = stringResource(R.string.board_delete_page),
            // The board must never end up with zero pages.
            enabled = pages.size > 1,
            onClick = onDeletePage,
        )
    }

    // A tap pops a 2 / 3 / 4 choice up above the button. The badge says how
    // many panes are open, since the icon alone cannot tell three from four.
    val split: @Composable () -> Unit = {
        Box {
            StripAction(
                icon = BoardSplitIcon,
                label = stringResource(R.string.board_split_view),
                selected = isSplit,
                badge = if (isSplit) paneCount.toString() else null,
                onClick = { splitMenuOpen = true },
            )
            if (splitMenuOpen) {
                // Hangs back over the strip rather than out past its end.
                PopupAbove(atStart = onRight, caretColor = IslandSurface, onDismiss = { splitMenuOpen = false }) {
                    FloatingIsland(contentPadding = PaddingValues(dimens.gutterSmall)) {
                        Row {
                            for (count in 2..maxPanes) {
                                SplitOption(
                                    panes = count,
                                    label = stringResource(R.string.board_split_count, count),
                                    selected = count == paneCount,
                                    onClick = {
                                        splitMenuOpen = false
                                        onSetPaneCount(count)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // An explicit way out, shown only while split.
    val closeSplit: @Composable () -> Unit = {
        StripAction(
            icon = Icons.Filled.Close,
            label = stringResource(R.string.board_close_split_view),
            onClick = onCloseSplit,
        )
    }

    // Each control its own island, set close together, as on the reference
    // panel and like the toolbar's separate squares: one long slab reads as a
    // single block from the back of a classroom.
    val groups = listOfNotNull(menuButton, pager, addPage, deletePage, split, closeSplit.takeIf { isSplit })
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall * 0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (if (onRight) groups.asReversed() else groups).forEach { group ->
            FloatingIsland(contentPadding = PaddingValues(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { group() }
            }
        }
    }
}

/**
 * A popup standing above its anchor, with a caret pointing down at it.
 *
 * [atStart] lines the popup's left edge up with the anchor's; otherwise its
 * right edge. Either way the caret sits under the middle of a strip button.
 */
@Composable
private fun PopupAbove(
    atStart: Boolean,
    caretColor: Color,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    val density = LocalDensity.current
    val above = remember(density, atStart) {
        val gap = with(density) { 6.dp.roundToPx() }
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = if (atStart) anchorBounds.left else anchorBounds.right - popupContentSize.width
                return IntOffset(
                    x.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width)),
                    anchorBounds.top - gap - popupContentSize.height,
                )
            }
        }
    }
    Popup(
        popupPositionProvider = above,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(horizontalAlignment = if (atStart) Alignment.Start else Alignment.End) {
            content()
            Box(
                Modifier
                    .padding(horizontal = dimens.chromeButton / 2 - 9.dp)
                    .size(width = 18.dp, height = 9.dp)
                    .background(caretColor, CaretShape),
            )
        }
    }
}

private val CaretShape = GenericShape { size, _ ->
    lineTo(size.width, 0f)
    lineTo(size.width / 2, size.height)
    close()
}

/**
 * A picture of the split itself: [panes] side-by-side boxes, each the same
 * size in every option, so the tile grows with the count.
 */
@Composable
private fun SplitOption(panes: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val dimens = SmartBoardTheme.dimens
    val ink = if (selected) TextOnChrome else TextOnChromeMuted
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .height(dimens.chromeButton)
            .clip(RoundedCornerShape(dimens.cornerRadius * 0.6f))
            .background(if (selected) Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 9.dp, vertical = 11.dp),
    ) {
        repeat(panes) {
            Box(
                Modifier
                    .width(dimens.chromeButton * 0.3f)
                    .fillMaxHeight()
                    .border(1.5.dp, ink, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun StripAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    badge: String? = null,
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

        if (badge != null) {
            Text(
                text = badge,
                color = Color.White,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 3.dp, bottom = 1.dp),
            )
        }
    }
}
