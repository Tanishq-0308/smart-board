package com.smartboard.teach.feature.whiteboard

import androidx.annotation.StringRes
import com.smartboard.teach.R
import com.smartboard.teach.domain.model.DrawTool
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The ready-made layouts behind Insert → Area, as on the reference board:
 * courts, fields and a Go board a PE or games teacher draws on.
 *
 * Laid out in metres from the real markings (simplified to the lines a class
 * needs), then scaled to fit the view by [AreaTemplates.place]. They go on
 * the board as ordinary ink, so they can be drawn over, erased in parts,
 * selected and moved like anything else.
 */
enum class AreaTemplate(@StringRes val label: Int) {
    BADMINTON(R.string.area_badminton),
    BASEBALL(R.string.area_baseball),
    BASKETBALL(R.string.area_basketball),
    GO(R.string.area_go),
    HANDBALL(R.string.area_handball),
    ICE_RINK(R.string.area_ice_rink),
}

/**
 * One mark of a layout: a straight [DrawTool.LINE] (two points), a curve
 * drawn as a dense PEN polyline, or a dot (one point). [xy] is x,y pairs;
 * [weight] multiplies the line width (frames are heavier, dots are blobs).
 */
class AreaMark(val tool: DrawTool, val xy: FloatArray, val weight: Float = 1f)

object AreaTemplates {

    /**
     * [template]'s marks scaled to fit inside [maxW] x [maxH] (world units)
     * and centred on ([cx], [cy]), keeping the real proportions.
     */
    fun place(template: AreaTemplate, cx: Float, cy: Float, maxW: Float, maxH: Float): List<AreaMark> {
        val marks = marksOf(template)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        marks.forEach { m ->
            for (i in m.xy.indices step 2) {
                minX = minOf(minX, m.xy[i]); maxX = maxOf(maxX, m.xy[i])
                minY = minOf(minY, m.xy[i + 1]); maxY = maxOf(maxY, m.xy[i + 1])
            }
        }
        val scale = minOf(maxW / (maxX - minX), maxH / (maxY - minY))
        val ox = cx - (minX + maxX) / 2f * scale
        val oy = cy - (minY + maxY) / 2f * scale
        return marks.map { m ->
            AreaMark(m.tool, FloatArray(m.xy.size) { i -> if (i % 2 == 0) ox + m.xy[i] * scale else oy + m.xy[i] * scale }, m.weight)
        }
    }

    fun marksOf(template: AreaTemplate): List<AreaMark> = Builder().apply {
        when (template) {
            AreaTemplate.BADMINTON -> badminton()
            AreaTemplate.BASEBALL -> baseball()
            AreaTemplate.BASKETBALL -> basketball()
            AreaTemplate.GO -> go()
            AreaTemplate.HANDBALL -> handball()
            AreaTemplate.ICE_RINK -> iceRink()
        }
    }.marks

    /** Doubles court, 13.4 x 6.1 m, net across the middle. */
    private fun Builder.badminton() {
        rect(0f, 0f, 13.4f, 6.1f)
        line(0f, 0.46f, 13.4f, 0.46f) // singles sidelines
        line(0f, 5.64f, 13.4f, 5.64f)
        for (x in floatArrayOf(0.76f, 12.64f, 4.72f, 8.68f, 6.7f)) line(x, 0f, x, 6.1f) // long and short service lines, net
        line(0f, 3.05f, 4.72f, 3.05f) // centre lines
        line(8.68f, 3.05f, 13.4f, 3.05f)
    }

    /** 28 x 15 m: keys, free-throw and three-point arcs, hoops, centre circle. */
    private fun Builder.basketball() {
        rect(0f, 0f, 28f, 15f)
        line(14f, 0f, 14f, 15f)
        circle(14f, 7.5f, 1.8f)
        mirrored(28f) {
            rect(0f, 5.05f, 5.8f, 4.9f) // key
            arc(5.8f, 7.5f, 1.8f, -90f, 90f) // free-throw semicircle
            circle(1.575f, 7.5f, 0.225f) // hoop
            line(1.2f, 6.6f, 1.2f, 8.4f) // backboard
            arc(1.575f, 7.5f, 1.25f, -90f, 90f) // restricted area
            val corner = Math.toDegrees(asin((7.5 - 0.9) / 6.75)).toFloat()
            line(0f, 0.9f, 2.99f, 0.9f) // three-point corners
            line(0f, 14.1f, 2.99f, 14.1f)
            arc(1.575f, 7.5f, 6.75f, -corner, corner)
        }
    }

    /** 40 x 20 m: goal areas (6 m) and free-throw lines (9 m), 7 m and 4 m marks. */
    private fun Builder.handball() {
        rect(0f, 0f, 40f, 20f)
        line(20f, 0f, 20f, 20f)
        line(15.5f, 19.6f, 15.5f, 20.4f) // substitution lines
        line(24.5f, 19.6f, 24.5f, 20.4f)
        rect(18.5f, 20.6f, 3f, 0.8f) // table
        mirrored(40f) {
            rect(-1f, 8.5f, 1f, 3f) // goal
            arc(0f, 8.5f, 6f, -90f, 0f) // goal area
            line(6f, 8.5f, 6f, 11.5f)
            arc(0f, 11.5f, 6f, 0f, 90f)
            val edge = Math.toDegrees(asin(8.5 / 9.0)).toFloat()
            arc(0f, 8.5f, 9f, -edge, 0f) // free-throw line, clipped to the court
            line(9f, 8.5f, 9f, 11.5f)
            arc(0f, 11.5f, 9f, 0f, edge)
            line(7f, 9.5f, 7f, 10.5f) // 7 m line
            line(4f, 9.85f, 4f, 10.15f) // goalkeeper's 4 m line
        }
    }

    /** 60 x 30 m rink, rounded corners, blue and red lines, five circles and nine dots. */
    private fun Builder.iceRink() {
        val r = 8.5f
        line(r, 0f, 60f - r, 0f)
        line(r, 30f, 60f - r, 30f)
        line(0f, r, 0f, 30f - r)
        line(60f, r, 60f, 30f - r)
        arc(r, r, r, 180f, 270f)
        arc(60f - r, r, r, 270f, 360f)
        arc(60f - r, 30f - r, r, 0f, 90f)
        arc(r, 30f - r, r, 90f, 180f)
        line(20f, 0f, 20f, 30f) // blue lines
        line(40f, 0f, 40f, 30f)
        line(30f, 0f, 30f, 30f) // centre line
        circle(30f, 15f, 4.5f)
        dot(30f, 15f)
        for (x in floatArrayOf(24f, 36f)) for (y in floatArrayOf(7.5f, 22.5f)) dot(x, y) // neutral zone
        rect(17f, -1.2f, 10f, 1.2f) // benches
        rect(33f, -1.2f, 10f, 1.2f)
        rect(25f, 30f, 10f, 1.2f) // penalty box
        arc(30f, 30f, 3f, 180f, 360f) // referee's crease
        mirrored(60f) {
            // Goal line, inside the rounded corners.
            val inset = r - sqrt(r * r - (r - 4f) * (r - 4f))
            line(4f, inset, 4f, 30f - inset)
            arc(4f, 15f, 1.8f, -90f, 90f) // crease
            rect(2.9f, 14.1f, 1.1f, 1.8f) // net
            for (y in floatArrayOf(7.5f, 22.5f)) {
                circle(10f, y, 4.5f) // end-zone faceoff circles
                dot(10f, y)
                for (dx in floatArrayOf(-0.9f, 0.9f)) {
                    line(10f + dx, y - 4.5f, 10f + dx, y - 5.4f) // hash marks
                    line(10f + dx, y + 4.5f, 10f + dx, y + 5.4f)
                }
            }
        }
    }

    /** A 19 x 19 board in its frame, with the nine star points. */
    private fun Builder.go() {
        for (i in 0..18) {
            line(i.toFloat(), 0f, i.toFloat(), 18f)
            line(0f, i.toFloat(), 18f, i.toFloat())
        }
        rect(-1f, -1f, 20f, 20f, weight = 2f)
        for (x in intArrayOf(3, 9, 15)) for (y in intArrayOf(3, 9, 15)) dot(x.toFloat(), y.toFloat(), weight = 3.5f)
    }

    /** Home plate at the bottom: diamond, foul lines, outfield fence, infield arc, mound. */
    private fun Builder.baseball() {
        val half = 27.43f / sqrt(2f) // half the diagonal of the 27.43 m base square
        line(0f, 0f, half, -half)
        line(half, -half, 0f, -2f * half)
        line(0f, -2f * half, -half, -half)
        line(-half, -half, 0f, 0f)
        val foul = 100f / sqrt(2f)
        line(0f, 0f, foul, -foul) // foul lines
        line(0f, 0f, -foul, -foul)
        arc(0f, 0f, 100f, -135f, -45f) // outfield fence
        arc(0f, -18.4f, 29f, -161.7f, -18.3f) // edge of the infield grass, foul line to foul line
        circle(0f, -18.4f, 2.7f) // pitcher's mound
        rect(-0.3f, -18.5f, 0.6f, 0.15f) // rubber
        circle(0f, 0f, 4f) // home plate circle
        rect(-1.6f, -0.9f, 1f, 1.8f) // batter's boxes
        rect(0.6f, -0.9f, 1f, 1.8f)
        circle(-12f, 6f, 1.5f) // on-deck circles
        circle(12f, 6f, 1.5f)
    }

    private class Builder {
        val marks = mutableListOf<AreaMark>()
        private var mirrorWidth: Float? = null

        private fun x(v: Float) = mirrorWidth?.let { it - v } ?: v

        fun line(x0: Float, y0: Float, x1: Float, y1: Float, weight: Float = 1f) {
            marks += AreaMark(DrawTool.LINE, floatArrayOf(x(x0), y0, x(x1), y1), weight)
        }

        fun rect(x0: Float, y0: Float, w: Float, h: Float, weight: Float = 1f) {
            line(x0, y0, x0 + w, y0, weight)
            line(x0 + w, y0, x0 + w, y0 + h, weight)
            line(x0 + w, y0 + h, x0, y0 + h, weight)
            line(x0, y0 + h, x0, y0, weight)
        }

        /**
         * Degrees, y-down (0 points right, 90 down), swept from [from] to
         * [to]. Mirrored copies sweep the mirror image, so one half-court
         * description draws both ends.
         */
        fun arc(cx: Float, cy: Float, r: Float, from: Float, to: Float) {
            val steps = ((to - from) / ARC_STEP_DEG).toInt().coerceAtLeast(2)
            val xy = FloatArray((steps + 1) * 2)
            for (i in 0..steps) {
                val a = Math.toRadians((from + (to - from) * i / steps).toDouble())
                xy[i * 2] = x(cx + (cos(a) * r).toFloat())
                xy[i * 2 + 1] = cy + (sin(a) * r).toFloat()
            }
            marks += AreaMark(DrawTool.PEN, xy)
        }

        fun circle(cx: Float, cy: Float, r: Float) = arc(cx, cy, r, 0f, 360f)

        fun dot(px: Float, py: Float, weight: Float = 2.5f) {
            marks += AreaMark(DrawTool.PEN, floatArrayOf(x(px), py), weight)
        }

        /** Draws [half] once as written and once mirrored across a court [width] long. */
        fun mirrored(width: Float, half: Builder.() -> Unit) {
            half()
            mirrorWidth = width
            half()
            mirrorWidth = null
        }
    }

    private const val ARC_STEP_DEG = 3f
}
