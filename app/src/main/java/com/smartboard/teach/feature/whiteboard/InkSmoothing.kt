package com.smartboard.teach.feature.whiteboard

import kotlin.math.abs

/**
 * Turns raw digitizer samples into smooth ink.
 *
 * Three stages, applied in order as points arrive:
 *
 *  1. **Distance filtering** — drop samples closer than [MIN_POINT_DISTANCE_PX]
 *     to the previous accepted point. Removes 30-50% of samples with no visible
 *     change, which matters because every retained point costs memory and
 *     replay time for the life of the stroke.
 *
 *  2. **Exponential moving average** — light positional smoothing to kill
 *     digitizer jitter. Alpha is deliberately high (0.5) so ink still tracks
 *     the pen tip; lower values feel laggy and "swimmy" on a large board.
 *
 *  3. **Catmull-Rom to cubic Bezier** — the spline that actually removes the
 *     polygonal look. Emitted append-only with one point of look-ahead: when
 *     point n arrives we can finally emit the curve segment ending at n-1.
 */
object InkSmoothing {

    /** Below this, consecutive samples are visually indistinguishable. */
    const val MIN_POINT_DISTANCE_PX = 2f

    /** Higher tracks the pen more closely; lower smooths more but feels laggy. */
    const val EMA_ALPHA = 0.5f

    fun shouldAcceptPoint(lastX: Float, lastY: Float, newX: Float, newY: Float): Boolean {
        // Manhattan distance: this runs on every motion sample, and the extra
        // precision of a hypotenuse changes no decision at a 2px threshold.
        return abs(newX - lastX) + abs(newY - lastY) >= MIN_POINT_DISTANCE_PX
    }

    fun smooth(previous: Float, current: Float, alpha: Float = EMA_ALPHA): Float =
        previous + alpha * (current - previous)

    /**
     * Catmull-Rom control points for the segment from p1 to p2.
     *
     * p0 and p3 are the neighbouring points that give the curve its tangents;
     * at the ends of a stroke the caller passes the endpoint twice.
     *
     * Returns [c1x, c1y, c2x, c2y] for a cubicTo(c1, c2, p2).
     */
    fun catmullRomControlPoints(
        p0x: Float, p0y: Float,
        p1x: Float, p1y: Float,
        p2x: Float, p2y: Float,
        p3x: Float, p3y: Float,
        tension: Float = 6f,
    ): FloatArray = floatArrayOf(
        p1x + (p2x - p0x) / tension,
        p1y + (p2y - p0y) / tension,
        p2x - (p3x - p1x) / tension,
        p2y - (p3y - p1y) / tension,
    )

    /**
     * Stroke width for a given pressure.
     *
     * Never scales to zero: at pressure 0 the stroke is still 40% of base
     * width, because many boards report 0 or a constant for pressure and a
     * literal reading would make ink vanish.
     */
    fun widthForPressure(
        baseWidthPx: Float,
        pressure: Float,
        pressureSensitive: Boolean,
    ): Float {
        if (!pressureSensitive) return baseWidthPx
        val clamped = pressure.coerceIn(0f, 1f)
        return baseWidthPx * (0.4f + 0.6f * clamped)
    }
}
