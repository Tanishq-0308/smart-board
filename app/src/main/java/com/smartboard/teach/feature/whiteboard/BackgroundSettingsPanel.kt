package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import com.smartboard.teach.domain.model.BoardCanvasStyle
import com.smartboard.teach.domain.model.GridStyle
import com.smartboard.teach.domain.model.defaultGridColor

/**
 * Paper colour and grid for the current page, matching the reference panel's
 * "Background Settings".
 *
 * Per PAGE, so one lesson can hold a squared page for a graph and a lined page
 * for writing. The file actions the reference shows beside this (New / Open /
 * Save / Upload) belong to the save-and-open milestone and are not here yet.
 */
@Composable
fun BackgroundSettingsPanel(
    style: BoardCanvasStyle,
    onStyleChanged: (BoardCanvasStyle) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // width(IntrinsicSize.Min) so the island hugs the swatch rows. Without it
    // the header Row's fillMaxWidth propagates outward and the panel stretches
    // across the whole board.
    FloatingIsland(
        modifier = modifier.width(IntrinsicSize.Min),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Background", color = TextOnChrome, fontSize = 15.sp)
                Box(Modifier.weight(1f))
                Text(
                    text = gridLabel(style.grid),
                    color = TextOnChromeMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 4.dp),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close background settings",
                        tint = TextOnChrome,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Text("Colour", color = TextOnChromeMuted, fontSize = 11.sp)
            // Two rows of six, as in the reference: pale tints above, deep
            // papers below.
            BoardCanvasStyle.PALETTE.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { argb ->
                        ColourSwatch(
                            argb = argb,
                            selected = style.colorArgb == argb,
                            onClick = {
                                // Grid colour is cleared, not kept: a grid
                                // tuned for pale paper is invisible on dark,
                                // so it must be re-derived for the new paper.
                                onStyleChanged(style.copy(colorArgb = argb, gridColorArgb = null))
                            },
                        )
                    }
                }
            }

            Text("Grid", color = TextOnChromeMuted, fontSize = 11.sp)
            GridStyle.entries.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { grid ->
                        GridThumbnail(
                            grid = grid,
                            paperArgb = style.colorArgb,
                            selected = style.grid == grid,
                            onClick = { onStyleChanged(style.copy(grid = grid)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColourSwatch(argb: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Accent else SWATCH_EDGE,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/**
 * A miniature of the grid itself, not an icon.
 *
 * The eight styles differ only in their pattern, so a literal preview is the
 * only thing that tells them apart — and it is drawn with the same rules the
 * board uses, on the paper currently chosen.
 */
@Composable
private fun GridThumbnail(
    grid: GridStyle,
    paperArgb: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val lineColor = Color(defaultGridColor(paperArgb) or ALPHA_FLOOR)

    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(paperArgb))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Accent else SWATCH_EDGE,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick),
    ) {
        androidx.compose.foundation.Canvas(Modifier.padding(5.dp).size(36.dp)) {
            val step = size.width / 4f
            val thin = 1f

            fun lattice(width: Float) {
                for (i in 1..3) {
                    val p = i * step
                    drawLine(lineColor, Offset(p, 0f), Offset(p, size.height), width)
                    drawLine(lineColor, Offset(0f, p), Offset(size.width, p), width)
                }
            }

            when (grid) {
                GridStyle.NONE -> Unit
                GridStyle.THIN -> lattice(thin)
                GridStyle.SQUARE -> lattice(thin * 2f)
                GridStyle.MIX -> {
                    lattice(thin)
                    val mid = size.width / 2f
                    drawLine(lineColor, Offset(mid, 0f), Offset(mid, size.height), thin * 2.5f)
                    drawLine(lineColor, Offset(0f, mid), Offset(size.width, mid), thin * 2.5f)
                }
                GridStyle.LINED -> for (i in 1..3) {
                    val y = i * step
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), thin)
                }
                GridStyle.DOTTED -> for (i in 1..3) {
                    for (j in 1..3) {
                        drawCircle(lineColor, radius = 1.4f, center = Offset(i * step, j * step))
                    }
                }
                GridStyle.RANGOLI -> {
                    lattice(thin)
                    for (i in 0..3) {
                        for (j in 0..3) {
                            val x = i * step
                            val y = j * step
                            drawLine(lineColor, Offset(x, y), Offset(x + step, y + step), thin)
                            drawLine(lineColor, Offset(x + step, y), Offset(x, y + step), thin)
                        }
                    }
                }
            }
        }
    }
}

private fun gridLabel(grid: GridStyle): String = when (grid) {
    GridStyle.NONE -> "Plain"
    GridStyle.THIN -> "Thin grid"
    GridStyle.MIX -> "Mix grid"
    GridStyle.SQUARE -> "Square grid"
    GridStyle.DOTTED -> "Dotted grid"
    GridStyle.LINED -> "Trace grid"
    GridStyle.RANGOLI -> "Rangoli grid"
}

private val SWATCH_EDGE = Color(0x33FFFFFF)

/**
 * The board's grid alpha is deliberately faint; at thumbnail size that is
 * invisible, so previews are drawn at full opacity.
 */
private const val ALPHA_FLOOR = 0xFF000000.toInt()
