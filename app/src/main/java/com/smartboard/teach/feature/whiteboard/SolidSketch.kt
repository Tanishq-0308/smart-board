package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import kotlin.math.abs
import kotlin.math.hypot

/**
 * A hand-sketched cube, cylinder or cone, recognised across several strokes
 * so it can be replaced by the board's clean 3-D figure (dashed hidden edges,
 * exact geometry).
 *
 * Works on what teachers actually draw:
 *  - cylinder: an oval on top and two upright sides (the base may be an oval
 *    or an arc);
 *  - cone: an oval and two sides meeting at a point;
 *  - cube: two overlapping squares, offset diagonally (joining edges optional).
 * Conservative like ShapeRecognizer: anything else gives null and stays ink.
 */
object SolidSketch {

    fun recognise(strokes: List<Stroke>): DrawTool? {
        val parts = strokes.mapNotNull(::partOf)
        val ovals = parts.filter { it.kind == Kind.OVAL }
        val lines = parts.filter { it.kind == Kind.LINE }
        val rects = parts.filter { it.kind == Kind.RECT }
        return when {
            ovals.isNotEmpty() && isCylinder(ovals, lines) -> DrawTool.CYLINDER
            ovals.isNotEmpty() && isCone(ovals, lines) -> DrawTool.CONE
            isCube(rects) -> DrawTool.CUBE
            else -> null
        }
    }

    /** The figure's two-corner box: the union of the sketch's bounds. */
    fun boxOf(strokes: List<Stroke>): FloatArray {
        val b = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        strokes.forEach { s ->
            // Raw bounds, without the pen-width padding Stroke.bounds() adds.
            for (i in 0 until s.pointCount) {
                b[0] = minOf(b[0], s.x(i)); b[1] = minOf(b[1], s.y(i))
                b[2] = maxOf(b[2], s.x(i)); b[3] = maxOf(b[3], s.y(i))
            }
        }
        return b
    }

    private enum class Kind { LINE, OVAL, RECT }

    private class Part(val kind: Kind, val l: Float, val t: Float, val r: Float, val b: Float, val x0: Float, val y0: Float, val x1: Float, val y1: Float) {
        val w get() = r - l
        val h get() = b - t
        val cx get() = (l + r) / 2f
        val cy get() = (t + b) / 2f
    }

    private fun partOf(stroke: Stroke): Part? {
        val n = stroke.pointCount
        if (n < 2) return null
        val box = boxOf(listOf(stroke))
        val (l, t, r, b) = box.toList()
        val x0 = stroke.x(0); val y0 = stroke.y(0); val x1 = stroke.x(n - 1); val y1 = stroke.y(n - 1)
        // Already-clean shapes from the Shape pen.
        when (stroke.tool) {
            DrawTool.LINE -> return Part(Kind.LINE, l, t, r, b, x0, y0, x1, y1)
            DrawTool.CIRCLE, DrawTool.ELLIPSE -> return Part(Kind.OVAL, l, t, r, b, x0, y0, x1, y1)
            DrawTool.RECT -> return Part(Kind.RECT, l, t, r, b, x0, y0, x1, y1)
            DrawTool.PEN -> Unit
            else -> return null
        }
        val diagonal = hypot(r - l, b - t)
        if (diagonal < MIN_SIZE) return null
        var path = 0f
        for (i in 1 until n) path += hypot(stroke.x(i) - stroke.x(i - 1), stroke.y(i) - stroke.y(i - 1))
        val chord = hypot(x1 - x0, y1 - y0)
        if (path <= chord * MAX_WOBBLE) return Part(Kind.LINE, l, t, r, b, x0, y0, x1, y1)
        val closed = chord < diagonal * CLOSED_GAP
        if (!closed) return null
        val shape = ShapeRecognizer.recognise(stroke)?.tool
        val kind = if (shape == DrawTool.RECT) Kind.RECT else Kind.OVAL
        return Part(kind, l, t, r, b, x0, y0, x1, y1)
    }

    /** Two upright sides standing at the oval's left and right ends. */
    private fun isCylinder(ovals: List<Part>, lines: List<Part>): Boolean {
        val top = ovals.minBy { it.cy }
        val sides = lines.filter { abs(it.x1 - it.x0) <= abs(it.y1 - it.y0) * UPRIGHT && it.h >= top.h }
        val left = sides.any { abs(it.cx - top.l) <= top.w * NEAR }
        val right = sides.any { abs(it.cx - top.r) <= top.w * NEAR }
        return left && right
    }

    /** Two sides from the oval's ends that meet at an apex away from it. */
    private fun isCone(ovals: List<Part>, lines: List<Part>): Boolean {
        val base = ovals.maxBy { it.w }
        val tol = base.w * NEAR
        fun ends(p: Part) = listOf(p.x0 to p.y0, p.x1 to p.y1)
        val fromLeft = lines.mapNotNull { p ->
            val e = ends(p); val i = e.indexOfFirst { (x, y) -> hypot(x - base.l, y - base.cy) <= tol }
            if (i < 0) null else e[1 - i]
        }
        val fromRight = lines.mapNotNull { p ->
            val e = ends(p); val i = e.indexOfFirst { (x, y) -> hypot(x - base.r, y - base.cy) <= tol }
            if (i < 0) null else e[1 - i]
        }
        return fromLeft.any { a ->
            fromRight.any { b ->
                hypot(a.first - b.first, a.second - b.second) <= tol &&
                    abs((a.second + b.second) / 2f - base.cy) >= base.h
            }
        }
    }

    /** Two similar squares offset both across and down: the front and back faces. */
    private fun isCube(rects: List<Part>): Boolean {
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            val a = rects[i]; val b = rects[j]
            val similar = abs(a.w - b.w) <= a.w * SIZE_MATCH && abs(a.h - b.h) <= a.h * SIZE_MATCH
            val dx = abs(a.cx - b.cx); val dy = abs(a.cy - b.cy)
            val offset = dx in a.w * MIN_OFFSET..a.w * MAX_OFFSET && dy in a.h * MIN_OFFSET..a.h * MAX_OFFSET
            if (similar && offset) return true
        }
        return false
    }

    private const val MIN_SIZE = 40f
    private const val MAX_WOBBLE = 1.15f
    private const val CLOSED_GAP = 0.3f
    private const val UPRIGHT = 0.3f
    private const val NEAR = 0.25f
    private const val SIZE_MATCH = 0.3f
    private const val MIN_OFFSET = 0.08f
    private const val MAX_OFFSET = 0.75f
}
