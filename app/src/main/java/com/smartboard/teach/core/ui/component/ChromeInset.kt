package com.smartboard.teach.core.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smartboard.teach.core.ui.theme.SmartBoardTheme

/**
 * Keeps screen content clear of the floating hamburger island.
 *
 * The menu button floats over every screen at the top-left. Content screens
 * (roster, attendance, notes, settings) draw their own headers, so without
 * this their back button or title would sit underneath it.
 *
 * Applied as a start inset rather than a full top bar, so the screen still
 * uses the whole window.
 */
@Composable
fun Modifier.chromeInset(): Modifier {
    val dimens = SmartBoardTheme.dimens
    // Menu button width plus its padding on both sides.
    return this.padding(start = dimens.touchTarget + dimens.gutterSmall * 3)
}
