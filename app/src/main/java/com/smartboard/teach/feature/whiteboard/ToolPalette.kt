package com.smartboard.teach.feature.whiteboard

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ViewInAr
import com.smartboard.teach.feature.whiteboard.container.TableSketch
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
import androidx.compose.ui.res.stringResource
import com.smartboard.teach.R
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
import androidx.compose.material.icons.filled.SwapHoriz
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
import com.smartboard.teach.core.ui.component.BoardAreaEraserIcon
import com.smartboard.teach.core.ui.component.BoardEraserIcon
import com.smartboard.teach.core.ui.component.NibBrushIcon
import com.smartboard.teach.core.ui.component.NibShapeIcon
import com.smartboard.teach.core.ui.component.NibTextIcon
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
private enum class OpenPanel { NONE, PEN, ERASER, SHAPES, INSERT, GEOMETRY, TABLE }

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
    onAddCustomColor: (Color) -> Unit,
    onRemoveCustomColor: (Color) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    textLanguage: com.smartboard.teach.domain.engine.BoardLanguage = com.smartboard.teach.domain.engine.BoardLanguage.DEFAULT,
    onTextLanguage: (com.smartboard.teach.domain.engine.BoardLanguage) -> Unit = {},
    onImportBackground: () -> Unit,
    onInsertTable: (rows: Int, cols: Int) -> Unit,
    onInsertMindmap: () -> Unit,
    onInsertPdf: () -> Unit,
    onInsertVideo: () -> Unit,
    onShowTimer: () -> Unit,
    onShowGame: (com.smartboard.teach.feature.whiteboard.games.Game) -> Unit,
    onInsertArea: () -> Unit = {},
    onBackgroundSettings: () -> Unit,
    onLessons: () -> Unit,
    onInsertImage: () -> Unit,
    onInsertInstrument: (InstrumentKind) -> Unit,
    onDeleteSelection: () -> Unit,
    onDuplicateSelection: () -> Unit,
    onMakeTable: () -> Unit = {},
    onMakeSolid: () -> Unit = {},
    onOpenSolid: (String) -> Unit = {},
    onTidyShapes: () -> Unit = {},
    onLookupSelection: () -> Unit,
    onExportSelection: () -> Unit,
    modifier: Modifier = Modifier,
    /** True when the bar sits bottom-right: popovers then line up with its right edge. */
    alignEnd: Boolean = false,
    /** Swaps the toolbar and the page strip between the bottom corners. */
    onSwapSides: () -> Unit = {},
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

    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {

        // --- popovers, above the bar ---
        if (panel != OpenPanel.NONE) {
            Box(Modifier.padding(bottom = dimens.gutterSmall * 1.5f)) {
                when (panel) {
                    OpenPanel.PEN -> PenPopover(
                        state = state,
                        onAddCustomColor = onAddCustomColor,
                        onRemoveCustomColor = onRemoveCustomColor,
                        textLanguage = textLanguage,
                        onTextLanguage = onTextLanguage,
                    )
                    OpenPanel.ERASER -> EraserPopover(
                        state = state,
                        onClear = { panel = OpenPanel.NONE; onClear() },
                    )
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

                    OpenPanel.TABLE -> TableSizePicker(
                        onPick = { rows, cols ->
                            panel = OpenPanel.NONE
                            onInsertTable(rows, cols)
                        },
                    )

                    OpenPanel.INSERT -> InsertTray(
                        onImage = { panel = OpenPanel.NONE; onInsertImage() },
                        onTable = { panel = OpenPanel.TABLE },
                        onGeometry = { panel = OpenPanel.GEOMETRY },
                        onMindmap = { panel = OpenPanel.NONE; onInsertMindmap() },
                        onPdf = { panel = OpenPanel.NONE; onInsertPdf() },
                        onVideo = { panel = OpenPanel.NONE; onInsertVideo() },
                        onTimer = { panel = OpenPanel.NONE; onShowTimer() },
                        onGame = { panel = OpenPanel.NONE; onShowGame(it) },
                        onArea = { panel = OpenPanel.NONE; onInsertArea() },
                        onText = {
                            panel = OpenPanel.NONE
                            state.clearSelection()
                            state.mode = BoardMode.TextPlacement
                        },
                        onBackground = { panel = OpenPanel.NONE; onBackgroundSettings() },
                        onLessons = { panel = OpenPanel.NONE; onLessons() },
                        // PDF reuses the existing background importer until it
                        // becomes a placeable object of its own.
                        pdfEnabled = true,
                        geometryEnabled = true,
                        mindmapEnabled = true,
                        videoEnabled = true,
                        timerEnabled = true,
                    )
                    OpenPanel.NONE -> Unit
                }
            }
        }

        // --- the eight buttons ---
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall * 0.6f)) {
            // The side switch sits in the bar's outer corner, as on the
            // reference panel, so it is never between the teacher and a tool.
            val swap: @Composable () -> Unit = {
                BarButton(Icons.Filled.SwapHoriz, stringResource(R.string.board_swap_sides), false) {
                    panel = OpenPanel.NONE
                    onSwapSides()
                }
            }
            if (!alignEnd) swap()

            // 1. Pen — tapping when already active opens the panel, so the
            //    common case (just draw) is one tap and styling is two.
            BarButton(
                // A pen whose cap is the current ink, as on the reference,
                // so the bar answers "what will this draw?" at a glance.
                icon = state.penType.nibIcon(),
                label = stringResource(state.penType.label),
                selected = penActive,
                ink = state.colorFor(state.penType),
                // The Text pen shows the language it reads, so the teacher can
                // see at a glance whether writing will become English or Hindi.
                badge = if (state.textPenActive) stringResource(textLanguage.badge) else null,
                caret = panel == OpenPanel.PEN,
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
            // Shows the eraser's current mode, as the reference bar does.
            BarButton(
                if (state.eraseByArea) BoardAreaEraserIcon else BoardEraserIcon,
                stringResource(R.string.board_eraser),
                eraserActive,
                caret = panel == OpenPanel.ERASER,
            ) {
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
                stringResource(R.string.board_select),
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
                caret = panel == OpenPanel.SHAPES,
            ) { toggle(OpenPanel.SHAPES) }

            // 5. Insert
            BarButton(
                Icons.Filled.Add,
                stringResource(R.string.board_insert),
                panel == OpenPanel.INSERT,
                // Not for the table and geometry pickers: they are narrower
                // than the bar up to this button, so a caret here would hang
                // in mid-air beside them.
                caret = panel == OpenPanel.INSERT,
            ) {
                toggle(OpenPanel.INSERT)
            }

            // 6/7. Undo & redo stay on the bar: they are reached mid-sentence
            //      and must never sit behind another tap.
            BarButton(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.board_undo), false, enabled = state.canUndo) {
                onUndo()
            }
            BarButton(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.board_redo), false, enabled = state.canRedo) {
                onRedo()
            }

            // 8. Gesture — pan and zoom by hand.
            BarButton(Icons.Filled.PanTool, stringResource(R.string.board_move_board), state.mode == BoardMode.Pan) {
                panel = OpenPanel.NONE
                state.clearSelection()
                state.mode = BoardMode.Pan
            }

            // Selection actions appear only with a selection, so the bar stays
            // eight buttons the rest of the time.
            if (state.hasSelection) {
                BarDivider()
                BarButton(Icons.Filled.ContentCopy, stringResource(R.string.board_duplicate), false) { onDuplicateSelection() }
                // Only when the selected ink is a hand-drawn grid.
                val selectionIsGrid = remember(state.selectedStrokeIds.toList()) {
                    TableSketch.recognise(state.selectedStrokes().filter { it.containerId == null }) != null
                }
                if (selectionIsGrid) {
                    BarButton(Icons.Filled.GridOn, stringResource(R.string.board_make_table), false) { onMakeTable() }
                }
                // Only when the selected ink is a sketched cube, cylinder or cone.
                val selectionIsSolid = remember(state.selectedStrokeIds.toList()) {
                    SolidSketch.recognise(state.selectedStrokes().filter { it.containerId == null }) != null
                }
                if (selectionIsSolid) {
                    BarButton(Icons.Filled.ViewInAr, stringResource(R.string.board_make_solid), false) { onMakeSolid() }
                }
                // Hand-drawn boxes, ovals and lines in the selection snap to clean shapes.
                val canTidy = remember(state.selectedStrokeIds.toList()) {
                    tidyCandidates(state.selectedStrokes()).isNotEmpty()
                }
                if (canTidy) {
                    BarButton(Icons.Filled.AutoFixHigh, stringResource(R.string.board_tidy_shapes), false) { onTidyShapes() }
                }
                // One board 3-D figure selected: work its maths out in 3D Maths.
                val solidName = state.selectedStrokes().singleOrNull()?.tool?.let { solidNameFor(it) }
                if (solidName != null) {
                    BarButton(Icons.Filled.ViewInAr, stringResource(R.string.board_open_in_maths3d), false) { onOpenSolid(solidName) }
                }
                BarButton(Icons.Filled.ImageSearch, stringResource(R.string.board_look_up), false) { onLookupSelection() }
                BarButton(Icons.Filled.Save, stringResource(R.string.board_save_as_image_or_pdf), false) { onExportSelection() }
                BarButton(Icons.Filled.DeleteOutline, stringResource(R.string.board_delete), false) { onDeleteSelection() }
            }

            if (alignEnd) swap()
        }
    }
}

/** 3D Maths' name for a board 3-D figure, or null for one it has no preset for. */
internal fun solidNameFor(tool: com.smartboard.teach.domain.model.DrawTool): String? = when (tool) {
    com.smartboard.teach.domain.model.DrawTool.CUBE -> "cube"
    com.smartboard.teach.domain.model.DrawTool.CYLINDER -> "cylinder"
    com.smartboard.teach.domain.model.DrawTool.CONE -> "cone"
    com.smartboard.teach.domain.model.DrawTool.SPHERE -> "sphere"
    com.smartboard.teach.domain.model.DrawTool.PYRAMID -> "pyramid"
    com.smartboard.teach.domain.model.DrawTool.PRISM -> "prism"
    else -> null
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
    /** Set for the pen: draws the pen picture with this ink instead of [icon]. */
    ink: Color? = null,
    badge: String? = null,
    caret: Boolean = false,
    onClick: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Box(Modifier.popoverCaret(caret), contentAlignment = Alignment.TopEnd) {
    Box(
        modifier = Modifier
            .barTile(selected)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (ink != null) {
            PenGlyph(ink, Modifier.size(dimens.chromeIcon * 1.5f))
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Accent else TextOnChrome,
                // Large, as on the reference: read from across a classroom.
                modifier = Modifier.size(dimens.chromeIcon * 1.3f),
            )
        }
    }
    if (badge != null) {
        Text(
            text = badge,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(2.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFE5484D))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
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

/**
 * The pointer from an open popover down to the button that opened it, as on
 * the reference panel: with four buttons able to open one, it says which.
 *
 * Drawn behind the button and outside its bounds, in the gap the popover
 * leaves above the bar, so no layout has to know where the button is.
 */
@Composable
private fun Modifier.popoverCaret(show: Boolean): Modifier {
    if (!show) return this
    val gap = SmartBoardTheme.dimens.gutterSmall
    return drawBehind {
        val h = gap.toPx()
        val top = -gap.toPx() * 1.5f - 1f
        drawPath(
            Path().apply {
                moveTo(size.width / 2 - h, top)
                lineTo(size.width / 2 + h, top)
                lineTo(size.width / 2, top + h)
                close()
            },
            IslandSurface,
        )
    }
}

/**
 * The eraser panel, modelled on the reference: option tiles, then a
 * slide-to-clear.
 *
 * Clearing is a slide rather than a tap because it wipes the whole page: a
 * tap is too easy to land by accident on a board people lean against.
 */
@Composable
private fun EraserPopover(state: BoardState, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = SmartBoardTheme.dimens
    FloatingIsland(modifier = modifier, contentPadding = PaddingValues(dimens.gutter)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gutter),
        ) {
            EraserModeTile(BoardEraserIcon, stringResource(R.string.board_eraser_rub), !state.eraseByArea) {
                state.eraseByArea = false
            }
            EraserModeTile(BoardAreaEraserIcon, stringResource(R.string.board_eraser_area), state.eraseByArea) {
                state.eraseByArea = true
            }
            // Size only means something to the rubbing eraser.
            if (!state.eraseByArea) ERASER_RADII.forEach { radius ->
                val selected = state.eraserScreenRadius == radius
                Box(
                    modifier = Modifier
                        .size(dimens.chromeButton + dimens.gutterSmall)
                        .clip(RoundedCornerShape(dimens.cornerRadius * 0.75f))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) Accent else ChromeBorder,
                            shape = RoundedCornerShape(dimens.cornerRadius * 0.75f),
                        )
                        .clickable { state.eraserScreenRadius = radius },
                    contentAlignment = Alignment.Center,
                ) {
                    // The dot IS the eraser tip at a readable scale, so the
                    // sizes compare against each other at a glance.
                    Box(
                        Modifier
                            .size((radius / 5f).dp + 8.dp)
                            .clip(CircleShape)
                            .background(if (selected) Accent else TextOnChromeMuted),
                    )
                }
            }
            SlideToClear(onClear)
        }
    }
}

/** One of the eraser's modes: a bordered tile, outlined and blue when chosen. */
@Composable
private fun EraserModeTile(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val dimens = SmartBoardTheme.dimens
    val shape = RoundedCornerShape(dimens.cornerRadius * 0.75f)
    Box(
        modifier = Modifier
            .size(dimens.chromeButton + dimens.gutterSmall)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Accent else ChromeBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Accent else TextOnChrome,
            modifier = Modifier.size(dimens.chromeIcon * 1.3f),
        )
    }
}

/** A thumb dragged to the far end of its track fires [onClear]; let go early and it springs back. */
@Composable
private fun SlideToClear(onClear: () -> Unit) {
    val dimens = SmartBoardTheme.dimens
    val thumb = dimens.chromeButton
    val inset = 4.dp
    val trackWidth = thumb * 3.6f
    val maxPx = with(LocalDensity.current) { (trackWidth - thumb - inset * 2).toPx() }
    var dragPx by remember { mutableStateOf(0f) }
    val shape = RoundedCornerShape(dimens.cornerRadius * 0.75f)

    Box(
        Modifier
            .width(trackWidth)
            .height(thumb + dimens.gutterSmall)
            .border(1.dp, ChromeBorder, shape)
            .padding(inset),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .align(Alignment.Center)
                .padding(start = thumb)
                .alpha(1f - dragPx / maxPx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        ) {
            Text("»", color = TextOnChromeMuted, fontSize = dimens.bodySize)
            Text(stringResource(R.string.board_slide_clear), color = TextOnChrome, fontSize = dimens.bodySize)
        }
        Box(
            Modifier
                .offset { IntOffset(dragPx.roundToInt(), 0) }
                .size(thumb)
                .clip(RoundedCornerShape(dimens.cornerRadius * 0.5f))
                .background(Color.White)
                .draggable(
                    state = rememberDraggableState { dragPx = (dragPx + it).coerceIn(0f, maxPx) },
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        if (dragPx >= maxPx * 0.9f) onClear()
                        dragPx = 0f
                    },
                ),
        )
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

/**
 * Small, medium and large, in screen px — a slider is fiddly for a control
 * used mid-sentence. Sized for a hand rubbing a wall-sized board, as on the
 * reference: the old 20/30/60 left a disc smaller than the fingertip on it.
 */
private val ERASER_RADII = listOf(30f, 60f, 100f)

/**
 * A bar button's tile. Selected keeps the dark tile and adds a blue outline,
 * as on the reference, rather than flooding it blue: a blue fill drowned the
 * pen's ink colour, which is the one thing that button exists to show.
 */
@Composable
private fun Modifier.barTile(selected: Boolean): Modifier {
    val dimens = SmartBoardTheme.dimens
    val shape = RoundedCornerShape(dimens.cornerRadius * 0.75f)
    return size(dimens.chromeButton + dimens.gutterSmall)
        .clip(shape)
        .background(IslandSurface)
        .then(if (selected) Modifier.border(2.dp, Accent, shape) else Modifier)
}

/**
 * The pen on the bar, as the reference draws it: a white pen lying corner to
 * corner, its cap in the ink it will draw with.
 */
@Composable
private fun PenGlyph(ink: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val u = size.minDimension / 24f
        fun at(x: Float, y: Float) = Offset(x * u, y * u)
        // Barrel.
        drawLine(Color.White, at(7.2f, 16.8f), at(18f, 6f), strokeWidth = 3.6f * u, cap = StrokeCap.Round)
        // Cap: ink inside a white rim, so black or navy ink still shows on
        // the dark tile.
        drawLine(Color.White, at(17.5f, 6.5f), at(20f, 4f), strokeWidth = 3.6f * u, cap = StrokeCap.Round)
        drawLine(ink, at(17.6f, 6.4f), at(20f, 4f), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
        // Cone down to the point.
        drawPath(
            Path().apply {
                moveTo(8.5f * u, 18.1f * u)
                lineTo(5.9f * u, 15.5f * u)
                lineTo(3.6f * u, 20.4f * u)
                close()
            },
            Color(0xFFC9CED6),
        )
        // The writing point, in the ink too.
        drawLine(ink, at(4.9f, 19.1f), at(3.6f, 20.4f), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
    }
}


/** Each nib gets its own silhouette, on the bar and in the panel alike. */
internal fun PenType.nibIcon() = when (this) {
    PenType.PEN -> NibPenIcon
    PenType.MARKER -> NibMarkerIcon
    PenType.HIGHLIGHTER -> NibHighlighterIcon
    PenType.FOUNTAIN -> NibFountainIcon
    PenType.BRUSH -> NibBrushIcon
    PenType.TEXT -> NibTextIcon
    PenType.SHAPE -> NibShapeIcon
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
    caret: Boolean = false,
    onClick: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Box(
        modifier = Modifier
            .popoverCaret(caret)
            .barTile(selected)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(dimens.chromeIcon)) {
            drawShapeGlyph(tool, if (selected) Accent else TextOnChrome)
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
            GeometryButton(RulerIcon, stringResource(R.string.board_ruler)) { onPick(InstrumentKind.RULER) }
            GeometryButton(SetSquare45Icon, stringResource(R.string.board_set_square_45)) {
                onPick(InstrumentKind.SET_SQUARE_45)
            }
            GeometryButton(SetSquare30Icon, stringResource(R.string.board_set_square_30_60)) {
                onPick(InstrumentKind.SET_SQUARE_30)
            }
            GeometryButton(ProtractorIcon, stringResource(R.string.board_protractor)) { onPick(InstrumentKind.PROTRACTOR) }
            GeometryButton(CompassIcon, stringResource(R.string.board_compass)) { onPick(InstrumentKind.COMPASS) }
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
