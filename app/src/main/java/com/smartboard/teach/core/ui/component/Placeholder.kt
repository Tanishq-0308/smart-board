package com.smartboard.teach.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted

/**
 * Temporary stand-in for screens not yet implemented, so the shell is
 * navigable and verifiable before the features land. Removed as each feature
 * step completes.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Construction,
) {
    val dimens = SmartBoardTheme.dimens
    Column(
        modifier = modifier.fillMaxSize().padding(dimens.gutterLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextOnSurfaceMuted,
            modifier = Modifier.size(dimens.iconSizeLarge * 1.6f),
        )
        Text(
            text = title,
            color = TextOnSurface,
            fontSize = dimens.headlineSize,
            modifier = Modifier.padding(top = dimens.gutter),
        )
        Text(
            text = detail,
            color = TextOnSurfaceMuted,
            fontSize = dimens.bodySize,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = dimens.gutterSmall),
        )
    }
}

/** Shown when a list has no rows yet. */
@Composable
fun EmptyState(
    title: String,
    detail: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) = PlaceholderScreen(title = title, detail = detail, modifier = modifier, icon = icon)
