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
                    InstrumentKind.COMPASS -> drawCompass(instrument, sx, sy, zoom)
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
            state.replaceInstrument(instrument.copy(rotation = 0f))
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

    InstrumentKind.COMPASS -> Offset(
        camera.worldToScreenX(instrument.x) + instrument.radiusWorld * camera.zoom +
            CONTROL_GAP_PX,
        camera.worldToScreenY(instrument.y) - instrument.radiusWorld * camera.zoom,
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
    val ax = camera.worldToScreenX(instrument.x)
    val ay = camera.worldToScreenY(instrument.y)
    val distance = hypot(offset.x - ax, offset.y - ay)

    // The pivot turns the instrument, and wins even over the edge.
    if (distance < ANCHOR_RADIUS_PX) return InstrumentDrag.ROTATE

    if (instrument.kind == InstrumentKind.COMPASS) {
        // Only near its own circle, or a compass would swallow the board.
        val r = instrument.radiusWorld * camera.zoom
        return if (kotlin.math.abs(distance - r) < ANCHOR_RADIUS_PX) {
            InstrumentDrag.RADIUS
        } else {
            InstrumentDrag.NONE
        }
    }

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

private fun DrawScope.drawCompass(
    instrument: Instrument,
    sx: Float,
    sy: Float,
    zoom: Float,
) {
    val radius = instrument.radiusWorld * zoom
    // The circle the compass would draw, previewed faintly so a teacher can
    // size it before committing ink.
    drawCircle(
        color = Accent.copy(alpha = 0.35f),
        radius = radius,
        center = Offset(sx, sy),
        style = Stroke(width = 2f),
    )
    // Pivot point.
    drawCircle(color = Accent, radius = 7f, center = Offset(sx, sy))
    // The arm, from pivot out to the pencil.
    val armX = sx + cos(instrument.rotation) * radius
    val armY = sy + sin(instrument.rotation) * radius
    drawLine(
        color = ARM_COLOR,
        start = Offset(sx, sy),
        end = Offset(armX, armY),
        strokeWidth = 6f,
        cap = StrokeCap.Round,
    )
    drawCircle(color = Accent, radius = 9f, center = Offset(armX, armY))
}

/** What a drag on the instrument is currently doing. */
enum class InstrumentDrag { NONE, MOVE, ROTATE, RADIUS }

private val BODY_FILL = Color(0x2233608F)
private val BODY_EDGE = Color(0x8833608F)
private val TICK_COLOR = Color(0xCC26384B)
private val ARM_COLOR = Color(0xCC4A5A6B)

private const val RULER_BODY_CM = 2.2f
private const val PROTRACTOR_RADIUS_CM = 4f
private const val TICK_MAJOR_PX = 16f
private const val TICK_MEDIUM_PX = 11f
private const val TICK_MINOR_PX = 6f
private const val MIN_TICK_SPACING_PX = 3f
private const val ANCHOR_RADIUS_PX = 40f
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
        InstrumentKind.COMPASS -> instrument.radiusWorld * camera.zoom
    } + ANCHOR_RADIUS_PX
    return GrabBox(sx - reach, sy - reach, reach * 2f, reach * 2f)
}
