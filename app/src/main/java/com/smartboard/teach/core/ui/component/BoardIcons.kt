package com.smartboard.teach.core.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Board tool icons drawn as vectors.
 *
 * Material ships neither a pen-nib stroke nor an angled eraser block, and the
 * nearest stand-ins are actively misleading: Delete (a waste bin) reads as
 * "throw the board away" rather than "rub something out", which is the wrong
 * message on the one control a teacher uses to correct a mistake mid-lesson.
 */
private fun outline(
    name: String,
    widthPx: Float = 2f,
    build: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply(build).build()

/** A diagonal nib stroke, as on the reference bar's first button. */
val BoardPenIcon: ImageVector = outline("BoardPen") {
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        // Shaft, running corner to corner.
        moveTo(19.5f, 4.5f)
        lineTo(8.5f, 15.5f)
        // Nib: a slim wedge at the low end.
        moveTo(8.5f, 15.5f)
        lineTo(5.2f, 18.8f)
        moveTo(5.2f, 18.8f)
        lineTo(4.2f, 19.8f)
    }
    path(
        fill = SolidColor(Color.White),
    ) {
        // Solid tip, so the pen reads as a pen rather than a plain line.
        moveTo(4.2f, 19.8f)
        lineTo(6.4f, 19.1f)
        lineTo(4.9f, 17.6f)
        close()
    }
}

/** An angled eraser block with a wipe trail, as on the reference bar. */
val BoardEraserIcon: ImageVector = outline("BoardEraser") {
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        // The block, a parallelogram tilted the way a held eraser sits.
        moveTo(10.5f, 18.5f)
        lineTo(20.0f, 9.0f)
        lineTo(16.0f, 5.0f)
        lineTo(6.5f, 14.5f)
        close()
        // Split line where the rubber meets the sleeve.
        moveTo(9.0f, 12.0f)
        lineTo(13.0f, 16.0f)
        // Wipe trail: two short strokes trailing off to the left.
        moveTo(8.0f, 20.0f)
        lineTo(4.0f, 20.0f)
        moveTo(6.0f, 16.5f)
        lineTo(3.0f, 16.5f)
    }
}

/**
 * One silhouette per nib.
 *
 * Five identical pen glyphs told a teacher nothing about which nib they were
 * choosing — the whole point of the column is to distinguish them, so each
 * gets a tip shaped like the mark it makes.
 */
private fun nib(
    name: String,
    tip: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit,
): ImageVector = outline(name) {
    // Shared barrel, so the five read as a family.
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.6f,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(9f, 4f)
        lineTo(15f, 4f)
        lineTo(15f, 13f)
        lineTo(9f, 13f)
        close()
    }
    tip()
}

/** Fine round point. */
val NibPenIcon: ImageVector = nib("NibPen") {
    path(fill = SolidColor(Color.White)) {
        moveTo(12f, 21f); lineTo(10.6f, 13f); lineTo(13.4f, 13f); close()
    }
}

/** Broad chisel, cut at an angle. */
val NibMarkerIcon: ImageVector = nib("NibMarker") {
    path(fill = SolidColor(Color.White)) {
        moveTo(9f, 13f); lineTo(15f, 13f); lineTo(15f, 19f); lineTo(9f, 16f); close()
    }
}

/** Wide flat edge — the highlighter lays down a band, not a line. */
val NibHighlighterIcon: ImageVector = nib("NibHighlighter") {
    path(fill = SolidColor(Color.White)) {
        moveTo(8f, 13f); lineTo(16f, 13f); lineTo(16f, 20f); lineTo(8f, 20f); close()
    }
}

/**
 * The text pen: a fine nib with an "A" beside it.
 *
 * Deliberately NOT just another tip shape. The other five differ only in how
 * ink is laid down; this one changes what the ink BECOMES, and a teacher has
 * to be able to tell that apart at a glance.
 */
val NibTextIcon: ImageVector = nib("NibText") {
    // Fine tip, like the pen.
    path(fill = SolidColor(Color.White)) {
        moveTo(12f, 21f); lineTo(10.8f, 14f); lineTo(13.2f, 14f); close()
    }
    // A small "A" to the right of the barrel.
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineJoin = StrokeJoin.Round,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(17.2f, 10f)
        lineTo(19.4f, 4.6f)
        lineTo(21.6f, 10f)
        moveTo(18f, 8f)
        lineTo(20.8f, 8f)
    }
}

/** Split fountain nib with its slit. */
val NibFountainIcon: ImageVector = nib("NibFountain") {
    path(fill = SolidColor(Color.White)) {
        moveTo(12f, 21.5f); lineTo(9.5f, 13f); lineTo(14.5f, 13f); close()
    }
    path(stroke = SolidColor(Color(0xFF1C2530)), strokeLineWidth = 1f) {
        moveTo(12f, 15f); lineTo(12f, 19.5f)
    }
}

/** Soft rounded bristle head. */
val NibBrushIcon: ImageVector = nib("NibBrush") {
    path(fill = SolidColor(Color.White)) {
        moveTo(9f, 13f)
        lineTo(15f, 13f)
        curveTo(15f, 18f, 13.5f, 21f, 12f, 21f)
        curveTo(10.5f, 21f, 9f, 18f, 9f, 13f)
        close()
    }
}
