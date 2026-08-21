package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Straighten
import com.smartboard.teach.feature.whiteboard.instruments.InstrumentKind
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop169
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.HighlightAlt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.smartboard.teach.core.ui.component.BoardEraserIcon
import com.smartboard.teach.core.ui.component.NibBrushIcon
import com.smartboard.teach.core.ui.component.NibFountainIcon
import com.smartboard.teach.core.ui.component.NibHighlighterIcon
import com.smartboard.teach.core.ui.component.NibMarkerIcon
import com.smartboard.teach.core.ui.component.NibPenIcon
import com.smartboard.teach.domain.model.PenType
import com.smartboard.teach.core.ui.component.CompassIcon
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.component.ProtractorIcon
import com.smartboard.teach.core.ui.component.RulerIcon
import com.smartboard.teach.core.ui.component.SetSquare30Icon
import com.smartboard.teach.core.ui.component.SetSquare45Icon
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.ChromeBorder
import com.smartboard.teach.core.ui.theme.HighlighterColors
import com.smartboard.teach.core.ui.theme.IslandSurface
import com.smartboard.teach.core.ui.theme.PenColors
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import com.smartboard.teach.domain.model.DrawTool

/** Which popover, if any, is open above the bar. */
private enum class OpenPanel { NONE, PEN, ERASER, SHAPES, INSERT, GEOMETRY }

/**
 * The board toolbar: eight buttons, bottom-left, modelled on the reference
 * panel.
 *
 *   Pen · Eraser · Select · Shapes · Insert · Undo · Redo · Gesture
 *
 * Each is a separate rounded square rather than segments of one island, so
 * the bar reads as discrete tools at a glance from across a classroom.
 *
 * Everything else folds into those eight rather than earning its own button:
 * the five nibs, colour and thickness live in the pen panel; text, tables and
 * backgrounds live under Insert; pan lives on the gesture button. A teacher
 * standing at a board scans a short row far faster than a long one, and the
 * previous 12-button bar had already started to merge into a single slab.
 */
@Composable
fun ToolPalette(
    state: BoardState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onSnapshot: () -> Unit,
    onImportBackground: () -> Unit,
    onInsertTable: () -> Unit,
    onInsertMindmap: () -> Unit,
    onInsertPdf: () -> Unit,
    onInsertVideo: () -> Unit,
    onShowTimer: () -> Unit,
    onWebSearch: () -> Unit,
    onBackgroundSettings: () -> Unit,
    onLessons: () -> Unit,
    onInsertImage: () -> Unit,
    onInsertInstrument: (InstrumentKind) -> Unit,
    onDeleteSelection: () -> Unit,
    onDuplicateSelection: () -> Unit,
    onLookupSelection: () -> Unit,
    onExportSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens
    var panel by remember { mutableStateOf(OpenPanel.NONE) }

    fun toggle(target: OpenPanel) {
        panel = if (panel == target) OpenPanel.NONE else target
    }

    val isDrawing = state.mode == BoardMode.Draw
    val penActive = isDrawing && state.tool.isFreehand
    val eraserActive = isDrawing && state.tool == DrawTool.ERASER
    val shapeActive = isDrawing && state.tool.isTwoPointShape

    Column(modifier, horizontalAlignment = Alignment.Start) {

        // --- popovers, above the bar ---
        if (panel != OpenPanel.NONE) {
            Box(Modifier.padding(bottom = dimens.gutterSmall)) {
                when (panel) {
                    OpenPanel.PEN -> PenPopover(state = state)
                    OpenPanel.ERASER -> EraserSizePopover(state = state)
                    OpenPanel.SHAPES -> ShapesPopover(
                        state = state,
                        onPicked = { panel = OpenPanel.NONE },
                    )
                    OpenPanel.GEOMETRY -> GeometryPicker(
                        onPick = { kind ->
                            panel = OpenPanel.NONE
                            onInsertInstrument(kind)
                        },
                    )

                    OpenPanel.INSERT -> InsertTray(
                        onImage = { panel = OpenPanel.NONE; onInsertImage() },
                        onTable = { panel = OpenPanel.NONE; onInsertTable() },
                        onGeometry = { panel = OpenPanel.GEOMETRY },
                        onMindmap = { panel = OpenPanel.NONE; onInsertMindmap() },
                        onPdf = { panel = OpenPanel.NONE; onInsertPdf() },
                        onVideo = { panel = OpenPanel.NONE; onInsertVideo() },
                        onWeb = { panel = OpenPanel.NONE; onWebSearch() },
                        onTimer = { panel = OpenPanel.NONE; onShowTimer() },
                        onText = {
                            panel = OpenPanel.NONE
                            state.clearSelection()
                            state.mode = BoardMode.TextPlacement
                        },
                        onSnapshot = { panel = OpenPanel.NONE; onSnapshot() },
                        onBackground = { panel = OpenPanel.NONE; onBackgroundSettings() },
                        onLessons = { panel = OpenPanel.NONE; onLessons() },
                        // PDF reuses the existing background importer until it
                        // becomes a placeable object of its own.
                        pdfEnabled = true,
                        geometryEnabled = true,
                        mindmapEnabled = true,
                        videoEnabled = true,
                        timerEnabled = true,
                        webEnabled = true,
                    )
                    OpenPanel.NONE -> Unit
                }
            }
        }

        // --- the eight buttons ---
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall * 0.6f)) {

            // 1. Pen — tapping when already active opens the panel, so the
            //    common case (just draw) is one tap and styling is two.
            BarButton(
                // Shows the ACTIVE nib, so the bar answers "which pen am I
                // holding?" without opening the panel.
                icon = state.penType.nibIcon(),
                label = state.penType.label,
                selected = penActive,
                tint = state.colorFor(state.penType),
            ) {
                if (penActive) {
                    toggle(OpenPanel.PEN)
                } else {
                    panel = OpenPanel.NONE
                    state.clearSelection()
                    state.selectPenType(state.penType)
                }
            }

            // 2. Eraser — an eraser block, never a waste bin: a bin reads as
            //    "delete everything", which is the opposite of a correction.
            BarButton(BoardEraserIcon, "Eraser", eraserActive) {
                if (eraserActive) {
                    toggle(OpenPanel.ERASER)
                } else {
                    panel = OpenPanel.NONE
                    selectDrawTool(state, DrawTool.ERASER)
                }
            }

            // 3. Select
            BarButton(
                Icons.Outlined.HighlightAlt,
                "Select",
                state.mode == BoardMode.Select,
            ) {
                panel = OpenPanel.NONE
                state.mode = BoardMode.Select
            }

            // 4. Shapes
            ShapeBarButton(
                // Shows whichever shape is armed, drawn with the board's own
                // geometry — so the bar says what the next drag will produce.
                tool = if (shapeActive) state.tool else DrawTool.RECT,
                selected = shapeActive,
            ) { toggle(OpenPanel.SHAPES) }

            // 5. Insert
            BarButton(Icons.Filled.Add, "Insert", panel == OpenPanel.INSERT) {
                toggle(OpenPanel.INSERT)
            }

            // 6/7. Undo & redo stay on the bar: they are reached mid-sentence
            //      and must never sit behind another tap.
            BarButton(Icons.AutoMirrored.Filled.Undo, "Undo", false, enabled = state.canUndo) {
                onUndo()
            }
            BarButton(Icons.AutoMirrored.Filled.Redo, "Redo", false, enabled = state.canRedo) {
                onRedo()
            }

            // 8. Gesture — pan and zoom by hand.
            BarButton(Icons.Filled.PanTool, "Move board", state.mode == BoardMode.Pan) {
                panel = OpenPanel.NONE
                state.clearSelection()
                state.mode = BoardMode.Pan
            }

            // Selection actions appear only with a selection, so the bar stays
            // eight buttons the rest of the time.
            if (state.hasSelection) {
                BarDivider()
                BarButton(Icons.Filled.ContentCopy, "Duplicate", false) { onDuplicateSelection() }
                BarButton(Icons.Filled.ImageSearch, "Look up", false) { onLookupSelection() }
                BarButton(Icons.Filled.Save, "Save as image or PDF", false) { onExportSelection() }
                BarButton(Icons.Filled.DeleteOutline, "Delete", false) { onDeleteSelection() }
            }
        }
    }
}

/**
 * One toolbar button: a rounded square with its own surface.
 *
 * Separate squares rather than a single segmented island — the reference
 * panel does the same, and a merged bar of a dozen icons reads as one block
 * from the back of a classroom.
 */
@Composable
private fun BarButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Box(
        modifier = Modifier
            .size(dimens.chromeButton + dimens.gutterSmall)
            .clip(RoundedCornerShape(dimens.cornerRadius * 0.75f))
            .background(if (selected) Accent else IslandSurface)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            // A selected pen shows its own ink colour, so the bar answers
            // "what will this draw?" without opening anything.
            tint = when {
                selected && tint != null -> contrastingInk(tint)
                selected -> TextOnChrome
                else -> TextOnChromeMuted
            },
            modifier = Modifier.size(dimens.chromeIcon),
        )
    }
}

@Composable
private fun BarDivider() {
    val dimens = SmartBoardTheme.dimens
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(width = 1.dp, height = dimens.chromeButton)
            .background(ChromeBorder),
    )
}

@Composable
private fun PopoverRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val dimens = SmartBoardTheme.dimens
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.cornerRadius * 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextOnChromeMuted,
            modifier = Modifier.size(dimens.chromeIcon),
        )
        Text(
            text = label,
            color = TextOnChrome,
            fontSize = dimens.bodySize,
            modifier = Modifier.padding(start = dimens.gutter),
        )
    }
}

@Composable
private fun EraserSizePopover(state: BoardState, modifier: Modifier = Modifier) {
    val dimens = SmartBoardTheme.dimens
    FloatingIsland(modifier = modifier, contentPadding = PaddingValues(dimens.gutterSmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Eraser",
                color = TextOnChromeMuted,
                fontSize = dimens.labelSize,
                modifier = Modifier.padding(horizontal = dimens.gutterSmall),
            )
            ERASER_RADII.forEach { radius ->
                val selected = state.eraserScreenRadius == radius
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(dimens.chromeButton)
                        .clip(RoundedCornerShape(dimens.cornerRadius * 0.6f))
                        .background(if (selected) Accent else Color.Transparent)
                        .clickable { state.eraserScreenRadius = radius },
                    contentAlignment = Alignment.Center,
                ) {
                    // The dot IS the eraser tip at a readable scale, so the
                    // sizes compare against each other at a glance.
                    Box(
                        Modifier
                            .size((radius / 3f).dp + 8.dp)
                            .clip(CircleShape)
                            .background(if (selected) TextOnChrome else TextOnChromeMuted),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShapeButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .size(dimens.chromeButton)
            .clip(RoundedCornerShape(dimens.cornerRadius * 0.6f))
            .background(if (selected) Accent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) TextOnChrome else TextOnChromeMuted,
            modifier = Modifier.size(dimens.chromeIcon),
        )
    }
}

private fun selectDrawTool(state: BoardState, tool: DrawTool) {
    state.clearSelection()
    state.mode = BoardMode.Draw
    state.tool = tool
}

/** Small, medium and large — a slider is fiddly for a control used mid-sentence. */
private val ERASER_RADII = listOf(20f, 30f, 60f)

/**
 * Keeps a selected pen's icon legible on the accent fill.
 *
 * The pen's own colour is used where it reads, but a near-black or deep blue
 * nib on the blue accent would vanish, so those fall back to white.
 */
private fun contrastingInk(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return if (luminance < 0.45f) Color.White else color
}

/** Each nib gets its own silhouette, on the bar and in the panel alike. */
internal fun PenType.nibIcon() = when (this) {
    PenType.PEN -> NibPenIcon
    PenType.MARKER -> NibMarkerIcon
    PenType.HIGHLIGHTER -> NibHighlighterIcon
    PenType.FOUNTAIN -> NibFountainIcon
    PenType.BRUSH -> NibBrushIcon
}

/**
 * The Shapes bar button.
 *
 * Draws the armed shape rather than picking from a fixed icon set: with 25
 * shapes a lookup table would need 25 entries and would silently fall back to
 * a wrong glyph for any shape it missed.
 */
@Composable
private fun ShapeBarButton(
    tool: DrawTool,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Box(
        modifier = Modifier
            .size(dimens.chromeButton + dimens.gutterSmall)
            .clip(RoundedCornerShape(dimens.cornerRadius * 0.75f))
            .background(if (selected) Accent else IslandSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(dimens.chromeIcon)) {
            drawShapeGlyph(tool, if (selected) TextOnChrome else TextOnChromeMuted)
        }
    }
}

/**
 * The four geometry instruments, behind the tray's compass icon.
 *
 * A second step rather than four slots in the tray: they are a family a
 * teacher picks between for one task, and the tray is already ten wide.
 */
@Composable
private fun GeometryPicker(
    onPick: (InstrumentKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens
    FloatingIsland(modifier = modifier, contentPadding = PaddingValues(dimens.gutterSmall)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            GeometryButton(RulerIcon, "Ruler") { onPick(InstrumentKind.RULER) }
            GeometryButton(SetSquare45Icon, "Set square 45°") {
                onPick(InstrumentKind.SET_SQUARE_45)
            }
            GeometryButton(SetSquare30Icon, "Set square 30/60°") {
                onPick(InstrumentKind.SET_SQUARE_30)
            }
            GeometryButton(ProtractorIcon, "Protractor") { onPick(InstrumentKind.PROTRACTOR) }
            GeometryButton(CompassIcon, "Compass") { onPick(InstrumentKind.COMPASS) }
        }
    }
}

/** An instrument button: its own silhouette, no label. */
@Composable
private fun GeometryButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Box(
        modifier = Modifier
            .size(dimens.chromeButton + dimens.gutterSmall)
            .clip(RoundedCornerShape(dimens.cornerRadius * 0.6f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextOnChrome,
            modifier = Modifier.size(dimens.chromeIcon * 1.2f),
        )
    }
}
