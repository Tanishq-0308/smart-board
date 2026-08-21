package com.smartboard.teach.feature.whiteboard

import android.graphics.Canvas
import android.graphics.Paint
import com.smartboard.teach.domain.model.BoardCanvasStyle
import com.smartboard.teach.domain.model.GridStyle
import com.smartboard.teach.domain.model.defaultGridColor
import com.smartboard.teach.domain.model.gridSpacingForZoom
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Draws the page's grid, in world space, straight to the screen canvas.
 *
 * Deliberately OUTSIDE the renderer's viewport cache. The grid is cheap — a
 * screenful of lines — and regenerating it per frame costs far less than
 * invalidating and re-rasterizing the whole committed-stroke bitmap every time
 * the board moves. It also means changing grid style needs no cache rebuild
 * at all.
 *
 * Only the visible world rect is walked, so an infinite canvas costs the same
 * as one screen.
 */
class GridPainter {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * [worldBounds] is (left, top, right, bottom); the caller has already
     * applied the camera transform to [canvas].
     */
    fun draw(
        canvas: Canvas,
        style: BoardCanvasStyle,
        worldBounds: FloatArray,
        zoom: Float,
    ) {
        if (!style.grid.isVisible) return

        val spacing = gridSpacingForZoom(style.spacingWorld, zoom)
        if (spacing <= 0f) return

        val color = style.gridColorArgb ?: defaultGridColor(style.colorArgb)
        paint.color = color
        dotPaint.color = color

        // Line width in WORLD units so it renders one hairline on screen at
        // any zoom — a fixed world width would thicken as you zoom in until
        // the grid competed with the ink.
        val hairline = HAIRLINE_PX / zoom.coerceAtLeast(0.001f)
        paint.strokeWidth = hairline

        val left = worldBounds[0]
        val top = worldBounds[1]
        val right = worldBounds[2]
        val bottom = worldBounds[3]

        // Snapped to the lattice, not to the viewport, so lines stay on the
        // same world coordinates as the board is panned.
        val firstX = floor(left / spacing) * spacing
        val firstY = floor(top / spacing) * spacing
        val stepsX = ceil((right - firstX) / spacing).toInt().coerceAtMost(MAX_STEPS)
        val stepsY = ceil((bottom - firstY) / spacing).toInt().coerceAtMost(MAX_STEPS)

        when (style.grid) {
            GridStyle.LINED -> {
                // Horizontal rules only: this is writing paper.
                for (i in 0..stepsY) {
                    val y = firstY + i * spacing
                    canvas.drawLine(left, y, right, y, paint)
                }
            }

            GridStyle.DOTTED -> {
                val radius = hairline * DOT_RADIUS_FACTOR
                for (i in 0..stepsX) {
                    val x = firstX + i * spacing
                    for (j in 0..stepsY) {
                        canvas.drawCircle(x, firstY + j * spacing, radius, dotPaint)
                    }
                }
            }

            GridStyle.MIX -> {
                // Fine squares with a heavier line every fifth, so a teacher
                // can count squares without losing their place.
                drawSquares(canvas, left, top, right, bottom, firstX, firstY, spacing, stepsX, stepsY)
                paint.strokeWidth = hairline * MAJOR_WIDTH_FACTOR
                val majorSpacing = spacing * MAJOR_EVERY
                val majorX = floor(left / majorSpacing) * majorSpacing
                val majorY = floor(top / majorSpacing) * majorSpacing
                var i = 0
                while (majorX + i * majorSpacing <= right && i <= MAX_STEPS) {
                    val x = majorX + i * majorSpacing
                    canvas.drawLine(x, top, x, bottom, paint)
                    i++
                }
                var j = 0
                while (majorY + j * majorSpacing <= bottom && j <= MAX_STEPS) {
                    val y = majorY + j * majorSpacing
                    canvas.drawLine(left, y, right, y, paint)
                    j++
                }
                paint.strokeWidth = hairline
            }

            GridStyle.RANGOLI -> {
                drawSquares(canvas, left, top, right, bottom, firstX, firstY, spacing, stepsX, stepsY)
                // Diagonals BOTH ways across every square, the rangoli lattice
                // used to teach symmetry and pattern.
                for (i in 0..stepsX) {
                    val x = firstX + i * spacing
                    for (j in 0..stepsY) {
                        val y = firstY + j * spacing
                        canvas.drawLine(x, y, x + spacing, y + spacing, paint)
                        canvas.drawLine(x + spacing, y, x, y + spacing, paint)
                    }
                }
            }

            GridStyle.SQUARE -> {
                paint.strokeWidth = hairline * MAJOR_WIDTH_FACTOR
                drawSquares(canvas, left, top, right, bottom, firstX, firstY, spacing, stepsX, stepsY)
                paint.strokeWidth = hairline
            }

            GridStyle.THIN -> {
                drawSquares(canvas, left, top, right, bottom, firstX, firstY, spacing, stepsX, stepsY)
            }

            GridStyle.NONE -> Unit
        }
    }

    private fun drawSquares(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        firstX: Float,
        firstY: Float,
        spacing: Float,
        stepsX: Int,
        stepsY: Int,
    ) {
        for (i in 0..stepsX) {
            val x = firstX + i * spacing
            canvas.drawLine(x, top, x, bottom, paint)
        }
        for (j in 0..stepsY) {
            val y = firstY + j * spacing
            canvas.drawLine(left, y, right, y, paint)
        }
    }

    private companion object {
        /** One screen pixel, converted to world by the caller's zoom. */
        const val HAIRLINE_PX = 1f

        /** Heavier lines read as major without becoming ink. */
        const val MAJOR_WIDTH_FACTOR = 2f
        const val MAJOR_EVERY = 5

        const val DOT_RADIUS_FACTOR = 1.6f

        /**
         * Hard ceiling on lines per axis.
         *
         * gridSpacingForZoom already keeps the count sane; this is a backstop
         * so a corrupt spacing can never lock the UI thread in a draw call.
         */
        const val MAX_STEPS = 400
    }
}
