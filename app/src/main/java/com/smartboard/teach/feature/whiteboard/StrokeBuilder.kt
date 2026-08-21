package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import java.util.UUID

/**
 * Accumulates points for the stroke currently under the pen.
 *
 * Points land in a growable FloatArray rather than a List<Offset> so that a
 * long stroke costs one buffer instead of hundreds of boxed objects. The
 * buffer doubles on demand, so a fast scribble does not allocate per sample.
 */
class StrokeBuilder(
    private val tool: DrawTool,
    private val style: StrokeStyle,
    /** Container cell the pen went down in, resolved once at press. */
    private val containerId: String? = null,
    private val cellIndex: Int = -1,
) {
    private var buffer = FloatArray(INITIAL_CAPACITY * Stroke.STRIDE)
    private var count = 0

    // Smoothed positions carried between samples for the EMA.
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var hasPoints = false

    val pointCount: Int get() = count

    val isEmpty: Boolean get() = count == 0

    fun lastX(): Float = if (count == 0) 0f else buffer[(count - 1) * Stroke.STRIDE]
    fun lastY(): Float = if (count == 0) 0f else buffer[(count - 1) * Stroke.STRIDE + 1]

    /**
     * Offers a raw sample.
     *
     * @return true if the point was kept (the caller should extend the drawn
     *         path), false if it was filtered out as too close to the last one.
     */
    fun addPoint(x: Float, y: Float, pressure: Float, applySmoothing: Boolean = true): Boolean {
        if (!hasPoints) {
            smoothedX = x
            smoothedY = y
            hasPoints = true
            append(x, y, pressure)
            return true
        }

        val targetX: Float
        val targetY: Float
        if (applySmoothing) {
            smoothedX = InkSmoothing.smooth(smoothedX, x)
            smoothedY = InkSmoothing.smooth(smoothedY, y)
            targetX = smoothedX
            targetY = smoothedY
        } else {
            targetX = x
            targetY = y
            smoothedX = x
            smoothedY = y
        }

        if (!InkSmoothing.shouldAcceptPoint(lastX(), lastY(), targetX, targetY)) return false

        append(targetX, targetY, pressure)
        return true
    }

    /** Shapes carry exactly two points: where the drag began and where it is now. */
    fun setShapeEndpoints(startX: Float, startY: Float, endX: Float, endY: Float) {
        if (buffer.size < 2 * Stroke.STRIDE) buffer = FloatArray(2 * Stroke.STRIDE)
        buffer[0] = startX
        buffer[1] = startY
        buffer[2] = 1f
        buffer[3] = endX
        buffer[4] = endY
        buffer[5] = 1f
        count = 2
        hasPoints = true
    }

    fun build(): Stroke? {
        if (count == 0) return null
        // A single-point tap still becomes a stroke: teachers use dots, and
        // dropping them makes the board feel unresponsive.
        return Stroke(
            id = UUID.randomUUID().toString(),
            tool = tool,
            style = style,
            points = buffer.copyOf(count * Stroke.STRIDE),
            containerId = containerId,
            cellIndex = cellIndex,
        )
    }

    private fun append(x: Float, y: Float, pressure: Float) {
        val needed = (count + 1) * Stroke.STRIDE
        if (needed > buffer.size) {
            buffer = buffer.copyOf(maxOf(needed, buffer.size * 2))
        }
        val base = count * Stroke.STRIDE
        buffer[base] = x
        buffer[base + 1] = y
        buffer[base + 2] = pressure
        count++
    }

    private companion object {
        /** Most strokes are short; long ones grow geometrically. */
        const val INITIAL_CAPACITY = 64
    }
}
