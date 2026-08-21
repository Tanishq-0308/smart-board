package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.Stroke

/**
 * Eraser hit-testing.
 *
 * The eraser removes WHOLE STROKES rather than pixels. Pixel erasing would
 * need destination-out blending into the committed bitmap, which makes undo
 * and re-serialization intractable — you can no longer describe the page as a
 * list of strokes. Stroke-level erase stays undoable, serializable and
 * replayable, and is what most whiteboard apps ship.
 */
object StrokeHitTest {

    /**
     * Does the eraser circle at (cx, cy) touch this stroke?
     *
     * Bounds are checked first because most strokes on a busy page are
     * nowhere near the eraser, and the cheap rejection keeps a drag across a
     * dense board smooth.
     */
    fun intersects(stroke: Stroke, cx: Float, cy: Float, radius: Float): Boolean {
        if (stroke.pointCount == 0) return false

        val bounds = stroke.bounds()
        if (cx + radius < bounds[0] || cx - radius > bounds[2] ||
            cy + radius < bounds[1] || cy - radius > bounds[3]
        ) {
            return false
        }

        val r2 = radius * radius

        if (stroke.pointCount == 1) {
            return distanceSquared(cx, cy, stroke.x(0), stroke.y(0)) <= r2
        }

        for (i in 0 until stroke.pointCount - 1) {
            if (pointToSegmentDistanceSquared(
                    cx, cy,
                    stroke.x(i), stroke.y(i),
                    stroke.x(i + 1), stroke.y(i + 1),
                ) <= r2
            ) {
                return true
            }
        }
        return false
    }

    private fun distanceSquared(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        return dx * dx + dy * dy
    }

    /** Squared distance from a point to a line segment. */
    fun pointToSegmentDistanceSquared(
        px: Float, py: Float,
        ax: Float, ay: Float,
        bx: Float, by: Float,
    ): Float {
        val abx = bx - ax
        val aby = by - ay
        val apx = px - ax
        val apy = py - ay

        val abLenSq = abx * abx + aby * aby
        if (abLenSq == 0f) return distanceSquared(px, py, ax, ay)

        // Projection parameter, clamped so we measure to the segment rather
        // than the infinite line.
        val t = ((apx * abx + apy * aby) / abLenSq).coerceIn(0f, 1f)
        val closestX = ax + t * abx
        val closestY = ay + t * aby
        return distanceSquared(px, py, closestX, closestY)
    }
}
