package com.smartboard.teach.feature.whiteboard.instruments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.component.BoardAngleIcon
import com.smartboard.teach.core.ui.component.BoardArcIcon
import com.smartboard.teach.core.ui.component.BoardArmIcon
import com.smartboard.teach.core.ui.component.BoardSectorIcon
import com.smartboard.teach.core.ui.component.BoardStretchIcon
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.feature.whiteboard.BoardState
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Draws the active geometry instrument and its controls.
 *
 * A Compose layer ABOVE the canvas rather than part of the ink: an instrument
 * is a thing lying on the board, not something drawn on it. It must never
 * enter the stroke list, the undo stack or an export — a ruler that showed up
 * in a saved lesson would be a bug, not a feature.
 */
@Composable
fun InstrumentLayer(
    state: BoardState,
    modifier: Modifier = Modifier,
) {
    if (state.instruments.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val camera = state.camera
    val rotateIcon = rememberVectorPainter(Icons.Filled.RotateRight)
    val resizeIcon = rememberVectorPainter(Icons.Filled.OpenInFull)
    val stretchIcon = rememberVectorPainter(BoardStretchIcon)
    val closeIcon = rememberVectorPainter(Icons.Filled.Close)
    val moveIcon = rememberVectorPainter(Icons.Filled.OpenWith)
    val compassIcons = CompassIcons(
        rotate = rotateIcon,
        swap = rememberVectorPainter(Icons.Filled.SwapHoriz),
        spread = resizeIcon,
        close = closeIcon,
    )
    val extraIcons = mapOf(
        InstrumentDrag.ARM to rememberVectorPainter(BoardArmIcon),
        InstrumentDrag.DRAW_ANGLE to rememberVectorPainter(BoardAngleIcon),
        InstrumentDrag.DRAW_ARC to rememberVectorPainter(BoardArcIcon),
        InstrumentDrag.DRAW_SECTOR to rememberVectorPainter(BoardSectorIcon),
    )

    Box(modifier.fillMaxSize()) {
        // INERT: all pointer input for instruments is handled by BoardCanvas,
        // which is the single owner of touches. A pointer handler here would
        // win the hit-test across the whole board and make it undrawable.
        Canvas(Modifier.fillMaxSize()) {
            val zoom = camera.zoom
            state.instruments.forEach { instrument ->
                val sx = camera.worldToScreenX(instrument.x)
                val sy = camera.worldToScreenY(instrument.y)
                when (instrument.kind) {
                    InstrumentKind.RULER -> drawRuler(instrument, sx, sy, measurer)
                    InstrumentKind.SET_SQUARE_45, InstrumentKind.SET_SQUARE_30 ->
                        drawSetSquare(instrument, sx, sy, measurer)
                    InstrumentKind.PROTRACTOR -> drawProtractor(instrument, sx, sy, measurer)
                    InstrumentKind.COMPASS -> drawCompass(
                        instrument = instrument,
                        camera = camera,
                        measurer = measurer,
                        icons = compassIcons,
                        ink = Color(state.currentStyle().colorArgb),
                        inkWidth = state.currentStyle().baseWidthPx * zoom,
                        sweeping = state.instrumentDragMode == InstrumentDrag.SWEEP &&
                            state.draggedInstrumentId == instrument.id,
                    )
                }
                // The compass is moved, turned and sized by its own legs;
                // every other instrument gets the same three handles.
                instrumentHandles(instrument, camera)?.let { handles ->
                    val (move, rotate, resize, close) = handles
                    // The ruler and set squares move by a white grip and
                    // stretch along their length, as on the reference board.
                    // The protractor moves by its body and readout, and has
                    // no stretch.
                    val gripped = instrument.kind.hasRulingEdge
                    when {
                        gripped -> drawMoveGrip(instrument, move)
                        instrument.kind != InstrumentKind.PROTRACTOR -> drawHandle(move, moveIcon)
                    }
                    drawHandle(rotate, rotateIcon)
                    resize?.let { drawHandle(it, if (gripped) stretchIcon else resizeIcon) }
                    close?.let { drawHandle(it, closeIcon) }
                    handles.extras.forEach { (mode, at) -> extraIcons[mode]?.let { drawHandle(at, it) } }
                    val rotating = state.instrumentDragMode == InstrumentDrag.ROTATE &&
                        state.draggedInstrumentId == instrument.id
                    val degrees = InstrumentGeometry.displayDegrees(instrument.rotation)
                    if (rotating || degrees != 0) drawAngleLabel(rotate, degrees, measurer)
                }
            }
        }

        // Each instrument carries its own controls, so two on the board at
        // once can be put away or reset independently.
        // Instruments with their own close button (all of them now but any
        // future one without) get none floating beside them.
        state.instruments.forEach { instrument ->
            val ownClose = instrument.kind == InstrumentKind.COMPASS ||
                instrumentHandles(instrument, camera)?.close != null
            if (!ownClose) InstrumentControls(state, instrument)
        }
    }
}

/**
 * Put away, beside the instrument it belongs to. Everything else is done on
 * the instrument itself: handles on the ruler, set squares and protractor,
 * and the hinge, needle and pencil legs on the compass.
 */
@Composable
private fun InstrumentControls(state: BoardState, instrument: Instrument) {
    val dimens = SmartBoardTheme.dimens
    val camera = state.camera
    val anchor = controlAnchor(instrument, camera)
    // Never off the board: a 30cm ruler is wider than some panels,
    // and controls past the edge would leave it impossible to put away.
    val columnPx = with(LocalDensity.current) {
        (dimens.chromeButton + dimens.gutterSmall * 2 + 4.dp).toPx()
    }

    Column(
        Modifier
            .offset {
                IntOffset(
                    anchor.x.coerceIn(0f, (state.viewportWidth - columnPx).coerceAtLeast(0f)).toInt(),
                    anchor.y.coerceIn(0f, (state.viewportHeight - columnPx * 2f).coerceAtLeast(0f)).toInt(),
                )
            }
            .padding(dimens.gutterSmall),
    ) {
        ControlButton(Icons.Filled.Close, stringResource(R.string.panel_instrument_put_away)) {
            state.instruments.removeAll { it.id == instrument.id }
        }
    }
}

/**
 * Where the Close button sits: just right of the instrument's top-right
 * corner, so it reads as belonging to the instrument.
 *
 * Right of the whole outline rather than of one edge, so a tilted ruler's far
 * corner never slides under it. The compass's sits by its hinge.
 */
private fun controlAnchor(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
): Offset {
    val size = instrument.lengthCm * InstrumentGeometry.pxPerCm
    val corners = when (instrument.kind) {
        InstrumentKind.RULER -> {
            val body = InstrumentGeometry.rulerBodyPx()
            listOf(Offset(0f, 0f), Offset(size, 0f), Offset(size, body), Offset(0f, body))
        }

        InstrumentKind.PROTRACTOR -> {
            val r = instrument.protractorRadiusPx
            listOf(Offset(-r, r * PROTRACTOR_BASE), Offset(r, r * PROTRACTOR_BASE), Offset(r, -r), Offset(-r, -r))
        }

        else -> listOf(Offset(0f, 0f), Offset(size, 0f), Offset(0f, size * instrument.kind.setSquareRatio))
    }
    val c = cos(instrument.rotation)
    val s = sin(instrument.rotation)
    val ax = camera.worldToScreenX(instrument.x)
    val ay = camera.worldToScreenY(instrument.y)
    return Offset(
        ax + corners.maxOf { it.x * c - it.y * s } + CONTROL_GAP_PX,
        ay + corners.minOf { it.x * s + it.y * c },
    )
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Box(
        Modifier
            .padding(2.dp)
            .size(dimens.chromeButton)
            .clip(CircleShape)
            .background(Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(dimens.chromeIcon * 0.8f),
        )
    }
}

/** Which part of the instrument a press landed on. */
/**
 * Which part of the instrument a press landed on, or NONE to let the canvas
 * have the touch.
 *
 * Critically, a press NEAR THE RULING EDGE is never claimed: that is exactly
 * where a teacher draws, and an instrument layer that swallowed those touches
 * would make the ruler impossible to rule against — it would only ever slide
 * around.
 */
/** Which instrument a press landed on, and what the drag would do. */
data class InstrumentHit(val id: String, val mode: InstrumentDrag)

/**
 * Resolves a press against every instrument on the board, topmost first.
 *
 * Teachers routinely use two at once — a set square rested against a ruler is
 * the standard way to draw parallels — so the board holds a list and a press
 * has to find the right one. Later-placed instruments win, matching the
 * "last placed is on top" rule used elsewhere on the board.
 *
 * A press NEAR A RULING EDGE is never claimed: that is where a teacher draws,
 * and claiming it would make the instrument impossible to rule against.
 */
internal fun instrumentHitAt(state: BoardState, offset: Offset): InstrumentHit? {
    for (i in state.instruments.indices.reversed()) {
        val mode = dragModeFor(state, state.instruments[i], offset)
        if (mode != InstrumentDrag.NONE) {
            return InstrumentHit(state.instruments[i].id, mode)
        }
    }
    return null
}

internal fun dragModeFor(
    state: BoardState,
    instrument: Instrument,
    offset: Offset,
): InstrumentDrag {
    val camera = state.camera

    // The compass answers for itself, BEFORE the shared pivot test. Its hinge
    // sits exactly where that test looks, so falling through to it reported
    // every hinge press as ROTATE — and a compass that rotates instead of
    // moving cannot be repositioned at all.
    if (instrument.kind == InstrumentKind.COMPASS) {
        return compassDragFor(instrument, camera, offset)
    }

    // The handles win even over the edge: they are the only way to turn or
    // resize, and a handle that drew a line instead would read as broken.
    instrumentHandles(instrument, camera)?.let { h ->
        // Nearest wins: the ruler's buttons are stacked close together, and
        // the protractor has a row of five.
        val nearest = h.all()
            .map { (mode, at) -> mode to hypot(offset.x - at.x, offset.y - at.y) }
            .minBy { it.second }
        if (nearest.second <= HANDLE_TOUCH_PX) return nearest.first
    }

    // The drawing band along the ruling edge belongs to the ink.
    // Likewise the protractor's rim, so an arc can be traced round it.
    val toGuide = InstrumentGeometry.distanceToGuide(
        instrument,
        camera.zoom,
        camera.screenToWorldX(offset.x),
        camera.screenToWorldY(offset.y),
    )
    if (toGuide != null && InstrumentGeometry.shouldSnap(toGuide, camera.zoom)) {
        return InstrumentDrag.NONE
    }

    return if (isOnBody(instrument, camera, offset)) InstrumentDrag.MOVE else InstrumentDrag.NONE
}

/**
 * Which part of the compass a press landed on, as on the reference board:
 * the grip on top and the needle tip move it, the hinge's ↺ turns it about
 * its middle, ↔ on the pencil leg opens the legs, ⇆ on the needle leg swaps
 * needle and pencil, the pencil tip draws, and ✕ puts it away.
 *
 * Nearest part wins, rather than a fixed order: zoomed out the tool is only a
 * few hundred pixels across and touch-sized grabs overlap. A press anywhere
 * else — between the legs above all — belongs to the board, or the inside of
 * every circle a teacher draws would be undrawable.
 */
internal fun compassDragFor(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
    offset: Offset,
): InstrumentDrag {
    val p = compassParts(instrument, camera)
    val nearest = listOf(
        InstrumentDrag.CLOSE to p.close,
        InstrumentDrag.MOVE to p.grip,
        InstrumentDrag.MOVE to p.needle,
        InstrumentDrag.ROTATE to p.hinge,
        InstrumentDrag.SWAP to p.swapButton,
        InstrumentDrag.SPREAD to p.spreadButton,
        InstrumentDrag.SWEEP to p.pencil,
    ).minBy { (_, at) -> hypot(offset.x - at.x, offset.y - at.y) }
    val distance = hypot(offset.x - nearest.second.x, offset.y - nearest.second.y)
    // The hinge is a big disc: its whole face is the grab.
    val reach = if (nearest.first == InstrumentDrag.ROTATE) maxOf(p.hingeRadius, compassGrabRadius(camera))
    else compassGrabRadius(camera)
    if (distance <= reach) return nearest.first

    // Anywhere else along a leg opens or closes it, as a real compass is
    // opened by its legs: the leg held moves, the other tip stays planted.
    val slop = p.legWidth / 2f + LEG_GRAB_SLOP_PX
    fun onLeg(tip: Offset) = com.smartboard.teach.feature.whiteboard.StrokeHitTest
        .pointToSegmentDistanceSquared(offset.x, offset.y, p.hinge.x, p.hinge.y, tip.x, tip.y) <= slop * slop
    return when {
        onLeg(p.pencil) -> InstrumentDrag.SPREAD
        onLeg(p.needle) -> InstrumentDrag.SPREAD_NEEDLE
        else -> InstrumentDrag.NONE
    }
}

/**
 * How far down the leg a press on it landed, as a fraction of the leg, so the
 * spread can keep that point under the finger for the whole drag.
 */
internal fun compassGrabFraction(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
    finger: Offset,
    byNeedleLeg: Boolean,
): Float {
    val p = compassParts(instrument, camera)
    val tip = if (byNeedleLeg) p.needle else p.pencil
    val lx = tip.x - p.hinge.x
    val ly = tip.y - p.hinge.y
    val lenSq = (lx * lx + ly * ly).coerceAtLeast(1f)
    return (((finger.x - p.hinge.x) * lx + (finger.y - p.hinge.y) * ly) / lenSq).coerceIn(MIN_GRAB_ALONG, 1f)
}

/** Where each part of a compass is drawn, in screen px. */
internal data class CompassParts(
    val hinge: Offset,
    val needle: Offset,
    val pencil: Offset,
    val grip: Offset,
    val close: Offset,
    val swapButton: Offset,
    val spreadButton: Offset,
    val hingeRadius: Float,
    val legWidth: Float,
)

internal fun compassParts(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
): CompassParts {
    val hinge = compassHinge(instrument, camera)
    val needle = compassNeedleTip(instrument, camera)
    val pencil = compassPencilTip(instrument, camera)
    val legPx = instrument.compassLegWorld * camera.zoom
    val hingeR = (legPx * 0.15f).coerceIn(34f, 72f)
    // Along the centre line (towards the legs) and across it (to the right
    // when the legs hang down).
    val ax = cos(instrument.rotation)
    val ay = sin(instrument.rotation)
    val px = -ay
    val py = ax
    val grip = Offset(hinge.x - ax * hingeR * 1.75f, hinge.y - ay * hingeR * 1.75f)
    fun along(tip: Offset) = Offset(
        hinge.x + (tip.x - hinge.x) * COMPASS_BUTTON_AT,
        hinge.y + (tip.y - hinge.y) * COMPASS_BUTTON_AT,
    )
    return CompassParts(
        hinge = hinge,
        needle = needle,
        pencil = pencil,
        grip = grip,
        close = Offset(grip.x - px * hingeR * 1.15f - ax * hingeR * 0.3f, grip.y - py * hingeR * 1.15f - ay * hingeR * 0.3f),
        swapButton = along(needle),
        spreadButton = along(pencil),
        hingeRadius = hingeR,
        legWidth = (legPx * 0.06f).coerceIn(14f, 32f),
    )
}

/**
 * The compass turned to [rotation] and opened to [spread] with its needle
 * left where it was — a real compass pivots and opens about its planted
 * needle, not about its hinge.
 */
internal fun compassKeepingNeedle(
    instrument: Instrument,
    rotation: Float,
    spread: Float,
    /** Plant the pencil instead, for a compass opened by its needle leg. */
    keepPencil: Boolean = false,
): Instrument {
    val leg = instrument.compassLegWorld
    val side = (if (instrument.swapped) 1f else -1f) * (if (keepPencil) -1f else 1f)
    val was = instrument.rotation - side * instrument.spreadRad / 2f
    val nx = instrument.x + cos(was) * leg
    val ny = instrument.y + sin(was) * leg
    val now = rotation - side * spread / 2f
    return instrument.copy(
        x = nx - cos(now) * leg,
        y = ny - sin(now) * leg,
        rotation = rotation,
        spreadRad = spread,
    )
}

/**
 * The spread that keeps the held point of a leg under [finger]: [along] is
 * how far down that leg it was grabbed (the ↔ button, or anywhere on the
 * leg). The other tip stays planted, so the held point's distance from it
 * fixes the angle at the hinge by the law of cosines.
 */
internal fun compassSpreadTo(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
    finger: Offset,
    along: Float = COMPASS_BUTTON_AT,
    byNeedleLeg: Boolean = false,
): Instrument {
    val planted = if (byNeedleLeg) compassPencilTip(instrument, camera) else compassNeedleTip(instrument, camera)
    val legPx = instrument.compassLegWorld * camera.zoom
    val d = hypot(finger.x - planted.x, finger.y - planted.y) / legPx
    val f = along.coerceAtLeast(MIN_GRAB_ALONG)
    val cosSpread = ((1f + f * f - d * d) / (2f * f)).coerceIn(-1f, 1f)
    val spread = kotlin.math.acos(cosSpread).coerceIn(MIN_SPREAD_RAD, MAX_SPREAD_RAD)
    // A resized compass starts its arc afresh: the old sweep was at a
    // different radius and would not join up.
    return compassKeepingNeedle(instrument, instrument.rotation, spread, keepPencil = byNeedleLeg)
        .copy(sweepStart = 0f, sweepEnd = 0f)
}

/**
 * Grabs nearer the hinge than this act as if made here: close to the hinge a
 * small move swings the leg a long way, and the spread would jump.
 */
private const val MIN_GRAB_ALONG = 0.3f

/** How far either side of a leg's edge a press still takes hold of it. */
private const val LEG_GRAB_SLOP_PX = 14f

/** How far the legs may open: closed enough to be useless, wide enough to splay. */
internal const val MIN_SPREAD_RAD = 0.12f
internal const val MAX_SPREAD_RAD = 2.4f

/** How far down each leg its button sits, as a fraction of the leg. */
internal const val COMPASS_BUTTON_AT = 0.78f

/**
 * How close a press must be to a compass part to take hold of it.
 *
 * Scales with the tool so the grabs stay proportional as the board zooms, but
 * never drops below a fingertip: zoomed out, a strictly proportional grab
 * would be a few pixels wide and the compass would be untouchable.
 */
internal fun compassGrabRadius(
    camera: com.smartboard.teach.feature.whiteboard.Camera,
): Float {
    val legPx = COMPASS_LEG_CM * InstrumentGeometry.pxPerCm * camera.zoom
    return (legPx * GRAB_FRACTION_OF_LEG).coerceIn(MIN_GRAB_PX, MAX_GRAB_PX)
}

/**
 * The move, rotate and resize handles in the instrument's own unrotated frame,
 * screen px from its anchor; null for the compass, whose legs are its handles.
 *
 * Both sit INSIDE the body and clear of the ruling edge, so neither steals a
 * stroke ruled along it. Their along-the-instrument position is a fixed
 * fraction of its size, which is what lets [resizedLengthCm] invert them.
 */
private fun handleLocals(instrument: Instrument): InstrumentHandles? {
    val size = instrument.lengthCm * InstrumentGeometry.pxPerCm
    return when (instrument.kind) {
        // As on the reference ruler: the grip in the middle, and close,
        // stretch and rotate stacked across the far end.
        InstrumentKind.RULER -> {
            val body = InstrumentGeometry.rulerBodyPx()
            val x = size - RULER_BUTTON_INSET_PX
            InstrumentHandles(
                move = Offset(size / 2f, body / 2f),
                rotate = Offset(x, body * 0.78f),
                resize = Offset(x, body * 0.5f),
                close = Offset(x, body * 0.22f),
            )
        }

        // As on the reference: the grip under the top scale, stretch and
        // rotate in from the sharp tip, close at the other end of the long
        // side — every one clear of both scales.
        InstrumentKind.SET_SQUARE_45, InstrumentKind.SET_SQUARE_30 -> {
            val height = size * instrument.kind.setSquareRatio
            val inset = HANDLE_RADIUS_PX + 12f
            val resizeAlong = clearOfTip(atan2(height, size), inset)
            InstrumentHandles(
                move = Offset(size * 0.36f, setSquareBandPx() + MOVE_GRIP_HEIGHT_PX / 2f + 6f),
                rotate = alongHypotenuse(size, height, resizeAlong + HANDLE_RADIUS_PX * 3f, inset, fromXTip = true),
                resize = alongHypotenuse(size, height, resizeAlong, inset, fromXTip = true),
                close = alongHypotenuse(size, height, clearOfTip(atan2(size, height), inset), inset, fromXTip = false),
            )
        }

        // As on the reference: close, the three draw buttons and rotate in a
        // row along the base strip; the arm's handle out past the rim; the
        // readout in the middle doubles as a grip.
        InstrumentKind.PROTRACTOR -> {
            val r = instrument.protractorRadiusPx
            val y = r * PROTRACTOR_BASE / 2f
            val arm = instrument.armRad
            InstrumentHandles(
                move = Offset(0f, -r * READOUT_AT),
                rotate = Offset(r * 0.86f, y),
                resize = null,
                close = Offset(-r * 0.86f, y),
                extras = mapOf(
                    InstrumentDrag.ARM to Offset(cos(-arm) * r * ARM_REACH, sin(-arm) * r * ARM_REACH),
                    InstrumentDrag.DRAW_ANGLE to Offset(-r * 0.22f, y),
                    InstrumentDrag.DRAW_ARC to Offset(0f, y),
                    InstrumentDrag.DRAW_SECTOR to Offset(r * 0.22f, y),
                ),
            )
        }

        InstrumentKind.COMPASS -> null
    }
}

/** How far a set square's scale reaches in from each leg, in px. */
private fun setSquareTickScale() = (InstrumentGeometry.pxPerCm * 0.42f / TICK_MAJOR_PX).coerceAtLeast(1f)
private fun setSquareBandPx() = TICK_MAJOR_PX * setSquareTickScale() + 34f

/**
 * A point [along] px down the hypotenuse from one of its tips — the x-leg's
 * when [fromXTip], else the y-leg's — then [inset] px in towards the right
 * angle.
 */
private fun alongHypotenuse(size: Float, height: Float, along: Float, inset: Float, fromXTip: Boolean): Offset {
    val hyp = hypot(size, height)
    val d = if (fromXTip) along else -along
    return Offset(
        (if (fromXTip) size else 0f) - size / hyp * d - height / hyp * inset,
        (if (fromXTip) 0f else height) + height / hyp * d - size / hyp * inset,
    )
}

/**
 * How far down the hypotenuse from a tip of angle [tipAngle] a handle inset
 * by [inset] clears the scale on the leg beside it. Sharp tips need further:
 * the 30 degree tip pushes its handles well in.
 */
private fun clearOfTip(tipAngle: Float, inset: Float): Float =
    (HANDLE_RADIUS_PX + setSquareBandPx() + inset * cos(tipAngle)) / sin(tipAngle)

/**
 * The window cut out of a set square: the outline scaled by [CUTOUT_SCALE]
 * about its incentre, so its edges run parallel to the outside ones at an
 * even margin. Returns the inner right-angle corner (both coordinates) and
 * the inner legs.
 */
private fun setSquareCutout(size: Float, height: Float): Triple<Float, Float, Float> {
    val inradius = size * height / (size + height + hypot(size, height))
    return Triple(inradius * (1f - CUTOUT_SCALE), size * CUTOUT_SCALE, height * CUTOUT_SCALE)
}

/** Where an instrument's move, rotate and resize handles sit. */
internal data class InstrumentHandles(
    val move: Offset,
    val rotate: Offset,
    /** Null where the instrument has no stretch (the protractor). */
    val resize: Offset?,
    /** On the instrument itself; null where Close floats beside it. */
    val close: Offset? = null,
    /** Instrument-specific buttons: the protractor's arm and draw buttons. */
    val extras: Map<InstrumentDrag, Offset> = emptyMap(),
) {
    /** Every handle with what pressing it does. */
    fun all(): List<Pair<InstrumentDrag, Offset>> = listOfNotNull(
        InstrumentDrag.MOVE to move,
        InstrumentDrag.ROTATE to rotate,
        resize?.let { InstrumentDrag.RESIZE to it },
        close?.let { InstrumentDrag.CLOSE to it },
    ) + extras.toList()
}

/** Screen positions of the handles (see [handleLocals]). */
internal fun instrumentHandles(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
): InstrumentHandles? {
    val local = handleLocals(instrument) ?: return null
    val ax = camera.worldToScreenX(instrument.x)
    val ay = camera.worldToScreenY(instrument.y)
    val c = cos(instrument.rotation)
    val s = sin(instrument.rotation)
    fun toScreen(p: Offset) = Offset(ax + p.x * c - p.y * s, ay + p.x * s + p.y * c)
    return InstrumentHandles(
        toScreen(local.move),
        toScreen(local.rotate),
        local.resize?.let(::toScreen),
        local.close?.let(::toScreen),
        local.extras.mapValues { toScreen(it.value) },
    )
}

/**
 * The point an instrument turns about, in its own unrotated frame (screen px
 * from its anchor): its middle, so rotating turns it in place like a real
 * ruler spun under a finger, rather than swinging it round a corner. The
 * protractor's anchor already is its centre.
 */
private fun pivotLocal(instrument: Instrument): Offset {
    val size = instrument.lengthCm * InstrumentGeometry.pxPerCm
    return when (instrument.kind) {
        InstrumentKind.RULER -> Offset(size / 2f, InstrumentGeometry.rulerBodyPx() / 2f)
        InstrumentKind.SET_SQUARE_45, InstrumentKind.SET_SQUARE_30 ->
            Offset(size / 3f, size * instrument.kind.setSquareRatio / 3f) // centroid
        else -> Offset.Zero
    }
}

/** Screen position of [pivotLocal]. */
internal fun instrumentPivot(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
): Offset {
    // The compass turns about its middle too: the centre of hinge, needle
    // and pencil. Not the hinge itself — ↺ sits on it, and an angle measured
    // from the point under the finger would spin wildly.
    if (instrument.kind == InstrumentKind.COMPASS) {
        val p = compassParts(instrument, camera)
        return Offset((p.hinge.x + p.needle.x + p.pencil.x) / 3f, (p.hinge.y + p.needle.y + p.pencil.y) / 3f)
    }
    val l = pivotLocal(instrument)
    val c = cos(instrument.rotation)
    val s = sin(instrument.rotation)
    return Offset(
        camera.worldToScreenX(instrument.x) + l.x * c - l.y * s,
        camera.worldToScreenY(instrument.y) + l.x * s + l.y * c,
    )
}

/** The instrument turned to [rotation] with its pivot left where it was. */
internal fun rotatedAboutPivot(
    instrument: Instrument,
    rotation: Float,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
): Instrument {
    if (instrument.kind == InstrumentKind.COMPASS) {
        // Swing the hinge round the middle by the change in turn.
        val pivot = instrumentPivot(instrument, camera)
        val px = camera.screenToWorldX(pivot.x)
        val py = camera.screenToWorldY(pivot.y)
        val delta = rotation - instrument.rotation
        val c = cos(delta)
        val s = sin(delta)
        val dx = instrument.x - px
        val dy = instrument.y - py
        return instrument.copy(rotation = rotation, x = px + dx * c - dy * s, y = py + dx * s + dy * c)
    }
    val pivot = instrumentPivot(instrument, camera)
    val l = pivotLocal(instrument)
    val c = cos(rotation)
    val s = sin(rotation)
    return instrument.copy(
        rotation = rotation,
        x = camera.screenToWorldX(pivot.x - (l.x * c - l.y * s)),
        y = camera.screenToWorldY(pivot.y - (l.x * s + l.y * c)),
    )
}

/**
 * The size that puts the resize handle under [finger] (a screen point).
 *
 * The ruler and set squares stretch along their length; the protractor grows
 * radially. Either way the handle tracks the hand exactly, because its
 * position is proportional to the size.
 */
internal fun resizedLengthCm(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
    finger: Offset,
): Float {
    val handle = handleLocals(instrument)?.resize ?: return instrument.lengthCm
    val dx = finger.x - camera.worldToScreenX(instrument.x)
    val dy = finger.y - camera.worldToScreenY(instrument.y)
    val c = cos(-instrument.rotation)
    val s = sin(-instrument.rotation)
    val lx = dx * c - dy * s
    val ly = dx * s + dy * c
    if (instrument.kind.hasRulingEdge) {
        // The handle sits a fixed distance in from the far tip rather than at
        // a fraction of the length, so the length is where the finger is plus
        // that distance.
        val inFromEnd = instrument.lengthCm * InstrumentGeometry.pxPerCm - handle.x
        return ((lx + inFromEnd) / InstrumentGeometry.pxPerCm).coerceIn(MIN_LENGTH_CM, MAX_LENGTH_CM)
    }
    val ratio = if (instrument.kind == InstrumentKind.PROTRACTOR) {
        (lx * handle.x + ly * handle.y) / (handle.x * handle.x + handle.y * handle.y)
    } else {
        lx / handle.x
    }
    return (instrument.lengthCm * ratio).coerceIn(MIN_LENGTH_CM, MAX_LENGTH_CM)
}

/** Screen position of the hinge the legs hang from. */
internal fun compassHinge(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
): Offset = Offset(
    camera.worldToScreenX(instrument.x),
    camera.worldToScreenY(instrument.y),
)

/**
 * Screen position of the needle tip — the centre of the circle.
 *
 * The needle stays put while the pencil sweeps, which is what makes the arc a
 * circle rather than a smear.
 */
internal fun compassNeedleTip(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
): Offset {
    val hinge = compassHinge(instrument, camera)
    val legPx = instrument.compassLegWorld * camera.zoom
    val side = if (instrument.swapped) 1f else -1f
    val angle = instrument.rotation - side * instrument.spreadRad / 2f
    return Offset(hinge.x + cos(angle) * legPx, hinge.y + sin(angle) * legPx)
}

/** Screen position of the pencil tip — where the arc is drawn. */
internal fun compassPencilTip(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
): Offset {
    val hinge = compassHinge(instrument, camera)
    val legPx = instrument.compassLegWorld * camera.zoom
    val side = if (instrument.swapped) 1f else -1f
    val angle = instrument.rotation + side * instrument.spreadRad / 2f
    return Offset(hinge.x + cos(angle) * legPx, hinge.y + sin(angle) * legPx)
}

/** Is this screen point over the instrument's painted area? */
internal fun isOnBody(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
    offset: Offset,
): Boolean {
    val ax = camera.worldToScreenX(instrument.x)
    val ay = camera.worldToScreenY(instrument.y)
    val dx = offset.x - ax
    val dy = offset.y - ay
    // Into the instrument's own frame, so a rotated ruler still tests as a box.
    val c = cos(-instrument.rotation)
    val sn = sin(-instrument.rotation)
    val localX = dx * c - dy * sn
    val localY = dx * sn + dy * c

    return when (instrument.kind) {
        InstrumentKind.RULER -> {
            val length = instrument.lengthCm * InstrumentGeometry.pxPerCm
            val body = InstrumentGeometry.rulerBodyPx()
            localX in 0f..length && localY in 0f..body
        }

        InstrumentKind.SET_SQUARE_45, InstrumentKind.SET_SQUARE_30 -> {
            val base = instrument.lengthCm * InstrumentGeometry.pxPerCm
            val height = base * instrument.kind.setSquareRatio
            // Inside the right triangle: both legs positive and under the
            // hypotenuse, expressed as the intercept form of that edge.
            // The window is a hole: ink can be drawn inside it, as through
            // the real one.
            val (corner, innerBase, innerHeight) = setSquareCutout(base, height)
            val inWindow = localX >= corner && localY >= corner &&
                (localX - corner) / innerBase + (localY - corner) / innerHeight <= 1f
            localX >= 0f && localY >= 0f &&
                (localX / base + localY / height) <= 1f && !inWindow
        }

        // The half disc and the straight strip under it.
        InstrumentKind.PROTRACTOR -> {
            val radius = instrument.protractorRadiusPx
            (localY <= 0f && hypot(localX, localY) <= radius) ||
                (abs(localX) <= radius && localY in 0f..radius * PROTRACTOR_BASE)
        }

        InstrumentKind.COMPASS -> true
    }
}

// --- drawing --------------------------------------------------------------

/**
 * A handle: a filled accent badge with an upright icon saying what it does.
 *
 * Upright rather than turning with the instrument, so the rotate arrow still
 * reads as "rotate" when the ruler is standing on end.
 */
private fun DrawScope.drawHandle(center: Offset, icon: VectorPainter) {
    drawCircle(color = Color.White, radius = HANDLE_RADIUS_PX + 2.5f, center = center)
    drawCircle(color = Accent, radius = HANDLE_RADIUS_PX, center = center)
    val iconPx = HANDLE_RADIUS_PX * 1.15f
    translate(center.x - iconPx / 2f, center.y - iconPx / 2f) {
        with(icon) {
            draw(Size(iconPx, iconPx), colorFilter = ColorFilter.tint(Color.White))
        }
    }
}

/** The instrument's angle, on a dark pill just above the rotate handle. */
private fun DrawScope.drawAngleLabel(rotateHandle: Offset, degrees: Int, measurer: TextMeasurer) {
    val layout = measurer.measure(
        "$degrees°",
        TextStyle(fontSize = 13.sp, color = Color.White),
    )
    val padX = 10f
    val padY = 4f
    val w = layout.size.width + padX * 2
    val h = layout.size.height + padY * 2
    val topLeft = Offset(rotateHandle.x - w / 2f, rotateHandle.y - HANDLE_RADIUS_PX - 8f - h)
    drawRoundRect(
        color = HINGE_FILL,
        topLeft = topLeft,
        size = Size(w, h),
        cornerRadius = CornerRadius(h / 2f),
    )
    drawText(layout, topLeft = Offset(topLeft.x + padX, topLeft.y + padY))
}

/** Drawn radius of a handle; its touch target is [HANDLE_TOUCH_PX]. */
private const val HANDLE_RADIUS_PX = 26f
private const val HANDLE_TOUCH_PX = 46f


/**
 * The ruler, as on the reference panel: a pale see-through strip, centimetres
 * along one long edge and inches (numbered in red) along the other, both
 * edges ruling. The scales stop short of the button column at the far end,
 * as a real ruler's markings stop short of its end.
 */
private fun DrawScope.drawRuler(
    instrument: Instrument,
    sx: Float,
    sy: Float,
    measurer: TextMeasurer,
) {
    val lengthPx = instrument.lengthCm * InstrumentGeometry.pxPerCm
    val bodyPx = InstrumentGeometry.rulerBodyPx()
    val scalePx = lengthPx - RULER_SCALE_START_PX - RULER_BUTTON_INSET_PX * 2f + HANDLE_RADIUS_PX
    val corner = CornerRadius(6f)
    // Bold, long ticks as on the reference — readable from the back row.
    val tickScale = (bodyPx * 0.17f / TICK_MAJOR_PX).coerceAtLeast(1f)

    translate(sx, sy) {
        rotateRad(instrument.rotation, pivot = Offset.Zero) {
            // Translucent, so ink and grid underneath stay visible — the whole
            // point of a transparent classroom ruler.
            drawRoundRect(RULER_FILL, Offset.Zero, Size(lengthPx, bodyPx), corner)
            drawRoundRect(RULER_EDGE, Offset.Zero, Size(lengthPx, bodyPx), corner, style = Stroke(width = 1.5f))

            // Centimetres and millimetres down from the near edge...
            drawScale(
                lengthPx = scalePx,
                perCm = InstrumentGeometry.MM_PER_CM,
                fromY = 0f,
                downwards = true,
                measurer = measurer,
                startX = RULER_SCALE_START_PX,
                unitLabel = "cm",
                labelColor = TICK_COLOR,
                tickScale = tickScale,
            )
            // ...and eighths of an inch up from the far one. Spaced by inch
            // rather than by cm — a per-cm count drew ticks that never met
            // their numbers.
            drawScale(
                lengthPx = scalePx,
                perCm = InstrumentGeometry.MM_PER_CM,
                fromY = bodyPx,
                downwards = false,
                measurer = measurer,
                unitPx = InstrumentGeometry.pxPerCm * 2.54f,
                divisions = 8,
                startX = RULER_SCALE_START_PX,
                unitLabel = "inch",
                labelColor = INCH_RED,
                tickScale = tickScale,
            )
        }
    }
}

/** The ruler's move grip: a white pill across its middle, turning with it. */
private fun DrawScope.drawMoveGrip(instrument: Instrument, center: Offset) {
    val lengthPx = instrument.lengthCm * InstrumentGeometry.pxPerCm
    val w = minOf(MOVE_GRIP_WIDTH_PX, lengthPx * 0.22f)
    val h = MOVE_GRIP_HEIGHT_PX
    translate(center.x, center.y) {
        rotateRad(instrument.rotation, pivot = Offset.Zero) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.78f),
                topLeft = Offset(-w / 2f, -h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f),
            )
        }
    }
}

/** Ticks and numbers along one side of a scale. */
private fun DrawScope.drawScale(
    lengthPx: Float,
    perCm: Int,
    fromY: Float,
    downwards: Boolean,
    measurer: TextMeasurer,
    /** Length of one labelled unit (1cm, or 1 inch for the imperial side). */
    unitPx: Float = InstrumentGeometry.pxPerCm,
    /** Ticks per unit. */
    divisions: Int = perCm,
    /** Where 0 sits along the edge. */
    startX: Float = 0f,
    /** Written after the 0, as "0cm" / "0inch" on the reference ruler. */
    unitLabel: String = "",
    labelColor: Color = TICK_COLOR,
    /** Multiplies tick length and weight: the ruler's ticks scale to its width. */
    tickScale: Float = 1f,
    /** False leaves 0 unnumbered, where a second scale starts at the same corner. */
    labelZero: Boolean = true,
) {
    val step = unitPx / divisions
    if (step < MIN_TICK_SPACING_PX) return

    val direction = if (downwards) 1f else -1f
    var i = 0
    var atX = startX
    while (atX <= startX + lengthPx) {
        // Every unit and half-unit tick is longer, the way a scale is read.
        val major = i % divisions == 0
        val medium = i % (divisions / 2).coerceAtLeast(1) == 0
        val tick = tickScale * when {
            major -> TICK_MAJOR_PX
            medium -> TICK_MEDIUM_PX
            else -> TICK_MINOR_PX
        }
        drawLine(
            color = TICK_COLOR,
            start = Offset(atX, fromY),
            end = Offset(atX, fromY + tick * direction),
            strokeWidth = tickScale * if (major) 1.6f else 1f,
        )

        if (major && (i != 0 || labelZero)) {
            // From the tick count, not atX / unitPx: float drift made that
            // truncate 2 to 1 and print 0 1 1 2 3 5.
            val value = i / divisions
            val text = if (value == 0) "0$unitLabel" else "$value"
            val layout = measurer.measure(text, TextStyle(fontSize = 12.sp, color = labelColor))
            // Centred on the tick, except 0, which reads from the tick on.
            val x = if (value == 0) atX - 2f else atX - layout.size.width / 2f
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x,
                    fromY + (if (downwards) tick + 2f else -tick - layout.size.height - 2f),
                ),
            )
        }
        atX += step
        i++
    }
}

/**
 * A set square, as on the reference board: the ruler's pale body, a cm scale
 * down each leg from the right angle, and a triangular window in the middle.
 * Each scale stops where it would run into the hypotenuse.
 */
private fun DrawScope.drawSetSquare(
    instrument: Instrument,
    sx: Float,
    sy: Float,
    measurer: TextMeasurer,
) {
    val leg = instrument.lengthCm * InstrumentGeometry.pxPerCm
    val height = leg * instrument.kind.setSquareRatio
    val band = setSquareBandPx()
    val tickScale = setSquareTickScale()
    translate(sx, sy) {
        rotateRad(instrument.rotation, pivot = Offset.Zero) {
            val outline = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(leg, 0f)
                lineTo(0f, height)
                close()
            }
            drawPath(outline, RULER_FILL)
            drawPath(outline, RULER_EDGE, style = Stroke(width = 1.5f))

            val (corner, innerLeg, innerHeight) = setSquareCutout(leg, height)
            val window = androidx.compose.ui.graphics.Path().apply {
                moveTo(corner, corner)
                lineTo(corner + innerLeg, corner)
                lineTo(corner, corner + innerHeight)
                close()
            }
            drawPath(window, CUTOUT_EDGE, style = Stroke(width = 3f))

            // Along the top leg, from the right angle...
            drawScale(
                lengthPx = leg * (1f - band / height) - RULER_SCALE_START_PX - 8f,
                perCm = InstrumentGeometry.MM_PER_CM,
                fromY = 0f,
                downwards = true,
                measurer = measurer,
                startX = RULER_SCALE_START_PX,
                unitLabel = "cm",
                tickScale = tickScale,
            )
            // ...and down the side one, drawn as the same scale turned a
            // quarter, its ticks pointing in. Its 0 is the top scale's 0.
            rotateRad((Math.PI / 2).toFloat(), pivot = Offset.Zero) {
                drawScale(
                    lengthPx = height * (1f - band / leg) - RULER_SCALE_START_PX - 8f,
                    perCm = InstrumentGeometry.MM_PER_CM,
                    fromY = 0f,
                    downwards = false,
                    measurer = measurer,
                    startX = RULER_SCALE_START_PX,
                    tickScale = tickScale,
                    labelZero = false,
                )
            }
        }
    }
}

/**
 * The protractor, as on the reference board: a half disc on a straight strip,
 * 1-degree ticks with blue tens, two scales (0 to 180 round the outside, 180
 * to 0 inside) on rings, a red 90 at the top, and the measuring arm swinging
 * from the centre over a fixed zero arm, its angle in the big readout.
 */
private fun DrawScope.drawProtractor(
    instrument: Instrument,
    sx: Float,
    sy: Float,
    measurer: TextMeasurer,
) {
    val r = instrument.protractorRadiusPx
    val labelStyle = TextStyle(fontSize = (r * 0.05f).toSp(), color = TICK_COLOR)
    translate(sx, sy) {
        rotateRad(instrument.rotation, pivot = Offset.Zero) {
            val body = androidx.compose.ui.graphics.Path().apply {
                moveTo(-r, 0f)
                arcTo(androidx.compose.ui.geometry.Rect(-r, -r, r, r), 180f, 180f, false)
                lineTo(r, r * PROTRACTOR_BASE)
                lineTo(-r, r * PROTRACTOR_BASE)
                close()
            }
            drawPath(body, RULER_FILL)
            drawPath(body, RULER_EDGE, style = Stroke(width = 1.5f))

            fun label(text: String, radius: Float, a: Double, deg: Int) {
                val layout = measurer.measure(text, labelStyle)
                val cx = (cos(a) * radius).toFloat()
                val cy = (sin(a) * radius).toFloat()
                // Turned to face the centre, as printed on a real protractor.
                rotate(deg - 90f, pivot = Offset(cx, cy)) {
                    drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
                }
            }

            for (deg in 0..180) {
                val a = Math.toRadians(180.0 + deg)
                val ten = deg % 10 == 0
                val len = r * when {
                    ten -> 0.075f
                    deg % 5 == 0 -> 0.05f
                    else -> 0.03f
                }
                drawLine(
                    color = if (ten) TICK_BLUE else TICK_COLOR,
                    start = Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat()),
                    end = Offset((cos(a) * (r - len)).toFloat(), (sin(a) * (r - len)).toFloat()),
                    strokeWidth = if (ten) 2.5f else 1f,
                )
                // 90 gets its own red badge below.
                if (ten && deg != 90) {
                    label("$deg", r * OUTER_LABEL, a, deg)
                    label("${180 - deg}", r * INNER_LABEL, a, deg)
                }
            }

            fun ring(fraction: Float) = drawArc(
                color = TICK_DARK,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(-r * fraction, -r * fraction),
                size = Size(r * fraction * 2f, r * fraction * 2f),
                style = Stroke(width = 2f),
            )
            ring(OUTER_RING)
            ring(INNER_RING)
            // The centre mark, the 90 line, and the left half of the base
            // line — the right half is the blue zero arm.
            ring(0.1f)
            drawLine(TICK_DARK, Offset(0f, 0f), Offset(0f, -r * OUTER_RING), strokeWidth = 1f)
            drawLine(TICK_DARK, Offset(-r * INNER_RING, 0f), Offset(0f, 0f), strokeWidth = 2f)

            val top = Offset(0f, -r * OUTER_RING)
            drawCircle(Color.White.copy(alpha = 0.88f), r * 0.07f, top)
            val ninety = measurer.measure("90", labelStyle.copy(color = INCH_RED, fontSize = (r * 0.07f).toSp()))
            drawText(ninety, topLeft = Offset(top.x - ninety.size.width / 2f, top.y - ninety.size.height / 2f))

            // The zero arm, fixed along the base line, and the measuring arm.
            drawLine(Accent, Offset.Zero, Offset(r, 0f), strokeWidth = 4f, cap = StrokeCap.Round)
            val arm = instrument.armRad
            drawLine(
                Accent,
                Offset.Zero,
                Offset(cos(-arm) * r * ARM_REACH, sin(-arm) * r * ARM_REACH),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )

            // The reading, on a blue disc. Upright whatever the protractor's
            // turn, so it can be read from the class.
            val readout = Offset(0f, -r * READOUT_AT)
            drawCircle(Accent, r * 0.12f, readout)
            val degrees = Math.round(Math.toDegrees(arm.toDouble()))
            val text = measurer.measure(
                "$degrees°",
                TextStyle(fontSize = (r * 0.085f).toSp(), color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            )
            rotateRad(-instrument.rotation, pivot = readout) {
                drawText(text, topLeft = Offset(readout.x - text.size.width / 2f, readout.y - text.size.height / 2f))
            }
        }
    }
}

/** The painters the compass draws its buttons with. */
internal data class CompassIcons(
    val rotate: VectorPainter,
    val swap: VectorPainter,
    val spread: VectorPainter,
    val close: VectorPainter,
)

/**
 * A two-legged compass, as on the reference board: dark legs with a blue
 * needle and a blue pencil, a hinge disc carrying ↺, a grip on top, ✕ beside
 * it, a button on each leg, and a cm ruler from needle to pencil reading off
 * the radius. While the pencil sweeps, the whole circle shows dashed, with a
 * dashed line to where the arc began and the angle swept so far.
 */
private fun DrawScope.drawCompass(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
    measurer: TextMeasurer,
    icons: CompassIcons,
    ink: Color,
    inkWidth: Float,
    sweeping: Boolean,
) {
    val p = compassParts(instrument, camera)
    val radius = instrument.compassRadius * camera.zoom
    val needle = p.needle
    val pencil = p.pencil

    if (sweeping || instrument.hasSweep) {
        val dashes = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))
        drawCircle(Color.White.copy(alpha = 0.55f), radius, needle, style = Stroke(width = 4f, pathEffect = dashes))
        val start = Offset(needle.x + cos(instrument.sweepStart) * radius, needle.y + sin(instrument.sweepStart) * radius)
        drawLine(Color.White.copy(alpha = 0.6f), needle, start, strokeWidth = 2f, pathEffect = dashes)
        if (instrument.hasSweep) {
            val from = minOf(instrument.sweepStart, instrument.sweepEnd)
            val sweep = abs(instrument.sweepEnd - instrument.sweepStart)
            drawArc(
                color = ink,
                startAngle = Math.toDegrees(from.toDouble()).toFloat(),
                sweepAngle = Math.toDegrees(sweep.toDouble()).toFloat(),
                useCenter = false,
                topLeft = Offset(needle.x - radius, needle.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = inkWidth.coerceAtLeast(2f), cap = StrokeCap.Round),
            )
        }
    }

    drawRadiusRuler(p, measurer, instrument, sweeping)

    // Legs: dark bars outlined in white, stopping short of their points.
    val nib = (instrument.compassLegWorld * camera.zoom) * 0.1f
    fun leg(tip: Offset): Offset {
        val dx = tip.x - p.hinge.x
        val dy = tip.y - p.hinge.y
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val end = Offset(tip.x - dx / len * nib, tip.y - dy / len * nib)
        drawLine(Color.White, p.hinge, end, strokeWidth = p.legWidth + 4f)
        drawLine(HINGE_FILL, p.hinge, end, strokeWidth = p.legWidth)
        return end
    }
    val needleEnd = leg(needle)
    val pencilEnd = leg(pencil)

    // The needle: a thin blue spike down to the point.
    drawSpike(needleEnd, needle, p.legWidth * 0.32f, Accent)

    // The pencil: a blue pencil strapped to the outside of its leg, its lead
    // on the point. Angled away from the needle leg, as on the reference.
    run {
        val ux = (pencil.x - p.hinge.x)
        val uy = (pencil.y - p.hinge.y)
        val len = hypot(ux, uy).coerceAtLeast(1f)
        val out = if (instrument.swapped) -0.22f else 0.22f
        val c = cos(out)
        val sn = sin(out)
        // Back up the leg, turned slightly outward.
        val bx = -(ux * c - uy * sn) / len
        val by = -(ux * sn + uy * c) / len
        val bodyLen = nib * 3.2f
        val lead = Offset(pencil.x + bx * nib * 0.9f, pencil.y + by * nib * 0.9f)
        val back = Offset(pencil.x + bx * bodyLen, pencil.y + by * bodyLen)
        drawLine(Accent, lead, back, strokeWidth = p.legWidth * 0.7f)
        drawSpike(lead, pencil, p.legWidth * 0.35f, Accent)
        drawCircle(Accent.copy(alpha = 0.35f), p.legWidth * 0.45f, pencil)
    }

    // The grip on top, along the centre line.
    translate(p.grip.x, p.grip.y) {
        rotateRad(instrument.rotation, pivot = Offset.Zero) {
            val l = p.hingeRadius * 1.15f
            val w = p.hingeRadius * 0.62f
            drawRoundRect(HINGE_FILL, Offset(-l / 2f, -w / 2f), Size(l, w), CornerRadius(w * 0.22f))
            drawRoundRect(Color.White, Offset(-l / 2f, -w / 2f), Size(l, w), CornerRadius(w * 0.22f), style = Stroke(width = 2.5f))
        }
    }

    // The hinge disc, with ↺ on its inner ring.
    drawCircle(HINGE_FILL, p.hingeRadius, p.hinge)
    drawCircle(Color.White, p.hingeRadius, p.hinge, style = Stroke(width = 3f))
    drawCircle(Color.White, p.hingeRadius * 0.64f, p.hinge, style = Stroke(width = 2.5f))
    drawIcon(icons.rotate, p.hinge, p.hingeRadius * 0.75f)

    // A button on each leg.
    val buttonR = (p.legWidth * 1.3f).coerceAtLeast(22f)
    listOf(p.swapButton to icons.swap, p.spreadButton to icons.spread).forEach { (at, icon) ->
        drawCircle(HINGE_FILL, buttonR, at)
        drawCircle(Color.White, buttonR, at, style = Stroke(width = 2.5f))
        drawIcon(icon, at, buttonR * 1.15f)
    }

    // Put away.
    val closeR = (p.hingeRadius * 0.36f).coerceAtLeast(20f)
    drawCircle(CLOSE_PINK, closeR, p.close)
    drawIcon(icons.close, p.close, closeR * 1.1f)
}

/** A narrow triangle from [base] to its point at [tip]. */
private fun DrawScope.drawSpike(base: Offset, tip: Offset, halfWidth: Float, color: Color) {
    val dx = tip.x - base.x
    val dy = tip.y - base.y
    val len = hypot(dx, dy).coerceAtLeast(1f)
    val nx = -dy / len * halfWidth
    val ny = dx / len * halfWidth
    drawPath(
        androidx.compose.ui.graphics.Path().apply {
            moveTo(base.x + nx, base.y + ny)
            lineTo(base.x - nx, base.y - ny)
            lineTo(tip.x, tip.y)
            close()
        },
        color,
    )
}

/** An upright white icon of [size] centred on [at]. */
private fun DrawScope.drawIcon(icon: VectorPainter, at: Offset, size: Float) {
    translate(at.x - size / 2f, at.y - size / 2f) {
        with(icon) { draw(Size(size, size), colorFilter = ColorFilter.tint(Color.White)) }
    }
}

/**
 * A cm ruler from the needle to the pencil, ticks on the side away from the
 * hinge, so the radius is read straight off the tool. While sweeping, the
 * angle drawn so far sits above its far end.
 */
private fun DrawScope.drawRadiusRuler(
    p: CompassParts,
    measurer: TextMeasurer,
    instrument: Instrument,
    sweeping: Boolean,
) {
    val dx = p.pencil.x - p.needle.x
    val dy = p.pencil.y - p.needle.y
    val len = hypot(dx, dy)
    if (len < 4f) return
    val ux = dx / len
    val uy = dy / len
    // The normal pointing away from the hinge.
    var nx = -uy
    var ny = ux
    if ((p.hinge.x - p.needle.x) * nx + (p.hinge.y - p.needle.y) * ny > 0f) { nx = -nx; ny = -ny }

    drawLine(Color.White, p.needle, p.pencil, strokeWidth = 2f)
    val mm = InstrumentGeometry.pxPerCm / 10f
    if (mm >= MIN_TICK_SPACING_PX) {
        val style = TextStyle(fontSize = 13.sp, color = Color.White)
        var i = 0
        var at = 0f
        while (at <= len + 0.5f) {
            val major = i % 10 == 0
            val tick = if (major) 16f else if (i % 5 == 0) 11f else 6f
            val x = p.needle.x + ux * at
            val y = p.needle.y + uy * at
            drawLine(Color.White, Offset(x, y), Offset(x + nx * tick, y + ny * tick), strokeWidth = if (major) 2f else 1f)
            if (major) {
                val layout = measurer.measure("${i / 10}", style)
                drawText(
                    layout,
                    topLeft = Offset(
                        x + nx * (tick + 14f) - layout.size.width / 2f,
                        y + ny * (tick + 14f) - layout.size.height / 2f,
                    ),
                )
            }
            at += mm
            i++
        }
    }

    if (sweeping && instrument.hasSweep) {
        val degrees = Math.round(Math.toDegrees(abs(instrument.sweepEnd - instrument.sweepStart).toDouble()))
        val layout = measurer.measure("$degrees°", TextStyle(fontSize = 18.sp, color = Color.White))
        val at = Offset(p.pencil.x - ux * 60f - nx * 26f, p.pencil.y - uy * 60f - ny * 26f)
        drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
    }
}

/** What a drag on the instrument is currently doing. */
enum class InstrumentDrag {
    NONE,
    MOVE,
    ROTATE,

    /** Compass: opening or closing the legs, which sets the radius. */
    SPREAD,

    /** Compass: sweeping the pencil leg round to draw an arc. */
    SWEEP,

    /** Ruler, set square, protractor: stretching it with the resize handle. */
    RESIZE,

    /** The ruler's own close button: a press puts it away. */
    CLOSE,

    /** Compass ⇆: needle and pencil change legs. */
    SWAP,

    /** Compass: opening the legs by the needle leg, the pencil planted. */
    SPREAD_NEEDLE,

    /** Protractor: swinging the measuring arm round its centre. */
    ARM,

    /** Protractor buttons: put the measured angle on the board as ink. */
    DRAW_ANGLE,
    DRAW_ARC,
    DRAW_SECTOR,
}

private val BODY_FILL = Color(0x2233608F)
private val BODY_EDGE = Color(0x8833608F)
private val TICK_COLOR = Color(0xCC26384B)
private val LEG_COLOR = Color(0xE53F4A57)
private val HINGE_FILL = Color(0xF2333C47)
private val CLOSE_PINK = Color(0xFFE6959E)

// See-through enough that ink drawn with the instrument shows beneath it.
private val RULER_FILL = Color(0x8CDCEAF3)
private val RULER_EDGE = Color(0xFFB4C8D6)
private val INCH_RED = Color(0xFFD04A4A)
private val TICK_DARK = Color(0xFF26323C)
private val TICK_BLUE = Color(0xFF4DA3E0)

/** Protractor proportions, as fractions of its radius. */
private const val PROTRACTOR_BASE = 0.32f
private const val OUTER_RING = 0.80f
private const val INNER_RING = 0.66f
private const val OUTER_LABEL = 0.87f
private const val INNER_LABEL = 0.73f
private const val READOUT_AT = 0.42f
private const val ARM_REACH = 1.18f
private val CUTOUT_EDGE = Color(0xFF2B3640)

/** The set square's window, as a fraction of its outline. */
private const val CUTOUT_SCALE = 0.48f

/** How far in from the far end the ruler's button column sits. */
private const val RULER_BUTTON_INSET_PX = 40f

/** Where the ruler's 0 sits, in from its near end. */
private const val RULER_SCALE_START_PX = 14f

private const val MOVE_GRIP_WIDTH_PX = 150f
private const val MOVE_GRIP_HEIGHT_PX = 34f
private const val TICK_MAJOR_PX = 16f
private const val TICK_MEDIUM_PX = 11f
private const val TICK_MINOR_PX = 6f
private const val MIN_TICK_SPACING_PX = 3f

/** A grab this size relative to the legs keeps the three parts distinct. */
private const val GRAB_FRACTION_OF_LEG = 0.14f

/** Never smaller than a fingertip, never so large the parts merge. */
private const val MIN_GRAB_PX = 40f
private const val MAX_GRAB_PX = 64f


private const val CONTROL_GAP_PX = 12f
