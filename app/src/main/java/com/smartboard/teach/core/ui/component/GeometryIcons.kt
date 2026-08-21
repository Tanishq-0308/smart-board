package com.smartboard.teach.core.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The geometry instruments, drawn to match the reference panel.
 *
 * Material's nearest equivalents are actively misleading here — `Straighten`
 * is a plain double-headed arrow, `Architecture` is a drafting triangle with
 * no scale, and `RadioButtonUnchecked` is a circle. A teacher picking an
 * instrument recognises it by silhouette, so each one is drawn as the tool it
 * actually is: graduated ruler, two distinct set squares, a ticked protractor
 * and a hinged compass.
 */
private fun instrument(
    name: String,
    build: ImageVector.Builder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply(build).build()

private const val LINE = 1.5f

/** A diagonal graduated strip, running lower-left to upper-right. */
val RulerIcon: ImageVector = instrument("Ruler") {
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = LINE,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        // The body: a long thin rectangle at 45 degrees.
        moveTo(3.2f, 17.4f)
        lineTo(17.4f, 3.2f)
        lineTo(20.8f, 6.6f)
        lineTo(6.6f, 20.8f)
        close()
    }
    // Graduations along the lower edge, alternating long and short.
    path(stroke = SolidColor(Color.White), strokeLineWidth = 1.1f, strokeLineCap = StrokeCap.Round) {
        moveTo(6.0f, 14.6f); lineTo(8.1f, 16.7f)
        moveTo(8.1f, 12.5f); lineTo(9.5f, 13.9f)
        moveTo(10.2f, 10.4f); lineTo(12.3f, 12.5f)
        moveTo(12.3f, 8.3f); lineTo(13.7f, 9.7f)
        moveTo(14.4f, 6.2f); lineTo(16.5f, 8.3f)
    }
}

/**
 * The 45-degree set square: an isoceles right triangle.
 *
 * Right angle at the bottom-left, as it is held.
 */
val SetSquare45Icon: ImageVector = instrument("SetSquare45") {
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = LINE,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(4f, 20f)
        lineTo(4f, 5f)
        lineTo(19f, 20f)
        close()
    }
    // Inner cut-out, the way a real set square is moulded.
    path(stroke = SolidColor(Color.White), strokeLineWidth = 1.1f, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7f, 16.6f)
        lineTo(7f, 11.5f)
        lineTo(12.1f, 16.6f)
        close()
    }
}

/**
 * The 30/60-degree set square: a scalene right triangle.
 *
 * Wider and shallower than the 45, which is how the two are told apart at a
 * glance — the whole reason the reference panel offers both.
 */
val SetSquare30Icon: ImageVector = instrument("SetSquare30") {
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = LINE,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(3f, 19.5f)
        lineTo(3f, 8.5f)
        lineTo(21f, 19.5f)
        close()
    }
    path(stroke = SolidColor(Color.White), strokeLineWidth = 1.1f, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6f, 16.8f)
        lineTo(6f, 12.6f)
        lineTo(12.8f, 16.8f)
        close()
    }
}

/** A semicircle with radial ticks around the arc. */
val ProtractorIcon: ImageVector = instrument("Protractor") {
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = LINE,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        // Flat base plus the arc, drawn as two quarter curves.
        moveTo(2.5f, 18f)
        lineTo(21.5f, 18f)
        moveTo(2.5f, 18f)
        curveTo(2.5f, 12.8f, 6.8f, 8.5f, 12f, 8.5f)
        curveTo(17.2f, 8.5f, 21.5f, 12.8f, 21.5f, 18f)
    }
    // Radial graduations just inside the arc.
    path(stroke = SolidColor(Color.White), strokeLineWidth = 1f, strokeLineCap = StrokeCap.Round) {
        moveTo(4.6f, 18f); lineTo(6.2f, 18f)
        moveTo(5.4f, 14.6f); lineTo(6.8f, 15.4f)
        moveTo(7.8f, 12.0f); lineTo(8.8f, 13.2f)
        moveTo(11.0f, 10.6f); lineTo(11.4f, 12.1f)
        moveTo(13.0f, 10.6f); lineTo(12.6f, 12.1f)
        moveTo(16.2f, 12.0f); lineTo(15.2f, 13.2f)
        moveTo(18.6f, 14.6f); lineTo(17.2f, 15.4f)
        moveTo(19.4f, 18f); lineTo(17.8f, 18f)
    }
}

/** Two splayed legs with a hinge knob on top. */
val CompassIcon: ImageVector = instrument("Compass") {
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = LINE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        // Legs, meeting at the hinge.
        moveTo(12f, 7.5f)
        lineTo(6f, 20.5f)
        moveTo(12f, 7.5f)
        lineTo(18f, 20.5f)
        // The handle above the hinge.
        moveTo(12f, 4.6f)
        lineTo(12f, 2.6f)
    }
    // Hinge knob.
    path(stroke = SolidColor(Color.White), strokeLineWidth = LINE) {
        moveTo(14.4f, 6.1f)
        curveTo(14.4f, 7.4f, 13.3f, 8.5f, 12f, 8.5f)
        curveTo(10.7f, 8.5f, 9.6f, 7.4f, 9.6f, 6.1f)
        curveTo(9.6f, 4.8f, 10.7f, 3.7f, 12f, 3.7f)
        curveTo(13.3f, 3.7f, 14.4f, 4.8f, 14.4f, 6.1f)
        close()
    }
    path(fill = SolidColor(Color.White)) {
        moveTo(12.7f, 6.1f)
        curveTo(12.7f, 6.5f, 12.4f, 6.8f, 12f, 6.8f)
        curveTo(11.6f, 6.8f, 11.3f, 6.5f, 11.3f, 6.1f)
        curveTo(11.3f, 5.7f, 11.6f, 5.4f, 12f, 5.4f)
        curveTo(12.4f, 5.4f, 12.7f, 5.7f, 12.7f, 6.1f)
        close()
    }
}
