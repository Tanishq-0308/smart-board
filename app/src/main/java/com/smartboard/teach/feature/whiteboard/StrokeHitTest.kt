package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.Stroke
import kotlin.math.sqrt

/**
 * Eraser hit-testing and cutting.
 *
 * The eraser rubs out only the ink under it, like a real one, but it does so
 * by CUTTING strokes rather than painting pixels away. Pixel erasing would
 * need destination-out blending into the committed bitmap, which makes undo
 * and re-serialization intractable — the page could no longer be described
 * as a list of strokes. A cut stroke is just two shorter strokes, so the page
 * stays undoable, serializable, selectable and sharp at any zoom.
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

    /**
     * What is left of [stroke] once an eraser circle at (cx, cy) has passed
     * over it: null when the circle misses, otherwise the runs of ink outside
     * the circle, each in [Stroke.points] layout. An empty list means the
     * eraser covered all of it.
     *
     * Cuts land exactly on the circle's edge, with pressure interpolated, so
     * the gap is the eraser's size rather than rounded out to the nearest
     * sampled point. Runs shorter than [minLength] are dropped: a crumb that
     * short still renders as a full-width dot.
     */
    fun cut(stroke: Stroke, cx: Float, cy: Float, radius: Float, minLength: Float = 0f): List<FloatArray>? {
        if (!intersects(stroke, cx, cy, radius)) return null

        val runs = mutableListOf<FloatArray>()
        val run = ArrayList<Float>()
        var runLength = 0f

        fun add(x: Float, y: Float, p: Float) {
            if (run.isNotEmpty()) {
                runLength += sqrt(distanceSquared(run[run.size - 3], run[run.size - 2], x, y))
            }
            run += x; run += y; run += p
        }

        fun close() {
            if (run.size >= 2 * Stroke.STRIDE && runLength >= minLength) runs += run.toFloatArray()
            run.clear()
            runLength = 0f
        }

        val r2 = radius * radius
        for (i in 0 until stroke.pointCount - 1) {
            val ax = stroke.x(i); val ay = stroke.y(i); val ap = stroke.pressure(i)
            val bx = stroke.x(i + 1); val by = stroke.y(i + 1); val bp = stroke.pressure(i + 1)

            // Where the segment A + t(B - A) enters and leaves the circle.
            val dx = bx - ax
            val dy = by - ay
            val fx = ax - cx
            val fy = ay - cy
            val a = dx * dx + dy * dy
            val b = 2f * (fx * dx + fy * dy)
            val c = fx * fx + fy * fy - r2
            var t0 = 2f
            var t1 = 2f // 2..2: no overlap
            if (a == 0f) {
                if (c <= 0f) { t0 = 0f; t1 = 1f }
            } else {
                val disc = b * b - 4f * a * c
                if (disc > 0f) {
                    val root = sqrt(disc)
                    t0 = (-b - root) / (2f * a)
                    t1 = (-b + root) / (2f * a)
                }
            }
            val overlaps = t0 < 1f && t1 > 0f

            if (run.isEmpty() && (!overlaps || t0 > 0f)) add(ax, ay, ap)
            if (!overlaps) {
                add(bx, by, bp)
                continue
            }
            if (t0 > 0f) add(ax + dx * t0, ay + dy * t0, ap + (bp - ap) * t0)
            close()
            if (t1 < 1f) {
                add(ax + dx * t1, ay + dy * t1, ap + (bp - ap) * t1)
                add(bx, by, bp)
            }
        }
        close()
        return runs
    }

    /** Ray-cast point-in-polygon. [poly] is x,y pairs; the last vertex joins the first. */
    fun insidePolygon(x: Float, y: Float, poly: FloatArray): Boolean {
        var inside = false
        var j = poly.size - 2
        var i = 0
        while (i < poly.size) {
            val xi = poly[i]
            val yi = poly[i + 1]
            val xj = poly[j]
            val yj = poly[j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
            i += 2
        }
        return inside
    }

    /**
     * What is left of [stroke] outside the loop [poly], for the area eraser:
     * null when none of it is inside, otherwise the runs outside (possibly
     * none). Cut at the samples either side of the loop rather than exactly
     * on it — a loop drawn by hand is no more exact than that.
     */
    fun cutByPolygon(stroke: Stroke, poly: FloatArray, minLength: Float = 0f): List<FloatArray>? {
        if (poly.size < 6) return null
        val n = stroke.pointCount
        val inside = BooleanArray(n) { insidePolygon(stroke.x(it), stroke.y(it), poly) }
        if (inside.none { it }) return null

        val runs = mutableListOf<FloatArray>()
        var start = -1
        for (i in 0..n) {
            val outside = i < n && !inside[i]
            if (outside && start < 0) start = i
            if (!outside && start >= 0) {
                val run = stroke.points.copyOfRange(start * Stroke.STRIDE, i * Stroke.STRIDE)
                if (i - start >= 2 && runLength(run) >= minLength) runs += run
                start = -1
            }
        }
        return runs
    }

    private fun runLength(run: FloatArray): Float {
        var total = 0f
        var i = Stroke.STRIDE
        while (i < run.size) {
            total += sqrt(distanceSquared(run[i - Stroke.STRIDE], run[i - Stroke.STRIDE + 1], run[i], run[i + 1]))
            i += Stroke.STRIDE
        }
        return total
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
