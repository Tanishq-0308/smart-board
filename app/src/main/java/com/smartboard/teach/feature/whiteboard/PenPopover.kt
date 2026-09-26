package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.ChromeBorder
import com.smartboard.teach.core.ui.theme.PenPaletteColors
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import com.smartboard.teach.domain.model.PenType

/**
 * The pen panel, modelled on the reference board.
 *
 * Three columns — Colour, Thickness, Pen — under a title bar, with a live
 * preview along the bottom. Everything about how the pen draws lives in one
 * place, so choosing a nib and its colour is one visit rather than a hunt
 * across separate popovers.
 */
@Composable
fun PenPopover(
    state: BoardState,
    onAddCustomColor: (Color) -> Unit,
    onRemoveCustomColor: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens
    val nib = state.penType
    val color = state.colorFor(nib)
    val width = state.widthFor(nib)
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        ColorPickerDialog(
            initial = color,
            onPick = {
                // Used straight away AND kept in Extras for next time.
                state.setColorFor(nib, it)
                onAddCustomColor(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    FloatingIsland(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.width(dimens.penPanelWidth)) {

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = nib.label,
                    color = TextOnChrome,
                    fontSize = dimens.bodySize,
                    fontWeight = FontWeight.Medium,
                )
            }

            PanelDivider(horizontal = true)

            // Height comes from the tallest column, not a constant: the nib
            // list grows as pen types are added, and a fixed height silently
            // clipped the last one off the bottom.
            Row(Modifier.height(IntrinsicSize.Min)) {
                ColorColumn(
                    selected = color,
                    extras = state.customPenColors,
                    onPick = { state.setColorFor(nib, it) },
                    onCustom = { picking = true },
                    onRemove = onRemoveCustomColor,
                    modifier = Modifier.weight(1.1f),
                )
                PanelDivider(horizontal = false)
                ThicknessColumn(
                    width = width,
                    onChange = { state.setWidthFor(nib, it) },
                    modifier = Modifier.weight(1f),
                )
                PanelDivider(horizontal = false)
                NibColumn(
                    selected = nib,
                    onPick = { state.selectPenType(it) },
                    modifier = Modifier.weight(0.8f),
                )
            }

            PanelDivider(horizontal = true)

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.board_pen_preview),
                    color = TextOnChromeMuted,
                    fontSize = dimens.labelSize,
                )
                StrokePreview(
                    color = color,
                    width = width,
                    alpha = nib.defaultAlpha,
                    modifier = Modifier
                        .padding(start = dimens.gutter)
                        .weight(1f)
                        .height(dimens.touchTarget * 0.5f),
                )
            }
        }
    }
}

@Composable
private fun ColorColumn(
    selected: Color,
    extras: List<Color>,
    onPick: (Color) -> Unit,
    onCustom: () -> Unit,
    onRemove: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens
    Column(
        modifier.padding(vertical = dimens.gutterSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.board_pen_color), color = TextOnChromeMuted, fontSize = dimens.labelSize)
        Column(
            Modifier.padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PenPaletteColors.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { c ->
                        Swatch(c, selected = c == selected) { onPick(c) }
                    }
                }
            }
        }
        // The teacher's own colours, then + to mix another. Null marks the + cell.
        Text(
            stringResource(R.string.board_pen_extras),
            color = TextOnChromeMuted,
            fontSize = dimens.labelSize,
            modifier = Modifier.padding(top = 8.dp),
        )
        // Holding is the delete gesture: a tap must stay "use this colour".
        if (extras.isNotEmpty()) {
            Text(stringResource(R.string.board_pen_hold_to_remove), color = TextOnChromeMuted, fontSize = dimens.labelSize * 0.75f)
        }
        Column(
            Modifier.padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            (extras + listOf<Color?>(null)).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { c ->
                        if (c == null) AddSwatch(onCustom)
                        else Swatch(c, selected = c == selected, onLongClick = { onRemove(c) }) { onPick(c) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Box(
        Modifier
            .size(dimens.swatchSize)
            .clip(CircleShape)
            .background(color)
            .border(
                // A ring rather than a tick: a tick would hide the colour it
                // is confirming, which is the one thing the control shows.
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) Accent else ChromeBorder,
                shape = CircleShape,
            )
            .combinedClickable(onLongClick = onLongClick, onClick = onClick),
    )
}

/** Opens the colour picker. Same size as a swatch so the grid stays even. */
@Composable
private fun AddSwatch(onClick: () -> Unit) {
    val dimens = SmartBoardTheme.dimens
    Box(
        Modifier
            .size(dimens.swatchSize)
            .clip(CircleShape)
            .border(1.dp, ChromeBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = stringResource(R.string.board_pen_custom_colour),
            tint = TextOnChrome,
            modifier = Modifier.size(dimens.swatchSize * 0.6f),
        )
    }
}

@Composable
private fun ThicknessColumn(
    width: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens
    Column(
        modifier.padding(vertical = dimens.gutterSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.board_pen_thickness), color = TextOnChromeMuted, fontSize = dimens.labelSize)
        Text(
            text = formatWidth(width),
            color = TextOnChrome,
            fontSize = dimens.labelSize,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
        VerticalSlider(
            value = width,
            onChange = onChange,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .weight(1f),
        )
    }
}

/** Two decimals without String.format, which is locale-sensitive. */
private fun formatWidth(width: Float): String {
    val whole = width.toInt()
    val hundredths = ((width - whole) * 100f).toInt().coerceIn(0, 99)
    return "$whole.${hundredths.toString().padStart(2, '0')}"
}

/**
 * A vertical slider.
 *
 * Hand-rolled because Material's Slider is horizontal, and rotating it with
 * graphicsLayer leaves the touch region unrotated — the thumb then follows a
 * drag along the wrong axis, which is worse than having no slider at all.
 */
@Composable
private fun VerticalSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val span = MAX_PEN_WIDTH - MIN_PEN_WIDTH
    val fraction = ((value - MIN_PEN_WIDTH) / span).coerceIn(0f, 1f)

    Box(
        modifier
            .width(28.dp)
            .pointerInput(Unit) {
                fun emit(y: Float) {
                    // Inverted: dragging UP must mean thicker.
                    val f = (1f - (y / size.height)).coerceIn(0f, 1f)
                    onChange(MIN_PEN_WIDTH + f * span)
                }
                detectDragGestures(
                    onDragStart = { emit(it.y) },
                    onDrag = { change, _ -> change.consume(); emit(change.position.y) },
                )
            }
            .pointerInput(Unit) {
                // Tapping the track jumps to that value, as on the reference.
                detectTapGestures { offset ->
                    val f = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                    onChange(MIN_PEN_WIDTH + f * span)
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .width(6.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(ChromeBorder),
        )
        Box(
            Modifier
                .width(6.dp)
                .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                .clip(RoundedCornerShape(3.dp))
                .background(Accent),
        )
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (fraction < 1f) Box(Modifier.weight(1f - fraction))
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
            if (fraction > 0f) Box(Modifier.weight(fraction))
        }
    }
}

@Composable
private fun NibColumn(
    selected: PenType,
    onPick: (PenType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens
    Column(
        modifier.padding(vertical = dimens.gutterSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(R.string.board_pen), color = TextOnChromeMuted, fontSize = dimens.labelSize)
        PenType.entries.forEach { type ->
            Box(
                Modifier
                    .size(dimens.chromeButton)
                    .clip(RoundedCornerShape(dimens.cornerRadius * 0.6f))
                    .border(
                        width = if (type == selected) 1.5.dp else 0.dp,
                        color = if (type == selected) TextOnChrome else Color.Transparent,
                        shape = RoundedCornerShape(dimens.cornerRadius * 0.6f),
                    )
                    .clickable { onPick(type) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = type.nibIcon(),
                    contentDescription = type.label,
                    tint = if (type == selected) TextOnChrome else TextOnChromeMuted,
                    // Larger than bar icons: the whole point of this column is
                    // telling five similar shapes apart, and at bar size the
                    // tips were indistinguishable.
                    modifier = Modifier.size(dimens.chromeIcon * 1.45f),
                )
            }
        }
    }
}

/** A live swoosh in the current colour, width and alpha. */
@Composable
private fun StrokePreview(
    color: Color,
    width: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.7f)
            cubicTo(
                size.width * 0.25f, size.height * -0.1f,
                size.width * 0.55f, size.height * 1.1f,
                size.width, size.height * 0.3f,
            )
        }
        drawPath(
            path = path,
            color = color.copy(alpha = alpha),
            style = Stroke(
                // Scaled down: the panel is far smaller than the board, and a
                // 40px nib drawn at full size would fill the whole footer.
                width = (width * 0.55f).coerceIn(1.5f, 22f),
                cap = StrokeCap.Round,
            ),
        )
    }
}

@Composable
private fun PanelDivider(horizontal: Boolean) {
    if (horizontal) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ChromeBorder),
        )
    } else {
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(ChromeBorder),
        )
    }
}

/** Thin enough for fine annotation, broad enough to be seen from the back row. */
const val MIN_PEN_WIDTH = 2f
const val MAX_PEN_WIDTH = 40f
