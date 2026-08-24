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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    InstrumentKind.RULER -> drawRuler(instrument, sx, sy, zoom, measurer)
                    InstrumentKind.SET_SQUARE_45, InstrumentKind.SET_SQUARE_30 ->
                        drawSetSquare(instrument, sx, sy, zoom, measurer)
                    InstrumentKind.PROTRACTOR -> drawProtractor(instrument, sx, sy, zoom, measurer)
                    InstrumentKind.COMPASS -> drawCompass(instrument, camera)
                }
                // The compass already turns by its own arm; every other
                // instrument needs somewhere visible to grab.
                if (instrument.kind != InstrumentKind.COMPASS) {
                    drawPivot(sx, sy)
                }
            }
        }

        // Each instrument carries its own controls, so two on the board at
        // once can be put away or reset independently.
        state.instruments.forEach { instrument ->
            InstrumentControls(state, instrument)
        }
    }
}

/**
 * Close / flip / reset, beside the instrument they belong to.
 *
 * Flip is offered only where it means something: a ruler or set square can be
 * mirrored to rule from the other side, but a protractor and compass are
 * symmetric and the control would do nothing.
 */
@Composable
private fun InstrumentControls(state: BoardState, instrument: Instrument) {
    val dimens = SmartBoardTheme.dimens
    val camera = state.camera
    val anchor = controlAnchor(instrument, camera)

    Column(
        Modifier
            .offset { IntOffset(anchor.x.toInt(), anchor.y.toInt()) }
            .padding(dimens.gutterSmall),
    ) {
        ControlButton(Icons.Filled.Close, "Put away") {
            state.instruments.removeAll { it.id == instrument.id }
        }
        if (instrument.kind.hasRulingEdge) {
            ControlButton(Icons.Filled.SwapHoriz, "Flip") {
                state.replaceInstrument(instrument.copy(flipped = !instrument.flipped))
            }
        }
        ControlButton(Icons.Filled.Refresh, "Reset angle") {
            // A compass's rest angle is hanging down, not lying flat, and
            // resetting also drops a part-drawn arc that no longer lines up.
            state.replaceInstrument(
                if (instrument.kind == InstrumentKind.COMPASS) {
                    instrument.copy(
                        rotation = (Math.PI / 2).toFloat(),
                        sweepStart = 0f,
                        sweepEnd = 0f,
                    )
                } else {
                    instrument.copy(rotation = 0f)
                },
            )
        }
    }
}

/** Where an instrument's controls sit: clear of its own outline. */
private fun controlAnchor(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
): Offset = when (instrument.kind) {
    // Beyond the far end of the ruling edge.
    InstrumentKind.RULER, InstrumentKind.SET_SQUARE_45, InstrumentKind.SET_SQUARE_30 -> {
        val edge = InstrumentGeometry.edgeOf(instrument, camera.zoom)
        Offset(
            camera.worldToScreenX(maxOf(edge[0], edge[2])) + CONTROL_GAP_PX,
            camera.worldToScreenY(minOf(edge[1], edge[3])),
        )
    }

    InstrumentKind.PROTRACTOR -> Offset(
        camera.worldToScreenX(instrument.x) +
            PROTRACTOR_RADIUS_CM * InstrumentGeometry.pxPerCm + CONTROL_GAP_PX,
        camera.worldToScreenY(instrument.y) - PROTRACTOR_RADIUS_CM * InstrumentGeometry.pxPerCm,
    )

    // Clear to the RIGHT of the hinge and level with it. Stacking them above
    // meant guessing the button column's height, and the guess put the bottom
    // button straight on top of the hinge — which is the grab that moves the
    // whole compass, so it could not be moved at all.
    // BEHIND the hinge, opposite the legs, and following them as the compass
    // turns. The buttons are a Compose layer above the canvas, so wherever
    // they sit is a press the canvas never sees: parked at a fixed side they
    // eventually covered the hinge or a leg, and whichever they covered
    // stopped responding. Behind the hinge is the one direction the tool
    // never occupies.
    InstrumentKind.COMPASS -> {
        val back = instrument.rotation + PI_F
        val reach = compassGrabRadius(camera) + COMPASS_CONTROL_CLEARANCE_PX
        Offset(
            camera.worldToScreenX(instrument.x) + cos(back) * reach,
            camera.worldToScreenY(instrument.y) + sin(back) * reach - HINGE_DRAW_PX,
        )
    }
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

    val ax = camera.worldToScreenX(instrument.x)
    val ay = camera.worldToScreenY(instrument.y)
    val distance = hypot(offset.x - ax, offset.y - ay)

    // The pivot turns the instrument, and wins even over the edge.
    if (distance < ANCHOR_RADIUS_PX) return InstrumentDrag.ROTATE

    // The drawing band along the ruling edge belongs to the ink.
    if (instrument.kind.hasRulingEdge) {
        val worldX = camera.screenToWorldX(offset.x)
        val worldY = camera.screenToWorldY(offset.y)
        val edge = InstrumentGeometry.edgeOf(instrument, camera.zoom)
        val toEdge = InstrumentGeometry.distanceToEdge(edge, worldX, worldY)
        if (InstrumentGeometry.shouldSnap(toEdge, camera.zoom)) return InstrumentDrag.NONE
    }

    return if (isOnBody(instrument, camera, offset)) InstrumentDrag.MOVE else InstrumentDrag.NONE
}

/**
 * Which part of the compass a press landed on.
 *
 * The tool has three grabs and they must not overlap: the pencil leg sweeps
 * the arc, the needle leg opens the spread, and the hinge moves the whole
 * thing. A press anywhere else is not the compass's — the legs are thin, and
 * claiming the space between them would make the board undrawable inside
 * every circle a teacher draws.
 */
internal fun compassDragFor(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
    offset: Offset,
): InstrumentDrag {
    val grab = compassGrabRadius(camera)

    val hinge = compassHinge(instrument, camera)
    val pencil = compassPencilTip(instrument, camera)
    val needle = compassNeedleTip(instrument, camera)

    val toHinge = hypot(offset.x - hinge.x, offset.y - hinge.y)
    val toPencil = hypot(offset.x - pencil.x, offset.y - pencil.y)
    val toNeedle = hypot(offset.x - needle.x, offset.y - needle.y)

    val nearest = minOf(toHinge, toPencil, toNeedle)
    if (nearest > grab) return InstrumentDrag.NONE

    // NEAREST part wins, rather than a fixed order. Zoomed out the tool is
    // only a few hundred pixels across and a touch-sized grab on each part
    // makes all three overlap; testing in a fixed order then hands every
    // press to whichever happens to be checked first, and the parts behind it
    // become unreachable.
    return when (nearest) {
        toHinge -> InstrumentDrag.MOVE
        toPencil -> InstrumentDrag.SWEEP
        else -> InstrumentDrag.SPREAD
    }
}

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
    val legPx = COMPASS_LEG_CM * InstrumentGeometry.pxPerCm * camera.zoom
    val angle = instrument.rotation - instrument.spreadRad / 2f
    return Offset(hinge.x + cos(angle) * legPx, hinge.y + sin(angle) * legPx)
}

/** Screen position of the pencil tip — where the arc is drawn. */
internal fun compassPencilTip(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
): Offset {
    val hinge = compassHinge(instrument, camera)
    val legPx = COMPASS_LEG_CM * InstrumentGeometry.pxPerCm * camera.zoom
    val angle = instrument.rotation + instrument.spreadRad / 2f
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
            val body = RULER_BODY_CM * InstrumentGeometry.pxPerCm
            localX in 0f..length && localY in 0f..body
        }

        InstrumentKind.SET_SQUARE_45, InstrumentKind.SET_SQUARE_30 -> {
            val base = instrument.lengthCm * InstrumentGeometry.pxPerCm
            val height = base * instrument.kind.setSquareRatio
            // Inside the right triangle: both legs positive and under the
            // hypotenuse, expressed as the intercept form of that edge.
            localX >= 0f && localY >= 0f &&
                (localX / base + localY / height) <= 1f
        }

        InstrumentKind.PROTRACTOR -> {
            val radius = PROTRACTOR_RADIUS_CM * InstrumentGeometry.pxPerCm
            localY <= 0f && hypot(localX, localY) <= radius
        }

        InstrumentKind.COMPASS -> true
    }
}

// --- drawing --------------------------------------------------------------

/**
 * The rotation grip, drawn at the instrument's pivot.
 *
 * Without it the pivot is a 40px circle of nothing: the instrument does turn
 * when dragged there, but there is no way to know that, so it reads as an
 * instrument that cannot be rotated at all.
 */
private fun DrawScope.drawPivot(sx: Float, sy: Float) {
    // Filled so it reads as a grip rather than a decorative ring, and ringed
    // in white so it stays visible against ink drawn underneath.
    drawCircle(color = Color.White, radius = PIVOT_RADIUS_PX, center = Offset(sx, sy))
    drawCircle(
        color = Accent,
        radius = PIVOT_RADIUS_PX,
        center = Offset(sx, sy),
        style = Stroke(width = 2.5f),
    )
    drawCircle(color = Accent, radius = PIVOT_RADIUS_PX * 0.45f, center = Offset(sx, sy))
}

/** Drawn size of the pivot. Smaller than its touch target, which is fine —
 *  a grip that filled its whole 40px slop would cover the ruler's zero mark. */
private const val PIVOT_RADIUS_PX = 11f


private fun DrawScope.drawRuler(
    instrument: Instrument,
    sx: Float,
    sy: Float,
    zoom: Float,
    measurer: TextMeasurer,
) {
    val lengthPx = instrument.lengthCm * InstrumentGeometry.pxPerCm
    val bodyPx = RULER_BODY_CM * InstrumentGeometry.pxPerCm

    translate(sx, sy) {
        rotateRad(instrument.rotation, pivot = Offset.Zero) {
            // Translucent body, so ink underneath stays visible — the whole
            // point of a transparent classroom ruler.
            drawRect(
                color = BODY_FILL,
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(lengthPx, bodyPx),
            )
            drawRect(
                color = BODY_EDGE,
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(lengthPx, bodyPx),
                style = Stroke(width = 1.5f),
            )

            // The snapping edge is drawn heavier and in the accent colour, so
            // it is obvious WHICH side rules a line.
            drawLine(
                color = Accent,
                start = Offset(0f, 0f),
                end = Offset(lengthPx, 0f),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )

            // Metric down from the ruling edge...
            drawScale(
                lengthPx = lengthPx,
                perCm = InstrumentGeometry.MM_PER_CM,
                fromY = 0f,
                downwards = true,
                measurer = measurer,
            )
            // ...and imperial up from the far side, as on the reference ruler.
            // Eighths of an inch, spaced by inch rather than by cm — passing a
            // per-cm count here drew ticks that never met the numbers.
            drawScale(
                lengthPx = lengthPx,
                perCm = InstrumentGeometry.MM_PER_CM,
                fromY = bodyPx,
                downwards = false,
                measurer = measurer,
                unitPx = InstrumentGeometry.pxPerCm * 2.54f,
                divisions = 8,
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
) {
    val step = unitPx / divisions
    if (step < MIN_TICK_SPACING_PX) return

    val direction = if (downwards) 1f else -1f
    var i = 0
    var atX = 0f
    while (atX <= lengthPx) {
        // Every unit and half-unit tick is longer, the way a scale is read.
        val major = i % divisions == 0
        val medium = i % (divisions / 2).coerceAtLeast(1) == 0
        val tick = when {
            major -> TICK_MAJOR_PX
            medium -> TICK_MEDIUM_PX
            else -> TICK_MINOR_PX
        }
        drawLine(
            color = TICK_COLOR,
            start = Offset(atX, fromY),
            end = Offset(atX, fromY + tick * direction),
            strokeWidth = if (major) 1.6f else 1f,
        )

        if (major) {
            val value = (atX / unitPx).toInt()
            val text = "$value"
            val layout = measurer.measure(text, TextStyle(fontSize = 9.sp, color = TICK_COLOR))
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    atX + 2f,
                    fromY + (if (downwards) tick + 1f else -tick - layout.size.height - 1f),
                ),
            )
        }
        atX += step
        i++
    }
}

private fun DrawScope.drawSetSquare(
    instrument: Instrument,
    sx: Float,
    sy: Float,
    zoom: Float,
    measurer: TextMeasurer,
) {
    val leg = instrument.lengthCm * InstrumentGeometry.pxPerCm
    translate(sx, sy) {
        rotateRad(instrument.rotation, pivot = Offset.Zero) {
            val height = leg * instrument.kind.setSquareRatio
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(leg, 0f)
                lineTo(0f, height)
                close()
            }
            drawPath(path, BODY_FILL)
            drawPath(path, BODY_EDGE, style = Stroke(width = 1.5f))
            // The hypotenuse is the ruling edge on a set square.
            drawLine(
                color = Accent,
                start = Offset(leg, 0f),
                end = Offset(0f, height),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
            drawScale(leg, InstrumentGeometry.MM_PER_CM, 0f, true, measurer)
        }
    }
}

private fun DrawScope.drawProtractor(
    instrument: Instrument,
    sx: Float,
    sy: Float,
    zoom: Float,
    measurer: TextMeasurer,
) {
    val radius = PROTRACTOR_RADIUS_CM * InstrumentGeometry.pxPerCm
    translate(sx, sy) {
        rotateRad(instrument.rotation, pivot = Offset.Zero) {
            drawArc(
                color = BODY_FILL,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(-radius, -radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            )
            drawArc(
                color = BODY_EDGE,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(-radius, -radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 1.5f),
            )

            // Degree ticks every 1 degree, numbered every 10.
            for (deg in 0..180) {
                val a = Math.toRadians((180 + deg).toDouble())
                val outer = radius
                val inner = radius - when {
                    deg % 10 == 0 -> TICK_MAJOR_PX
                    deg % 5 == 0 -> TICK_MEDIUM_PX
                    else -> TICK_MINOR_PX
                }
                drawLine(
                    color = TICK_COLOR,
                    start = Offset(
                        (cos(a) * inner).toFloat(),
                        (sin(a) * inner).toFloat(),
                    ),
                    end = Offset(
                        (cos(a) * outer).toFloat(),
                        (sin(a) * outer).toFloat(),
                    ),
                    strokeWidth = if (deg % 10 == 0) 1.6f else 1f,
                )
                if (deg % 30 == 0) {
                    val layout = measurer.measure(
                        "$deg",
                        TextStyle(fontSize = 9.sp, color = TICK_COLOR),
                    )
                    val labelR = radius - TICK_MAJOR_PX - 12f
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            (cos(a) * labelR).toFloat() - layout.size.width / 2f,
                            (sin(a) * labelR).toFloat() - layout.size.height / 2f,
                        ),
                    )
                }
            }
            drawLine(
                color = TICK_COLOR,
                start = Offset(-radius, 0f),
                end = Offset(radius, 0f),
                strokeWidth = 1.5f,
            )
        }
    }
}

/**
 * A two-legged compass, drawn as the physical tool.
 *
 * A hinge with a needle leg and a pencil leg, matching the instrument a
 * teacher already knows how to use: the needle stays on the centre, the
 * pencil sweeps the arc, and how far the legs are opened IS the radius. The
 * earlier version drew a ring with one arm, which told a class nothing about
 * how a circle is actually constructed.
 */
private fun DrawScope.drawCompass(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
) {
    val hinge = compassHinge(instrument, camera)
    val needle = compassNeedleTip(instrument, camera)
    val pencil = compassPencilTip(instrument, camera)
    val radius = instrument.compassRadius * camera.zoom

    // The circle this spread would draw, so the size is clear BEFORE any ink
    // is committed — the whole reason to preview rather than just draw.
    drawCircle(
        color = Accent.copy(alpha = 0.25f),
        radius = radius,
        center = needle,
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f))),
    )

    // The arc swept so far, solid, so a part-drawn circle reads as progress
    // rather than as a broken preview.
    if (instrument.hasSweep) {
        val from = minOf(instrument.sweepStart, instrument.sweepEnd)
        val sweep = abs(instrument.sweepEnd - instrument.sweepStart)
        drawArc(
            color = Accent,
            startAngle = Math.toDegrees(from.toDouble()).toFloat(),
            sweepAngle = Math.toDegrees(sweep.toDouble()).toFloat(),
            useCenter = false,
            topLeft = Offset(needle.x - radius, needle.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
    }

    drawCompassLeg(hinge, needle, isPencil = false)
    drawCompassLeg(hinge, pencil, isPencil = true)

    // The hinge sits on top of both legs, as it does on the real tool.
    drawCircle(color = HINGE_FILL, radius = HINGE_DRAW_PX, center = hinge)
    drawCircle(
        color = Color.White,
        radius = HINGE_DRAW_PX * 0.34f,
        center = hinge,
        style = Stroke(width = 2.5f),
    )
}

/**
 * One leg: a tapered shaft ending in a needle point or a pencil nib.
 *
 * The two are drawn differently on purpose — which leg is which decides where
 * the circle's centre is, and a teacher has to be able to tell at a glance.
 */
private fun DrawScope.drawCompassLeg(hinge: Offset, tip: Offset, isPencil: Boolean) {
    drawLine(
        color = LEG_COLOR,
        start = hinge,
        end = tip,
        strokeWidth = LEG_WIDTH_PX,
        cap = StrokeCap.Round,
    )

    // Direction along the leg, for the last stretch down to the point.
    val dx = tip.x - hinge.x
    val dy = tip.y - hinge.y
    val length = hypot(dx, dy).coerceAtLeast(1f)
    val ux = dx / length
    val uy = dy / length
    val nibStart = Offset(tip.x - ux * NIB_LENGTH_PX, tip.y - uy * NIB_LENGTH_PX)

    drawLine(
        color = if (isPencil) Accent else NEEDLE_COLOR,
        start = nibStart,
        end = tip,
        strokeWidth = if (isPencil) LEG_WIDTH_PX * 0.75f else LEG_WIDTH_PX * 0.45f,
        cap = StrokeCap.Round,
    )

    // The grab badge, so both legs read as handles rather than decoration.
    // Centred ON the tip, which is where the grab is. Offset back along the
    // leg, the badge advertised a target 34px from the one that actually
    // responded.
    val badge = Offset(tip.x, tip.y)
    drawCircle(color = HINGE_FILL, radius = BADGE_RADIUS_PX, center = badge)
    drawCircle(
        color = if (isPencil) Accent else Color.White,
        radius = BADGE_RADIUS_PX * 0.38f,
        center = badge,
    )
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
}

private val BODY_FILL = Color(0x2233608F)
private val BODY_EDGE = Color(0x8833608F)
private val TICK_COLOR = Color(0xCC26384B)
private val ARM_COLOR = Color(0xCC4A5A6B)
private val LEG_COLOR = Color(0xE53F4A57)
private val HINGE_FILL = Color(0xF2333C47)
private val NEEDLE_COLOR = Color(0xFFE8ECF1)

private const val RULER_BODY_CM = 2.2f
private const val PROTRACTOR_RADIUS_CM = 4f
private const val TICK_MAJOR_PX = 16f
private const val TICK_MEDIUM_PX = 11f
private const val TICK_MINOR_PX = 6f
private const val MIN_TICK_SPACING_PX = 3f
private const val ANCHOR_RADIUS_PX = 40f

/** A grab this size relative to the legs keeps the three parts distinct. */
private const val GRAB_FRACTION_OF_LEG = 0.14f

/** Never smaller than a fingertip, never so large the parts merge. */
private const val MIN_GRAB_PX = 40f
private const val MAX_GRAB_PX = 64f

/** Gap between the hinge's grab and the control column behind it. */
private const val COMPASS_CONTROL_CLEARANCE_PX = 34f

private const val PI_F = Math.PI.toFloat()

private const val HINGE_DRAW_PX = 19f
private const val LEG_WIDTH_PX = 13f
private const val NIB_LENGTH_PX = 26f
private const val BADGE_RADIUS_PX = 11f

private const val CONTROL_GAP_PX = 12f

/** Screen rect covering an instrument's grabbable body. */
private data class GrabBox(
    val left: Float,
    val top: Float,
    val widthDp: Float,
    val heightDp: Float,
)

/**
 * Where the instrument accepts drags.
 *
 * Deliberately generous and axis-aligned — a rotated ruler's box covers more
 * than the ruler itself, and [dragModeFor] does the precise test inside it.
 * What matters is that the box does NOT cover the whole screen.
 */
private fun grabBoxFor(
    instrument: Instrument,
    camera: com.smartboard.teach.feature.whiteboard.Camera,
): GrabBox {
    val sx = camera.worldToScreenX(instrument.x)
    val sy = camera.worldToScreenY(instrument.y)
    val reach = when (instrument.kind) {
        InstrumentKind.RULER, InstrumentKind.SET_SQUARE_45, InstrumentKind.SET_SQUARE_30 ->
            instrument.lengthCm * InstrumentGeometry.pxPerCm
        InstrumentKind.PROTRACTOR -> PROTRACTOR_RADIUS_CM * InstrumentGeometry.pxPerCm
        // The legs, not the circle: a nearly-closed compass draws a tiny
        // circle but its legs still reach their full length.
        InstrumentKind.COMPASS -> COMPASS_LEG_CM * InstrumentGeometry.pxPerCm * camera.zoom
    } + ANCHOR_RADIUS_PX
    return GrabBox(sx - reach, sy - reach, reach * 2f, reach * 2f)
}
