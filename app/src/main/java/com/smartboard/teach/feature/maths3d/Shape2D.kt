package com.smartboard.teach.feature.maths3d

import androidx.annotation.StringRes
import com.smartboard.teach.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The 2D half of 3D Maths: a drawn stroke becomes a clean shape in board
 * units (1 unit = 1 cm, y points DOWN like the screen).
 *
 * Deliberately not [com.smartboard.teach.feature.whiteboard.ShapeRecognizer].
 * That one is conservative — it leaves ink alone when unsure and reduces a
 * rectangle to its bounding box. Here the teacher drew the stroke precisely
 * so it becomes a solid, so every stroke must be interpreted: tilted
 * rectangles keep their tilt, odd outlines become a free polygon, and an open
 * line becomes a revolve profile.
 */
data class P(val x: Double, val y: Double)

sealed interface Shape2D {
    @get:StringRes val name: Int

    data class Circle(val cx: Double, val cy: Double, val r: Double) : Shape2D {
        override val name get() = R.string.m3d_shape_circle
    }

    data class Rect(val cx: Double, val cy: Double, val w: Double, val h: Double) : Shape2D {
        val square get() = w == h
        override val name get() = if (square) R.string.m3d_shape_square else R.string.m3d_shape_rectangle
    }

    data class Polygon(
        val pts: List<P>,
        @StringRes override val name: Int,
        val regular: Boolean = false,
        val free: Boolean = false,
    ) : Shape2D

    data class Profile(val pts: List<P>) : Shape2D {
        override val name get() = R.string.m3d_shape_profile
    }
}

enum class Mode(@StringRes val label: Int) {
    PRISM(R.string.m3d_solid_prism), PYRAMID(R.string.m3d_solid_pyramid), CYLINDER(R.string.m3d_solid_cylinder),
    CONE(R.string.m3d_solid_cone), SPHERE(R.string.m3d_solid_sphere), REVOLVE(R.string.m3d_mode_revolve),
}

data class ModeOption(val mode: Mode, @StringRes val label: Int, val enabled: Boolean = true)

const val SNAP = 0.5

fun snap(v: Double, s: Double = SNAP) = round(v / s) * s

/** Two decimals at most, no trailing zeros, no "-0". */
fun fmt(n: Double): String {
    val r = round(n * 100) / 100
    if (r == 0.0) return "0"
    return if (r == floor(r)) r.toLong().toString() else r.toString()
}

// --- 2D helpers -------------------------------------------------------------

fun dist(a: P, b: P) = hypot(a.x - b.x, a.y - b.y)

fun pathLength(pts: List<P>) = (1 until pts.size).sumOf { dist(pts[it - 1], pts[it]) }

fun signedArea(pts: List<P>): Double {
    var s = 0.0
    for (i in pts.indices) {
        val a = pts[i]; val b = pts[(i + 1) % pts.size]
        s += a.x * b.y - b.x * a.y
    }
    return s / 2
}

fun polyArea(pts: List<P>) = abs(signedArea(pts))

fun sides(pts: List<P>) = pts.indices.map { dist(pts[it], pts[(it + 1) % pts.size]) }

fun perimeter(pts: List<P>) = sides(pts).sum()

fun centroid(pts: List<P>): P {
    val a = signedArea(pts)
    if (abs(a) < 1e-9) return P(pts.sumOf { it.x } / pts.size, pts.sumOf { it.y } / pts.size)
    var cx = 0.0; var cy = 0.0
    for (i in pts.indices) {
        val p = pts[i]; val q = pts[(i + 1) % pts.size]
        val f = p.x * q.y - q.x * p.y
        cx += (p.x + q.x) * f
        cy += (p.y + q.y) * f
    }
    return P(cx / (6 * a), cy / (6 * a))
}

/** [n] points evenly spaced along the path (closing it back to the start if [closed]). */
fun resample(pts: List<P>, n: Int, closed: Boolean): List<P> {
    val path = if (closed) pts + pts[0] else pts
    val step = pathLength(path) / (if (closed) n else n - 1)
    val out = mutableListOf(path[0])
    var acc = 0.0
    for (i in 1 until path.size) {
        var a = path[i - 1]
        val b = path[i]
        var d = dist(a, b)
        while (acc + d >= step && out.size < n && d > 0) {
            val t = (step - acc) / d
            val p = P(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
            out += p
            a = p
            d = dist(a, b)
            acc = 0.0
        }
        acc += d
    }
    while (out.size < n) out += path.last()
    return out
}

/** Ramer–Douglas–Peucker simplification. */
fun rdp(pts: List<P>, eps: Double): List<P> {
    if (pts.size < 3) return pts
    val a = pts.first(); val b = pts.last()
    val len = dist(a, b).takeIf { it > 0 } ?: 1e-9
    var idx = -1; var max = 0.0
    for (i in 1 until pts.size - 1) {
        val p = pts[i]
        val d = abs((b.x - a.x) * (a.y - p.y) - (a.x - p.x) * (b.y - a.y)) / len
        if (d > max) { max = d; idx = i }
    }
    if (max <= eps) return listOf(a, b)
    return rdp(pts.subList(0, idx + 1), eps).dropLast(1) + rdp(pts.subList(idx, pts.size), eps)
}

/** Interior angle at vertex [i], in degrees. */
fun angleAt(pts: List<P>, i: Int): Double {
    val n = pts.size
    val a = pts[(i - 1 + n) % n]; val b = pts[i]; val c = pts[(i + 1) % n]
    val v1x = a.x - b.x; val v1y = a.y - b.y; val v2x = c.x - b.x; val v2y = c.y - b.y
    val cos = (v1x * v2x + v1y * v2y) / (hypot(v1x, v1y) * hypot(v2x, v2y))
    return acos(cos.coerceIn(-1.0, 1.0)) * 180 / PI
}

// --- recognition --------------------------------------------------------------

// Only 5..8 corners reach makeNGon; 3 and 4 have their own recognisers.
private val POLY_NAMES = mapOf(
    5 to R.string.m3d_shape_pentagon, 6 to R.string.m3d_shape_hexagon,
    7 to R.string.m3d_shape_heptagon, 8 to R.string.m3d_shape_octagon,
)

private val REGULAR_NAMES = mapOf(
    5 to R.string.m3d_shape_regular_pentagon, 6 to R.string.m3d_shape_regular_hexagon,
    7 to R.string.m3d_shape_regular_heptagon, 8 to R.string.m3d_shape_regular_octagon,
)

/** A raw stroke in board units → a shape, or null when it is too small to read. */
fun recognise(raw: List<P>): Shape2D? {
    if (raw.size < 5) return null
    val len = pathLength(raw)
    if (len < 1) return null

    val closed = dist(raw.first(), raw.last()) < max(0.18 * len, 0.7)
    if (!closed) return recogniseProfile(raw)

    // Cut any overshoot past the starting point before looking for corners.
    var cut = raw.size - 1
    var best = Double.MAX_VALUE
    for (i in (raw.size * 0.8).toInt() until raw.size) {
        val d = dist(raw[i], raw[0])
        if (d < best) { best = d; cut = i }
    }
    val stroke = raw.subList(0, cut + 1)

    val n = 64; val k = 3
    val pts = resample(stroke, n, true)
    val turn = DoubleArray(n) { i ->
        val a = pts[(i - k + n) % n]; val b = pts[i]; val c = pts[(i + k) % n]
        val d = abs(atan2(c.y - b.y, c.x - b.x) - atan2(b.y - a.y, b.x - a.x))
        if (d > PI) 2 * PI - d else d
    }
    val corners = mutableListOf<P>()
    for (i in 0 until n) {
        if (turn[i] < 0.87) continue // ~50°
        val isPeak = (1..k).all { j -> turn[(i - j + n) % n] < turn[i] && turn[(i + j) % n] <= turn[i] }
        if (isPeak) corners += pts[i]
    }

    val c = centroid(pts)
    val ds = pts.map { dist(it, c) }
    val r = ds.average()
    val circleErr = ds.sumOf { abs(it - r) } / n / r

    val count = corners.size
    return when {
        count <= 2 && circleErr < 0.2 -> Shape2D.Circle(snap(c.x), snap(c.y), max(SNAP, snap(r)))
        count == 3 -> makeTriangle(corners)
        count == 4 -> makeQuad(corners)
        count in 5..8 -> makeNGon(corners)
        else -> {
            // Anything else: keep the drawn outline, simplified.
            val outline = rdp(resample(stroke, 96, false), 0.12).dropLast(1)
            if (outline.size < 3) null else Shape2D.Polygon(outline, R.string.m3d_shape_free, free = true)
        }
    }
}

private fun makeTriangle(corners: List<P>): Shape2D? {
    var pts = corners.map { P(snap(it.x), snap(it.y)) }
    if (polyArea(pts) < 0.25) return null
    val s = sides(pts).sorted()
    val angles = pts.indices.map { angleAt(pts, it) }
    var name = R.string.m3d_shape_scalene_triangle
    var regular = false
    if (s[2] - s[0] < 0.08 * s[2]) {
        name = R.string.m3d_shape_equilateral_triangle
        regular = true
        // Make it exactly equilateral around its centroid, keeping the first vertex direction.
        val c = centroid(pts)
        val rr = snap(s.average()) / sqrt(3.0)
        val a0 = atan2(pts[0].y - c.y, pts[0].x - c.x)
        pts = (0 until 3).map { P(c.x + rr * cos(a0 + it * 2 * PI / 3), c.y + rr * sin(a0 + it * 2 * PI / 3)) }
    } else if (angles.any { abs(it - 90) < 6 }) {
        name = R.string.m3d_shape_right_triangle
    } else if (s[1] - s[0] < 0.08 * s[1] || s[2] - s[1] < 0.08 * s[2]) {
        name = R.string.m3d_shape_isosceles_triangle
    }
    return Shape2D.Polygon(pts, name, regular)
}

private fun makeQuad(corners: List<P>): Shape2D? {
    val rightAngles = corners.indices.all { abs(angleAt(corners, it) - 90) < 18 }
    val edgeAng = atan2(corners[1].y - corners[0].y, corners[1].x - corners[0].x) * 180 / PI
    val off = abs(((edgeAng % 90) + 90) % 90)
    val axisAligned = off < 15 || off > 75

    if (rightAngles && axisAligned) {
        val xs = corners.map { it.x }.sorted()
        val ys = corners.map { it.y }.sorted()
        val x0 = (xs[0] + xs[1]) / 2; val x1 = (xs[2] + xs[3]) / 2
        val y0 = (ys[0] + ys[1]) / 2; val y1 = (ys[2] + ys[3]) / 2
        var w = max(SNAP, snap(x1 - x0)); var h = max(SNAP, snap(y1 - y0))
        if (abs(w - h) <= 0.15 * max(w, h)) { w = max(SNAP, snap((w + h) / 2)); h = w }
        return Shape2D.Rect(snap((x0 + x1) / 2, 0.25), snap((y0 + y1) / 2, 0.25), w, h)
    }

    val pts = corners.map { P(snap(it.x), snap(it.y)) }
    if (polyArea(pts) < 0.25) return null
    val s = sides(pts)
    fun parallel(i: Int): Boolean {
        val a = pts[i]; val b = pts[(i + 1) % 4]; val c = pts[(i + 2) % 4]; val d = pts[(i + 3) % 4]
        val cross = (b.x - a.x) * (c.y - d.y) - (b.y - a.y) * (c.x - d.x)
        return abs(cross) / (dist(a, b) * dist(c, d)) < 0.12
    }
    val name = when {
        rightAngles -> R.string.m3d_shape_rectangle_tilted
        parallel(0) && parallel(1) -> if (s.max() - s.min() < 0.1 * s.max()) R.string.m3d_shape_rhombus else R.string.m3d_shape_parallelogram
        parallel(0) || parallel(1) -> R.string.m3d_shape_trapezium
        else -> R.string.m3d_shape_quadrilateral
    }
    return Shape2D.Polygon(pts, name)
}

private fun makeNGon(corners: List<P>): Shape2D {
    val n = corners.size
    val s = sides(corners)
    val mean = s.average()
    val cv = sqrt(s.sumOf { (it - mean) * (it - mean) } / n) / mean
    if (cv >= 0.18) return Shape2D.Polygon(corners.map { P(snap(it.x), snap(it.y)) }, POLY_NAMES.getValue(n))
    val c = centroid(corners)
    val rr = max(SNAP, snap(mean)) / (2 * sin(PI / n))
    val a0 = atan2(corners[0].y - c.y, corners[0].x - c.x)
    val dir = if (signedArea(corners) > 0) 1 else -1
    val cx = snap(c.x); val cy = snap(c.y)
    val pts = (0 until n).map { P(cx + rr * cos(a0 + dir * it * 2 * PI / n), cy + rr * sin(a0 + dir * it * 2 * PI / n)) }
    return Shape2D.Polygon(pts, REGULAR_NAMES.getValue(n), regular = true)
}

private fun recogniseProfile(raw: List<P>): Shape2D? {
    val pts = rdp(raw, 0.06)
    return if (pts.size < 2) null else Shape2D.Profile(pts)
}

// --- shape → solid ------------------------------------------------------------

/** Corners of a closed shape in board units. */
fun outline(shape: Shape2D, segments: Int = 64): List<P> = when (shape) {
    is Shape2D.Rect -> with(shape) {
        listOf(P(cx - w / 2, cy - h / 2), P(cx + w / 2, cy - h / 2), P(cx + w / 2, cy + h / 2), P(cx - w / 2, cy + h / 2))
    }
    is Shape2D.Circle -> (0 until segments).map {
        val a = it.toDouble() / segments * 2 * PI
        P(shape.cx + shape.r * cos(a), shape.cy + shape.r * sin(a))
    }
    is Shape2D.Polygon -> shape.pts
    is Shape2D.Profile -> shape.pts
}

/** Does the shape stay on one side of the revolve axis (x = 0)? */
fun revolvable(shape: Shape2D): Boolean {
    if (shape is Shape2D.Profile) return true
    val xs = outline(shape).map { it.x }
    return xs.min() >= -1e-6 || xs.max() <= 1e-6
}

fun modesFor(shape: Shape2D): List<ModeOption> {
    val revolve = ModeOption(Mode.REVOLVE, if (shape is Shape2D.Circle) R.string.m3d_mode_torus_revolve else R.string.m3d_mode_revolve, revolvable(shape))
    return when (shape) {
        is Shape2D.Circle -> listOf(ModeOption(Mode.CYLINDER, R.string.m3d_solid_cylinder), ModeOption(Mode.CONE, R.string.m3d_solid_cone), ModeOption(Mode.SPHERE, R.string.m3d_solid_sphere), revolve)
        is Shape2D.Profile -> listOf(revolve)
        is Shape2D.Rect -> listOf(ModeOption(Mode.PRISM, if (shape.square) R.string.m3d_solid_cube_cuboid else R.string.m3d_solid_cuboid), ModeOption(Mode.PYRAMID, R.string.m3d_solid_pyramid), revolve)
        is Shape2D.Polygon -> listOf(ModeOption(Mode.PRISM, R.string.m3d_solid_prism), ModeOption(Mode.PYRAMID, R.string.m3d_solid_pyramid), revolve)
    }
}

fun isRegular(shape: Shape2D) = when (shape) {
    is Shape2D.Rect -> shape.square
    is Shape2D.Polygon -> shape.regular
    else -> false
}

/** A (radius, height) point of a revolve loop. */
data class RY(val r: Double, val y: Double)

/** The closed (radius, height) loop that gets revolved about the vertical axis. */
fun revolveLoop(shape: Shape2D): List<RY> {
    if (shape is Shape2D.Profile) {
        val base = shape.pts.maxOf { it.y }
        val loop = shape.pts.map { RY(if (abs(it.x) < 0.35) 0.0 else abs(it.x), base - it.y) }.toMutableList()
        if (loop.first().y > loop.last().y) loop.reverse()
        if (loop.first().r > 0) loop.add(0, RY(0.0, loop.first().y))
        if (loop.last().r > 0) loop.add(RY(0.0, loop.last().y))
        return loop
    }
    val pts = outline(shape, 72)
    val base = pts.maxOf { it.y }
    val loop = pts.map { RY(abs(it.x), base - it.y) }
    return loop + loop.first()
}

data class RevolveStats(val volume: Double, val surface: Double, val height: Double, val rMax: Double)

/** Volume by stacked frusta (disk method) and surface by frustum bands. */
fun revolveStats(loop: List<RY>): RevolveStats {
    var v = 0.0; var s = 0.0
    for (i in 1 until loop.size) {
        val a = loop[i - 1]; val b = loop[i]
        v += PI * (b.y - a.y) * (a.r * a.r + a.r * b.r + b.r * b.r) / 3
        s += PI * (a.r + b.r) * hypot(b.r - a.r, b.y - a.y)
    }
    val ys = loop.map { it.y }
    return RevolveStats(abs(v), s, ys.max() - ys.min(), loop.maxOf { it.r })
}

// --- presets ------------------------------------------------------------------

data class Preset(@StringRes val label: Int, val shape: Shape2D, val mode: Mode, val h: Double)

private fun equilateral(side: Double): List<P> {
    val r = side / sqrt(3.0)
    return (0 until 3).map { P(r * cos(-PI / 2 + it * 2 * PI / 3), r * sin(-PI / 2 + it * 2 * PI / 3)) }
}

val PRESETS = listOf(
    Preset(R.string.m3d_solid_cube, Shape2D.Rect(0.0, 0.0, 4.0, 4.0), Mode.PRISM, 4.0),
    Preset(R.string.m3d_solid_cuboid, Shape2D.Rect(0.0, 0.0, 6.0, 3.0), Mode.PRISM, 4.0),
    Preset(R.string.m3d_solid_cylinder, Shape2D.Circle(0.0, 0.0, 3.0), Mode.CYLINDER, 6.0),
    Preset(R.string.m3d_solid_cone, Shape2D.Circle(0.0, 0.0, 3.0), Mode.CONE, 6.0),
    Preset(R.string.m3d_solid_sphere, Shape2D.Circle(0.0, 0.0, 3.0), Mode.SPHERE, 6.0),
    Preset(R.string.m3d_solid_pyramid, Shape2D.Rect(0.0, 0.0, 4.0, 4.0), Mode.PYRAMID, 5.0),
    Preset(R.string.m3d_preset_tri_prism, Shape2D.Polygon(equilateral(4.0), R.string.m3d_shape_equilateral_triangle, regular = true), Mode.PRISM, 6.0),
    Preset(
        R.string.m3d_preset_vase, Shape2D.Profile(
            listOf(P(0.0, 4.0), P(2.0, 4.0), P(3.0, 2.5), P(3.2, 0.5), P(2.3, -1.5), P(1.3, -2.5), P(1.4, -3.5), P(2.0, -4.0)),
        ), Mode.REVOLVE, 4.0,
    ),
)

// --- picking ------------------------------------------------------------------

/** Is board point [p] on this shape? Inside for closed shapes, near the line for a profile. */
fun contains(shape: Shape2D, p: P): Boolean {
    if (shape is Shape2D.Profile) {
        return shape.pts.zipWithNext().any { (a, b) -> segmentDistance(p, a, b) < 0.5 }
    }
    // Ray casting: count edge crossings to the right of p.
    val pts = outline(shape)
    var inside = false
    for (i in pts.indices) {
        val a = pts[i]; val b = pts[(i + 1) % pts.size]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
    }
    return inside
}

private fun segmentDistance(p: P, a: P, b: P): Double {
    val dx = b.x - a.x; val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
    return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
}
