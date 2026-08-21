package com.smartboard.teach.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smartboard.teach.core.ui.theme.IslandBorder
import com.smartboard.teach.core.ui.theme.IslandSurface
import com.smartboard.teach.core.ui.theme.SmartBoardTheme

/**
 * A floating chrome panel that sits ON TOP of the canvas.
 *
 * The board is full-bleed: no UI reserves layout space from it. Everything —
 * toolbar, clock, zoom, page strip — floats as an island so the drawing
 * surface stays the whole window. On the 1920x1080 board this is the
 * difference between ~1566x706 of usable canvas and the full 1920x1080.
 */
@Composable
fun FloatingIsland(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(4.dp),
    content: @Composable () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Box(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(dimens.cornerRadius),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.30f),
            )
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .background(IslandSurface)
            .border(1.dp, IslandBorder, RoundedCornerShape(dimens.cornerRadius))
            .padding(contentPadding),
    ) {
        content()
    }
}
