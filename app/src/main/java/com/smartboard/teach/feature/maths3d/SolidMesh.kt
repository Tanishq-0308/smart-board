package com.smartboard.teach.feature.maths3d

import com.smartboard.teach.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** 3D point, y UP. Floor is the x/z plane, with board y mapped onto z. */
data class V(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V) = V(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V) = V(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V(x * s, y * s, z * s)
    infix fun dot(o: V) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: V) = V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(this dot this)
    fun normalized() = length().let { if (it < 1e-9f) this else this * (1f / it) }
    fun lerp(o: V, t: Float) = this + (o - this) * t
}

private fun v(x: Double, y: Double, z: Double) = V(x.toFloat(), y.toFloat(), z.toFloat())

/** A straight overlay line — dimension lines and the revolve profile. */
data class Line(val pts: List<V>, val teal: Boolean)

data class Label(val pos: V, val text: Txt, val teal: Boolean, val small: Boolean = false)

/** A crease edge, with the outward normals of the two faces that meet there. */
data class Edge(val a: V, val b: V, val n1: V, val n2: V)

class Solid(
    /** 9 floats per triangle. Winding is not relied on: shading is double-sided. */
    val tris: FloatArray,
    val edges: List<Edge>,
    val lines: List<Line>,
    val labels: List<Label>,
) {
    val triangleCount get() = tris.size / 9
}

/**
 * The mesh for a shape in a mode, at growth [t] in [0, 1] (the "Grow"
 * animation). Port of the prototype's buildSolid; dimension lines sit where
 * the prototype put them.
 */
fun buildSolid(shape: Shape2D, mode: Mode, h: Double, t: Double = 1.0): Solid {
    val b = MeshBuilder()
    val hh = max(0.001, h * t)
    when (mode) {
        Mode.CYLINDER -> {
            val r = (shape as Shape2D.Circle).r
            b.cylinder(r, r, hh)
            b.dim(v(0.0, hh, 0.0), v(r, hh, 0.0), "r = ${fmt(r)}")
            b.dim(v(r * 1.08, 0.0, 0.0), v(r * 1.08, hh, 0.0), "h = ${fmt(hh)}", teal = false)
        }
        Mode.CONE -> {
            val r = (shape as Shape2D.Circle).r
            b.cylinder(r, 0.0, hh)
            b.dim(v(0.0, 0.0, 0.0), v(r, 0.0, 0.0), "r = ${fmt(r)}")
            b.dim(v(0.0, 0.0, 0.0), v(0.0, hh, 0.0), "h = ${fmt(hh)}", teal = false, at = 0.45f)
            b.dim(v(-r, 0.0, 0.0), v(0.0, hh, 0.0), "l = ${fmt(hypot(r, hh))}")
        }
        Mode.SPHERE -> {
            val full = (shape as Shape2D.Circle).r
            b.sphere(full * max(0.001, t), full)
            b.dim(v(0.0, full, 0.0), v(full * max(0.001, t), full, 0.0), "r = ${fmt(full)}")
        }
        Mode.PRISM -> {
            val poly = basePoly(shape)
            b.prism(poly, hh)
            if (shape is Shape2D.Rect) {
                val l = shape.w / 2; val w = shape.h / 2; val o = 0.25
                b.dim(v(-l, 0.0, w + o), v(l, 0.0, w + o), "l = ${fmt(shape.w)}")
                b.dim(v(l + o, 0.0, -w), v(l + o, 0.0, w), "b = ${fmt(shape.h)}")
                b.dim(v(l + o, 0.0, w + o), v(l + o, hh, w + o), "h = ${fmt(hh)}", teal = false)
            } else {
                val p = poly[0]
                b.dim(v(p.x, 0.0, p.y), v(p.x, hh, p.y), "h = ${fmt(hh)}", teal = false)
                if (poly.size <= 8) b.edgeLabels(poly)
            }
        }
        Mode.PYRAMID -> {
            val poly = basePoly(shape)
            b.pyramid(poly, hh)
            b.dim(v(0.0, 0.0, 0.0), v(0.0, hh, 0.0), "h = ${fmt(hh)}", teal = false, at = 0.4f)
            if (isRegular(shape)) {
                val p = poly[0]; val q = poly[1]
                b.dim(v((p.x + q.x) / 2, 0.0, (p.y + q.y) / 2), v(0.0, hh, 0.0), Txt.Res(R.string.m3d_label_slant))
            }
            if (poly.size <= 8) b.edgeLabels(poly)
        }
        Mode.REVOLVE -> {
            val loop = revolveLoop(shape)
            val phi = max(0.001, 2 * PI * t)
            b.lathe(loop, phi)
            b.lines += Line(loop.map { v(it.r * sin(phi), it.y, it.r * cos(phi)) }, teal = true)
            val st = revolveStats(loop)
            val y0 = loop.minOf { it.y }
            b.dim(v(0.0, y0 - 0.3, 0.0), v(0.0, y0 + st.height + 0.3, 0.0), "h = ${fmt(st.height)}", teal = false, at = 0.9f)
            if (shape is Shape2D.Circle) {
                val bigR = abs(shape.cx); val yc = shape.r
                b.dim(v(0.0, yc, 0.0), v(0.0, yc, bigR), "R = ${fmt(bigR)}")
                b.dim(v(0.0, yc, bigR), v(0.0, yc, bigR + shape.r), "r = ${fmt(shape.r)}", teal = false)
            } else {
                val widest = loop.maxBy { it.r }
                b.dim(v(0.0, widest.y, 0.0), v(0.0, widest.y, widest.r), "r = ${fmt(widest.r)}")
            }
        }
    }
    // Smooth solids get no crease lines, as in the prototype.
    val creases = mode != Mode.REVOLVE && mode != Mode.SPHERE
    return b.build(creases)
}

/**
 * [buildSolid] moved to where the shape was drawn: board x → x, board y → z.
 * A revolve stays on the axis, since the axis is what it turned around.
 * [overlays] false drops the dimension lines and labels — only the selected
 * solid carries them, or several solids would bury the view in numbers.
 */
fun placedSolid(shape: Shape2D, mode: Mode, h: Double, t: Double = 1.0, overlays: Boolean = true): Solid {
    val s = buildSolid(shape, mode, h, t)
    val o = when {
        mode == Mode.REVOLVE -> P(0.0, 0.0)
        shape is Shape2D.Rect -> P(shape.cx, shape.cy)
        shape is Shape2D.Circle -> P(shape.cx, shape.cy)
        else -> centroid(outline(shape))
    }
    val d = V(o.x.toFloat(), 0f, o.y.toFloat())
    if (d == V(0f, 0f, 0f) && overlays) return s
    val tris = s.tris.copyOf()
    for (i in tris.indices step 3) { tris[i] += d.x; tris[i + 2] += d.z }
    return Solid(
        tris,
        s.edges.map { it.copy(a = it.a + d, b = it.b + d) },
        if (overlays) s.lines.map { l -> l.copy(pts = l.pts.map { it + d }) } else emptyList(),
        if (overlays) s.labels.map { it.copy(pos = it.pos + d) } else emptyList(),
    )
}

/** Several solids drawn as one scene. */
fun mergeSolids(parts: List<Solid>): Solid {
    if (parts.size == 1) return parts[0]
    val tris = FloatArray(parts.sumOf { it.tris.size })
    var at = 0
    for (p in parts) { p.tris.copyInto(tris, at); at += p.tris.size }
    return Solid(tris, parts.flatMap { it.edges }, parts.flatMap { it.lines }, parts.flatMap { it.labels })
}

/** Base polygon centred on its centroid, in floor coords: P.x → x, P.y → z. */
fun basePoly(shape: Shape2D): List<P> {
    val pts = outline(shape)
    val c = if (shape is Shape2D.Rect) P(shape.cx, shape.cy) else centroid(pts)
    return pts.map { P(it.x - c.x, it.y - c.y) }
}

/** Ear-clipping triangulation of a simple polygon. Returns index triples. */
fun triangulate(poly: List<P>): List<IntArray> {
    val idx = poly.indices.toMutableList()
    if (signedArea(poly) < 0) idx.reverse()
    val out = mutableListOf<IntArray>()
    fun cross(a: P, b: P, c: P) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    fun inside(p: P, a: P, b: P, c: P) = cross(a, b, p) >= 0 && cross(b, c, p) >= 0 && cross(c, a, p) >= 0
    var guard = 0
    while (idx.size > 3 && guard++ < 10_000) {
        var clipped = false
        for (i in idx.indices) {
            val ia = idx[(i - 1 + idx.size) % idx.size]; val ib = idx[i]; val ic = idx[(i + 1) % idx.size]
            val a = poly[ia]; val b = poly[ib]; val c = poly[ic]
            if (cross(a, b, c) <= 1e-12) continue
            if (idx.any { it != ia && it != ib && it != ic && inside(poly[it], a, b, c) }) continue
            out += intArrayOf(ia, ib, ic)
            idx.removeAt(i)
            clipped = true
            break
        }
        // Self-intersecting outline: fall back to a fan rather than loop forever.
        if (!clipped) break
    }
    for (i in 1 until idx.size - 1) out += intArrayOf(idx[0], idx[i], idx[i + 1])
    return out
}

private class MeshBuilder {
    val tris = ArrayList<Float>()
    val lines = mutableListOf<Line>()
    val labels = mutableListOf<Label>()

    fun tri(a: V, b: V, c: V) {
        for (p in arrayOf(a, b, c)) { tris += p.x; tris += p.y; tris += p.z }
    }

    fun quad(a: V, b: V, c: V, d: V) { tri(a, b, c); tri(a, c, d) }

    fun dim(a: V, b: V, text: String, teal: Boolean = true, at: Float = 0.5f) = dim(a, b, text.raw, teal, at)

    fun dim(a: V, b: V, text: Txt, teal: Boolean = true, at: Float = 0.5f) {
        lines += Line(listOf(a, b), teal)
        labels += Label(a.lerp(b, at), text, teal)
    }

    fun edgeLabels(poly: List<P>) {
        val cx = poly.sumOf { it.x } / poly.size; val cz = poly.sumOf { it.y } / poly.size
        for (i in poly.indices) {
            val p = poly[i]; val q = poly[(i + 1) % poly.size]
            val mx = (p.x + q.x) / 2; val mz = (p.y + q.y) / 2
            val len = hypot(mx - cx, mz - cz).takeIf { it > 0 } ?: 1.0
            labels += Label(v(mx + (mx - cx) / len * 0.4, 0.0, mz + (mz - cz) / len * 0.4), fmt(dist(p, q)).raw, teal = true, small = true)
        }
    }

    /** Frustum from radius [r0] at y=0 to [r1] at y=[h]; r1 = 0 makes a cone. */
    fun cylinder(r0: Double, r1: Double, h: Double, seg: Int = 48) {
        val bottom = v(0.0, 0.0, 0.0); val top = v(0.0, h, 0.0)
        for (i in 0 until seg) {
            val a0 = 2 * PI * i / seg; val a1 = 2 * PI * (i + 1) / seg
            val b0 = v(r0 * sin(a0), 0.0, r0 * cos(a0)); val b1 = v(r0 * sin(a1), 0.0, r0 * cos(a1))
            val t0 = v(r1 * sin(a0), h, r1 * cos(a0)); val t1 = v(r1 * sin(a1), h, r1 * cos(a1))
            if (r1 > 0) { quad(b0, b1, t1, t0); tri(top, t0, t1) } else tri(b0, b1, top)
            tri(bottom, b1, b0)
        }
    }

    // ponytail: 32x20 is coarse next to three.js's 64x40, kept low because
    // every triangle is rasterised on the CPU each frame.
    fun sphere(r: Double, cy: Double, seg: Int = 32, rings: Int = 20) {
        fun p(i: Int, j: Int): V {
            val th = PI * j / rings; val ph = 2 * PI * i / seg
            return v(r * sin(th) * sin(ph), cy + r * cos(th), r * sin(th) * cos(ph))
        }
        for (j in 0 until rings) for (i in 0 until seg) {
            val a = p(i, j); val b = p(i + 1, j); val c = p(i + 1, j + 1); val d = p(i, j + 1)
            when (j) {
                0 -> tri(a, c, d)
                rings - 1 -> tri(a, b, c)
                else -> quad(a, b, c, d)
            }
        }
    }

    fun prism(poly: List<P>, h: Double) {
        val bot = poly.map { v(it.x, 0.0, it.y) }
        val top = poly.map { v(it.x, h, it.y) }
        for ((a, b, c) in triangulate(poly)) { tri(bot[a], bot[b], bot[c]); tri(top[a], top[b], top[c]) }
        for (i in poly.indices) {
            val j = (i + 1) % poly.size
            quad(bot[i], bot[j], top[j], top[i])
        }
    }

    fun pyramid(poly: List<P>, h: Double) {
        val bot = poly.map { v(it.x, 0.0, it.y) }
        val apex = v(0.0, h, 0.0)
        for ((a, b, c) in triangulate(poly)) tri(bot[a], bot[b], bot[c])
        for (i in poly.indices) tri(bot[i], bot[(i + 1) % poly.size], apex)
    }

    fun lathe(loop: List<RY>, phi: Double, seg: Int = 48) {
        val steps = max(1, (seg * phi / (2 * PI)).toInt())
        fun p(q: RY, a: Double) = v(q.r * sin(a), q.y, q.r * cos(a))
        for (s in 0 until steps) {
            val a0 = phi * s / steps; val a1 = phi * (s + 1) / steps
            for (i in 1 until loop.size) {
                val q0 = loop[i - 1]; val q1 = loop[i]
                val a = p(q0, a0); val b = p(q0, a1); val c = p(q1, a1); val d = p(q1, a0)
                if (q0.r > 0) tri(a, b, c)
                if (q1.r > 0) tri(a, c, d)
            }
        }
    }

    fun build(creases: Boolean): Solid {
        val arr = tris.toFloatArray()
        return Solid(arr, if (creases) creaseEdges(arr) else emptyList(), lines, labels)
    }
}

/**
 * Edges where faces meet at more than ~25° — what three.js EdgesGeometry
 * draws. Normals are flipped to point away from the solid's centre so the
 * renderer can hide edges behind the solid.
 *
 * ponytail: "away from the centre" is only exact for convex solids; a prism
 * over a concave free shape may show or hide the odd inner crease wrongly.
 */
fun creaseEdges(tris: FloatArray, thresholdDeg: Double = 25.0): List<Edge> {
    val n = tris.size / 9
    fun vert(t: Int, k: Int) = V(tris[t * 9 + k * 3], tris[t * 9 + k * 3 + 1], tris[t * 9 + k * 3 + 2])
    var centre = V(0f, 0f, 0f)
    for (t in 0 until n) for (k in 0 until 3) centre += vert(t, k)
    centre *= 1f / (n * 3).coerceAtLeast(1)

    val normals = Array(n) { t ->
        val a = vert(t, 0); val b = vert(t, 1); val c = vert(t, 2)
        val nn = ((b - a) cross (c - a)).normalized()
        val mid = (a + b + c) * (1f / 3)
        if (nn dot (mid - centre) < 0) nn * -1f else nn
    }
    fun key(p: V) = Triple(Math.round(p.x * 1e4f), Math.round(p.y * 1e4f), Math.round(p.z * 1e4f))

    val keyOrder = compareBy<Triple<Int, Int, Int>>({ it.first }, { it.second }, { it.third })
    val seen = HashMap<Pair<Triple<Int, Int, Int>, Triple<Int, Int, Int>>, Int>()
    val out = mutableListOf<Edge>()
    val cosLimit = cos(thresholdDeg * PI / 180).toFloat()
    for (t in 0 until n) {
        if (normals[t].length() < 0.5f) continue // degenerate triangle
        for (k in 0 until 3) {
            val a = vert(t, k); val b = vert(t, (k + 1) % 3)
            val ka = key(a); val kb = key(b)
            val edgeKey = if (keyOrder.compare(ka, kb) <= 0) ka to kb else kb to ka
            val other = seen.remove(edgeKey)
            if (other == null) { seen[edgeKey] = t; continue }
            if ((normals[t] dot normals[other]) < cosLimit) out += Edge(a, b, normals[t], normals[other])
        }
    }
    // Unpaired edges are open boundaries — always a crease.
    for ((k, t) in seen) {
        val a = V(k.first.first / 1e4f, k.first.second / 1e4f, k.first.third / 1e4f)
        val b = V(k.second.first / 1e4f, k.second.second / 1e4f, k.second.third / 1e4f)
        out += Edge(a, b, normals[t], normals[t])
    }
    return out
}
