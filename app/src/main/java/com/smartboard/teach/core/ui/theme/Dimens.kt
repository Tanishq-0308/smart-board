package com.smartboard.teach.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Size scale derived from SCREEN SIZE, not density.
 *
 * Smart boards report wildly inconsistent densities — an 86" 4K panel may claim
 * mdpi (making dp-sized UI microscopic) or xxhdpi (making it enormous). Sizing
 * off `smallestScreenWidthDp` gives a stable physical result across those cases
 * and across the tablet used for development.
 */
@Immutable
data class Dimens(
    val scale: Float,
    val sidebarWidth: Dp,
    val sidebarCollapsedWidth: Dp,
    val headerHeight: Dp,
    val toolbarHeight: Dp,
    val pageStripHeight: Dp,
    val touchTarget: Dp,
    val touchTargetLarge: Dp,
    /**
     * Board chrome button.
     *
     * Smaller than [touchTarget] on purpose. The bottom bars are dense strips
     * of ~12 icons each; at the full 76dp target they merged into one slab
     * across the board. The reference panel uses compact icons for exactly
     * this reason, and 44dp still clears the 44dp accessibility floor.
     */
    val chromeButton: Dp,
    val chromeIcon: Dp,
    /** Pen panel: three columns of controls plus a preview footer. */
    val penPanelWidth: Dp,
    val swatchSize: Dp,
    val gutter: Dp,
    val gutterSmall: Dp,
    val gutterLarge: Dp,
    val cornerRadius: Dp,
    val iconSize: Dp,
    val iconSizeLarge: Dp,
    val clockTimeSize: TextUnit,
    val clockDateSize: TextUnit,
    val bodySize: TextUnit,
    val titleSize: TextUnit,
    val headlineSize: TextUnit,
    val labelSize: TextUnit,
    val studentCardMinWidth: Dp,
    /**
     * Space the top-left menu island occupies, including its padding.
     * The board toolbar insets by this so it can never slide underneath.
     */
    val menuIslandWidth: Dp,
    /** Same, for the top-right clock island. */
    val clockIslandWidth: Dp,
)

private const val BASELINE_SW_DP = 600f

/**
 * Builds the scale for the current window.
 *
 * - a ~10" tablet (sw ~800dp)  -> ~1.15x
 * - a 1080p board  (sw ~960dp) -> ~1.35x
 * - a 4K board at low density  -> capped at 1.7x
 *
 * Capped at both ends so a phone-sized emulator stays usable and a 4K panel
 * doesn't produce comically large chrome.
 */
fun dimensFor(smallestScreenWidthDp: Int): Dimens {
    val raw = smallestScreenWidthDp / BASELINE_SW_DP
    // Capped at 1.35x rather than 1.70x. The old layout had fixed strips that
    // absorbed oversized chrome; now everything floats over the canvas, and at
    // 1.70x the toolbar and clock islands ate the board they are supposed to
    // be revealing. 1.35x still gives a ~76dp touch target — comfortably
    // larger than the 56dp platform minimum for someone standing at a board.
    val s = raw.coerceIn(0.85f, 1.35f)

    return Dimens(
        scale = s,
        sidebarWidth = (208 * s).dp,
        sidebarCollapsedWidth = (76 * s).dp,
        headerHeight = (60 * s).dp,
        toolbarHeight = (68 * s).dp,
        pageStripHeight = (92 * s).dp,
        // 56dp baseline is the platform minimum; on a board a teacher taps
        // while standing and moving, so we push well past it.
        touchTarget = (56 * s).dp,
        touchTargetLarge = (72 * s).dp,
        chromeButton = (36 * s).dp,
        chromeIcon = (19 * s).dp,
        penPanelWidth = (300 * s).dp,
        swatchSize = (26 * s).dp,
        gutter = (16 * s).dp,
        gutterSmall = (8 * s).dp,
        gutterLarge = (28 * s).dp,
        cornerRadius = (12 * s).dp,
        iconSize = (24 * s).dp,
        iconSizeLarge = (32 * s).dp,
        // The clock is glanceable chrome, not content: it should read from
        // across the room without dominating the corner of the board.
        clockTimeSize = (20 * s).sp,
        clockDateSize = (11 * s).sp,
        bodySize = (16 * s).sp,
        titleSize = (20 * s).sp,
        headlineSize = (28 * s).sp,
        labelSize = (13 * s).sp,
        studentCardMinWidth = (260 * s).dp,
        // Touch target plus island padding plus the gutter it sits in.
        menuIslandWidth = (56 * s + 24).dp,
        // Wide enough for "Wednesday, 5 August 2026" at clockDateSize, which
        // is the longest string the clock ever renders.
        clockIslandWidth = (210 * s).dp,
    )
}

val LocalDimens = staticCompositionLocalOf { dimensFor(600) }

/** Convenience accessor: `SmartBoardTheme.dimens` inside composables. */
object SmartBoardTheme {
    val dimens: Dimens
        @Composable @ReadOnlyComposable get() = LocalDimens.current
}

@Composable
internal fun rememberDimens(): Dimens {
    val config = LocalConfiguration.current
    return dimensFor(config.smallestScreenWidthDp)
}
