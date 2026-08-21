package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Recognises a hand-drawn stroke as a geometric shape.
 *
 * Pure geometry — no model, no network, no Play Services. Works on a board
 * with the Wi-Fi down, which is the whole point: a teacher sketching a circle
 * in a maths lesson gets a clean circle without the board needing a backend.
 *
 * The approach is deliberately conservative. Every test must CLEAR a threshold
 * to fire; anything ambiguous stays as the teacher's own ink. Wrongly
 * "correcting" a deliberate freehand squiggle is far more annoying than
 * failing to snap a rough circle, so the failure mode is biased toward
 * leaving ink alone.
 */
object ShapeRecognizer {

    /** What the recognizer decided, and how sure it is. */
    data class Result(
        val tool: DrawTool,
        /**
         * Two endpoints for LINE/RECT/CIRCLE/ARROW: [x0, y0, x1, y1].
         * Empty for a polygon, which carries [vertices] instead.
         */
        val endpoints: FloatArray,
        val confidence: Float,
        /**
         * Polygon corners as [x0, y0, x1, y1, ...], already regularised.
         * Null for the two-point shapes.
         */
        val vertices: FloatArray? = null,
    )

    /**
     * @param stroke the finished freehand stroke
     * @return a shape to substitute, or null to keep the original ink
     */
    fun recognise(stroke: Stroke): Result? {
        val n = stroke.pointCount
        if (n < MIN_POINTS) return null

        val metrics = Metrics.of(stroke)

        // Too small to be a deliberate shape — probably a dot or a tick.
        if (metrics.diagonal < MIN_SIZE) return null

        // A stroke that doubles back on itself repeatedly is handwriting or a
        // scribble, never a shape.
        if (metrics.pathLength > metrics.diagonal * MAX_PATH_TO_DIAGONAL) return null

        return if (metrics.isClosed) {
            recogniseClosed(stroke, metrics)
        } else {
            recogniseOpen(stroke, metrics)
        }
    }

    // --- closed shapes: circle, rectangle, triangle ------------------------

    private fun recogniseClosed(stroke: Stroke, m: Metrics): Result? {
        val corners = findCorners(stroke, m)
        val rect = rectangleConfidence(stroke, m, corners)
        val circle = circleConfidence(stroke, m, corners)

        // Rectangle is tested FIRST and wins ties. A square has low radial
        // variance too, so it scores well as a circle; four sharp corners is
        // the stronger signal and must take priority.
        if (rect >= MIN_CONFIDENCE && rect >= circle) {
            return Result(
                tool = DrawTool.RECT,
                endpoints = floatArrayOf(m.minX, m.minY, m.maxX, m.maxY),
                confidence = rect,
            )
        }

        if (circle >= MIN_CONFIDENCE) {
            return Result(
                tool = DrawTool.CIRCLE,
                endpoints = floatArrayOf(m.minX, m.minY, m.maxX, m.maxY),
                confidence = circle,
            )
        }

        // Any other corner count is a polygon: triangle, pentagon, hexagon,
        // and so on. These used to be declined for want of a tool to put them
        // in, which is why a hand-drawn hexagon either stayed as ink or fell
        // through to the circle branch.
        if (corners.size in MIN_POLYGON_SIDES..MAX_POLYGON_SIDES) {
            val concentration = turnConcentration(stroke)
            if (concentration >= MIN_POLYGON_TURN_CONCENTRATION) {
                val vertices = regularisePolygon(stroke, corners, m)
                if (vertices != null) {
                    return Result(
                        tool = DrawTool.POLYGON,
                        endpoints = FloatArray(0),
                        confidence = concentration.coerceIn(0f, 1f),
                        vertices = vertices,
                    )
                }
            }
        }

        return null
    }

    /**
     * Circle score from radial consistency.
     *
     * Every point of a true circle sits the same distance from the centroid,
     * so the coefficient of variation of those distances approaches zero.
     */
    private fun circleConfidence(stroke: Stroke, m: Metrics, corners: List<Int>): Float {
        if (m.aspectRatio > MAX_CIRCLE_ASPECT) return 0f

        // A circle turns EVENLY all the way round; a polygon does almost all
        // of its turning at a handful of corners and goes straight between
        // them. Radial variance cannot tell them apart — a regular hexagon is
        // nearly as radially even as a circle, which is why hexagons were
        // being snapped into circles — but turning concentration can, and
        // unlike chord-to-arc ratios it does not depend on how densely the
        // digitizer sampled the stroke.
        if (turnConcentration(stroke) > MAX_CIRCLE_TURN_CONCENTRATION) return 0f

        var sum = 0f
        for (i in 0 until stroke.pointCount) {
            sum += hypot(stroke.x(i) - m.centroidX, stroke.y(i) - m.centroidY)
        }
        val meanRadius = sum / stroke.pointCount
        if (meanRadius < 1e-3f) return 0f

        var variance = 0f
        for (i in 0 until stroke.pointCount) {
            val d = hypot(stroke.x(i) - m.centroidX, stroke.y(i) - m.centroidY) - meanRadius
            variance += d * d
        }
        val cv = sqrt(variance / stroke.pointCount) / meanRadius

        // cv ~0.00-0.10 is a good circle; 0.30+ is not a circle at all.
        return (1f - cv / CIRCLE_CV_LIMIT).coerceIn(0f, 1f)
    }

    /**
     * Rectangle score, judged by CORNERS rather than by hugging the bounding
     * box.
     *
     * The first version measured how many points lay near the axis-aligned
     * bounding box and demanded 80%. A hand-drawn square is almost always
     * slightly rotated, and then only ~33% of its points are near that box —
     * so the test was effectively unpassable and every square fell through to
     * the circle branch. That is why squares were being turned into circles.
     *
     * Four corners, roughly evenly spaced around the path, with turning
     * concentrated at them, is what actually identifies a quadrilateral —
     * and it does not care how the shape is rotated.
     */
    private fun rectangleConfidence(stroke: Stroke, m: Metrics, corners: List<Int>): Float {
        if (corners.size != 4) return 0f

        // Turning must be concentrated at corners, or this is a curve that
        // happened to trip the corner detector four times.
        val concentration = turnConcentration(stroke)
        if (concentration < MIN_RECT_TURN_CONCENTRATION) return 0f

        // A quadrilateral's corners divide the path into four comparable
        // runs. Wildly uneven spacing means the detections are noise.
        val n = stroke.pointCount
        val gaps = IntArray(4)
        for (i in 0 until 4) {
            val next = corners[(i + 1) % 4]
            val current = corners[i]
            gaps[i] = if (next > current) next - current else next + n - current
        }
        val shortest = gaps.min()
        val longest = gaps.max()
        if (shortest <= 0) return 0f
        if (longest.toFloat() / shortest > MAX_RECT_SIDE_IMBALANCE) return 0f

        return concentration.coerceIn(0f, 1f)
    }

    /**
     * Turns detected corners into a clean, regular polygon.
     *
     * The corners a person draws are never evenly spaced or equidistant from
     * the centre, so using them raw would produce a tidier version of a wonky
     * shape rather than the shape they meant. Instead the vertices are
     * REGULARISED: placed at equal angles around the centroid at a common
     * radius, then rotated to match the orientation the teacher actually
     * drew — so a hexagon comes out as a true hexagon, sitting the way they
     * drew it.
     *
     * @return vertices as [x0, y0, x1, y1, ...], or null if degenerate
     */
    fun regularisePolygon(stroke: Stroke, corners: List<Int>, m: Metrics): FloatArray? {
        val sides = corners.size
        if (sides < MIN_POLYGON_SIDES) return null

        // Centre on the corners rather than the whole path: samples bunch up
        // along slower-drawn edges and would drag a path centroid off centre.
        var cx = 0f
        var cy = 0f
        corners.forEach { index ->
            cx += stroke.x(index)
            cy += stroke.y(index)
        }
        cx /= sides
        cy /= sides

        // Mean corner distance sets the size; the first corner sets rotation.
        var meanRadius = 0f
        corners.forEach { index ->
            meanRadius += hypot(stroke.x(index) - cx, stroke.y(index) - cy)
        }
        meanRadius /= sides
        if (meanRadius < MIN_SIZE / 2f) return null

        val firstAngle = atan2(stroke.y(corners[0]) - cy, stroke.x(corners[0]) - cx)

        val vertices = FloatArray(sides * 2)
        for (i in 0 until sides) {
            val angle = firstAngle + 2.0 * Math.PI * i / sides
            vertices[i * 2] = cx + meanRadius * kotlin.math.cos(angle).toFloat()
            vertices[i * 2 + 1] = cy + meanRadius * kotlin.math.sin(angle).toFloat()
        }
        return vertices
    }

    /**
     * What share of the stroke's total turning happens in its sharpest steps.
     *
     * Walk the path measuring how much direction changes at each point, then
     * ask how much of the total is contributed by the sharpest quarter.
     *
     *  - A circle turns by the same small amount everywhere, so the sharpest
     *    quarter contributes about a quarter of the turning: ~0.25-0.49 once
     *    hand-drawn noise is included.
     *  - A polygon goes straight along each side and turns hard at each
     *    corner, so a small number of steps dominate: ~0.59-0.76.
     *
     * Crucially this is a RATIO of turning to turning, so it does not shift
     * when the digitizer samples more or fewer points — which is exactly
     * where a chord-to-arc straightness measure fell down.
     */
    fun turnConcentration(stroke: Stroke): Float {
        val n = stroke.pointCount
        if (n < MIN_POINTS) return 0f

        val window = max(2, (n * TURN_WINDOW_FRACTION).toInt())
        val turns = FloatArray(n)
        var total = 0f

        for (i in 0 until n) {
            val before = (i - window + n) % n
            val after = (i + window) % n
            val beforeAngle = atan2(
                stroke.y(i) - stroke.y(before),
                stroke.x(i) - stroke.x(before),
            )
            val afterAngle = atan2(
                stroke.y(after) - stroke.y(i),
                stroke.x(after) - stroke.x(i),
            )
            var turn = abs(afterAngle - beforeAngle)
            if (turn > Math.PI) turn = (2 * Math.PI - turn).toFloat()
            turns[i] = turn
            total += turn
        }

        if (total <= 1e-6f) return 0f

        turns.sort()
        val topCount = max(1, n / 4)
        var sharpest = 0f
        for (i in n - topCount until n) sharpest += turns[i]

        return sharpest / total
    }

    // --- open shapes: line, arrow -----------------------------------------

    private fun recogniseOpen(stroke: Stroke, m: Metrics): Result? {
        val n = stroke.pointCount
        val x0 = stroke.x(0)
        val y0 = stroke.y(0)
        val x1 = stroke.x(n - 1)
        val y1 = stroke.y(n - 1)

        val straight = straightnessConfidence(stroke, x0, y0, x1, y1)
        if (straight < MIN_CONFIDENCE) return null

        return Result(
            tool = DrawTool.LINE,
            endpoints = floatArrayOf(x0, y0, x1, y1),
            confidence = straight,
        )
    }

    /**
     * Straightness from the largest perpendicular deviation of any point from
     * the line joining the endpoints, normalised by the line's own length.
     */
    private fun straightnessConfidence(
        stroke: Stroke,
        x0: Float, y0: Float,
        x1: Float, y1: Float,
    ): Float {
        val length = hypot(x1 - x0, y1 - y0)
        if (length < MIN_SIZE) return 0f

        var maxDeviation = 0f
        for (i in 1 until stroke.pointCount - 1) {
            val d = perpendicularDistance(stroke.x(i), stroke.y(i), x0, y0, x1, y1)
            if (d > maxDeviation) maxDeviation = d
        }

        val ratio = maxDeviation / length
        return (1f - ratio / LINE_DEVIATION_LIMIT).coerceIn(0f, 1f)
    }

    // --- corner detection --------------------------------------------------

    /**
     * Indices where the path turns sharply.
     *
     * The direction window is measured in ARC LENGTH, not in point count.
     * That distinction matters: a densely sampled octagon has so many points
     * that a count-based window spanned a whole side and smoothed its corners
     * away entirely, while a sparse triangle over-detected. Arc length scales
     * with the SHAPE rather than with how fast the digitizer happened to
     * sample it.
     *
     * Candidates are then reduced by non-maximum suppression: the sharpest
     * turn wins and anything within [CORNER_SEPARATION_FRACTION] of the
     * perimeter of an already-chosen corner is discarded, so one physical
     * corner cannot register several times.
     */
    fun findCorners(stroke: Stroke, m: Metrics): List<Int> {
        val n = stroke.pointCount
        if (n < MIN_POINTS) return emptyList()

        // Cumulative arc length, so windows and spacing are distance-based.
        val cumulative = FloatArray(n)
        for (i in 1 until n) {
            cumulative[i] = cumulative[i - 1] +
                hypot(stroke.x(i) - stroke.x(i - 1), stroke.y(i) - stroke.y(i - 1))
        }
        val perimeter = cumulative[n - 1]
        if (perimeter <= 0f) return emptyList()

        val windowDistance = perimeter * CORNER_WINDOW_FRACTION

        fun walk(from: Int, forward: Boolean): Int {
            var travelled = 0f
            var index = from
            repeat(n) {
                val next = if (forward) (index + 1) % n else (index - 1 + n) % n
                travelled += hypot(
                    stroke.x(next) - stroke.x(index),
                    stroke.y(next) - stroke.y(index),
                )
                index = next
                if (travelled >= windowDistance) return index
            }
            return index
        }

        val candidates = mutableListOf<Pair<Int, Float>>()
        for (i in 0 until n) {
            val before = walk(i, forward = false)
            val after = walk(i, forward = true)

            val beforeAngle = atan2(
                stroke.y(i) - stroke.y(before),
                stroke.x(i) - stroke.x(before),
            )
            val afterAngle = atan2(
                stroke.y(after) - stroke.y(i),
                stroke.x(after) - stroke.x(i),
            )
            var turn = abs(afterAngle - beforeAngle)
            if (turn > Math.PI) turn = (2 * Math.PI - turn).toFloat()

            if (turn > CORNER_MIN_TURN_RAD) candidates += i to turn
        }

        if (candidates.isEmpty()) return emptyList()

        // Non-max suppression, sharpest first.
        val minSeparation = perimeter * CORNER_SEPARATION_FRACTION
        val chosen = mutableListOf<Int>()
        candidates.sortByDescending { it.second }

        for ((index, _) in candidates) {
            val clashes = chosen.any { other ->
                val raw = abs(cumulative[index] - cumulative[other])
                min(raw, perimeter - raw) < minSeparation
            }
            if (!clashes) chosen += index
        }

        return chosen.sorted()
    }

    private fun perpendicularDistance(
        px: Float, py: Float,
        ax: Float, ay: Float,
        bx: Float, by: Float,
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        if (lengthSq < 1e-6f) return hypot(px - ax, py - ay)
        return abs(dy * px - dx * py + bx * ay - by * ax) / sqrt(lengthSq)
    }

    /** Cached measurements of a stroke, computed once per recognition. */
    data class Metrics(
        val minX: Float, val minY: Float,
        val maxX: Float, val maxY: Float,
        val centroidX: Float, val centroidY: Float,
        val pathLength: Float,
        val closingGap: Float,
    ) {
        val width: Float get() = maxX - minX
        val height: Float get() = maxY - minY
        val diagonal: Float get() = hypot(width, height)

        /** Longer side over shorter side; 1.0 is square. */
        val aspectRatio: Float
            get() {
                val w = max(width, 1e-3f)
                val h = max(height, 1e-3f)
                return max(w, h) / min(w, h)
            }

        /**
         * Closed if the gap between the ends is small relative to the size of
         * the shape — people rarely close a circle exactly.
         */
        val isClosed: Boolean get() = closingGap < diagonal * CLOSING_GAP_RATIO

        companion object {
            fun of(stroke: Stroke): Metrics {
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                var sumX = 0f
                var sumY = 0f
                var pathLength = 0f

                val n = stroke.pointCount
                for (i in 0 until n) {
                    val x = stroke.x(i)
                    val y = stroke.y(i)
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    sumX += x
                    sumY += y
                    if (i > 0) {
                        pathLength += hypot(x - stroke.x(i - 1), y - stroke.y(i - 1))
                    }
                }

                return Metrics(
                    minX = minX, minY = minY, maxX = maxX, maxY = maxY,
                    centroidX = sumX / n, centroidY = sumY / n,
                    pathLength = pathLength,
                    closingGap = hypot(
                        stroke.x(n - 1) - stroke.x(0),
                        stroke.y(n - 1) - stroke.y(0),
                    ),
                )
            }
        }
    }

    // --- thresholds --------------------------------------------------------
    //
    // Tuned to be conservative. Every one of these is a decision about how
    // wrong a teacher's drawing may be before we stop trusting our guess.

    /** Fewer points than this is a tap or a flick, not a drawn shape. */
    const val MIN_POINTS = 8

    /** World units. Below this, snapping would fight small annotations. */
    const val MIN_SIZE = 24f

    /**
     * Ends must meet within this fraction of the shape's diagonal.
     *
     * Measured from real strokes drawn on the tablet: a hand-drawn square
     * closed at 0.22 and a circle at 0.15, so 0.28 was marginal. 0.34 accepts
     * a genuinely sloppy close while still rejecting an open arc, which
     * measured 0.59.
     */
    const val CLOSING_GAP_RATIO = 0.34f

    /**
     * A path much longer than its diagonal has doubled back — handwriting,
     * hatching, a scribble. Never a simple shape.
     */
    const val MAX_PATH_TO_DIAGONAL = 4.2f

    /**
     * Radial coefficient of variation at which circle confidence hits zero.
     *
     * Tuned against strokes actually drawn by hand on the target tablet, not
     * synthetic test circles. A real hand-drawn circle measured cv=0.22 —
     * with the original 0.32 limit that scored 0.31 and was rejected, so the
     * feature silently did nothing on the device. At 0.50 that circle scores
     * 0.56 and passes, while a shapeless blob (cv=0.31) scores 0.38 and an
     * open arc (cv=0.34) scores 0.32, both correctly rejected.
     */
    const val CIRCLE_CV_LIMIT = 0.50f

    /** Beyond this width:height ratio it is an ellipse we should not snap. */
    const val MAX_CIRCLE_ASPECT = 2.6f

    /**
     * Above this, turning is concentrated at corners, so the shape is a
     * polygon and must not be snapped to a circle.
     *
     * Measured: circles 0.40-0.49 (turning spread evenly), hexagon 0.59,
     * pentagon 0.61, square 0.74, triangle 0.76. 0.54 sits in the gap.
     *
     * KNOWN LIMIT, measured rather than assumed: across 144 synthetic circle
     * variants (40-160 samples, 0-20 units of wobble) concentration ranged
     * 0.24-0.54, with 10% at or above 0.50. A regular octagon measures 0.50.
     * The distributions genuinely OVERLAP, so no threshold separates them —
     * catching octagons would misclassify roughly one circle in ten, and a
     * teacher's circle turning into an octagon is the worse failure.
     *
     * Three to six sides are recognised exactly. Seven-plus may snap to a
     * circle; Settings > Snap shapes turns the whole behaviour off for a
     * lesson that needs exact polygons.
     */
    const val MAX_CIRCLE_TURN_CONCENTRATION = 0.54f

    /** Direction window for turn measurement, as a fraction of point count. */
    const val TURN_WINDOW_FRACTION = 0.06f

    /**
     * Turning must be at least this concentrated for a four-corner detection
     * to count as a rectangle. Measured: square 0.74, and the circle veto
     * sits at 0.54, so this also keeps the two branches from disagreeing.
     */
    const val MIN_RECT_TURN_CONCENTRATION = 0.55f

    /**
     * Longest side over shortest, by point count. A real quadrilateral is
     * roughly balanced; 4:1 allows a long thin rectangle while rejecting
     * corner detections that clustered on one part of a curve.
     */
    const val MAX_RECT_SIDE_IMBALANCE = 4.5f

    /** Max perpendicular deviation, as a fraction of length, for a line. */
    const val LINE_DEVIATION_LIMIT = 0.14f

    /**
     * Direction window as a fraction of the path's perimeter.
     *
     * Arc-length based, NOT point-count based: a count-based window scaled
     * with sampling density, so a densely sampled octagon had its corners
     * smoothed away while a sparse triangle over-detected. Swept against
     * triangle through hexagon plus four circle variants; 0.05 recovers the
     * exact side count for 3-6 sides and leaves circles at 0-1 corners.
     */
    const val CORNER_WINDOW_FRACTION = 0.05f

    /**
     * Two corners must be at least this far apart along the perimeter, or the
     * weaker one is suppressed. Stops a single physical corner registering
     * several times.
     */
    const val CORNER_SEPARATION_FRACTION = 0.10f

    /** ~50 degrees. Below this a bend is a curve, not a corner. */
    const val CORNER_MIN_TURN_RAD = 0.87f

    /** Nothing is substituted below this confidence. */
    const val MIN_CONFIDENCE = 0.55f

    /** Fewest sides worth regularising. Below three there is no polygon. */
    const val MIN_POLYGON_SIDES = 3

    /**
     * Most sides worth regularising. Past about ten a polygon is visually a
     * circle anyway, and corner detections that high are usually noise.
     */
    const val MAX_POLYGON_SIDES = 10

    /**
     * Turning must be at least this concentrated at the corners for a
     * polygon. Measured: hexagon 0.59, pentagon 0.61, triangle 0.76 against
     * circles at 0.40-0.49.
     */
    const val MIN_POLYGON_TURN_CONCENTRATION = 0.52f
}
