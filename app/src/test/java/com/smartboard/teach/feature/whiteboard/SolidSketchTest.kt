package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class SolidSketchTest {

    private var n = 0
    private fun stroke(points: List<Pair<Float, Float>>) = Stroke(
        "s${n++}", DrawTool.PEN, StrokeStyle(0xFF000000.toInt(), 4f),
        points.flatMap { listOf(it.first, it.second, 1f) }.toFloatArray(),
    )

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float) =
        stroke((0..20).map { i -> val t = i / 20f; (x0 + (x1 - x0) * t) to (y0 + (y1 - y0) * t) })

    /** A freehand oval, closed, centred at (cx, cy). */
    private fun oval(cx: Float, cy: Float, rx: Float, ry: Float) =
        stroke((0..48).map { i -> val a = i / 48.0 * 2 * Math.PI; (cx + rx * cos(a)).toFloat() to (cy + ry * sin(a)).toFloat() })

    private fun square(l: Float, t: Float, s: Float) = stroke(
        listOf(l to t, l + s to t, l + s to t + s, l to t + s, l to t + 1f).let { corners ->
            corners.zipWithNext().flatMap { (a, b) -> (0..10).map { i -> val k = i / 10f; (a.first + (b.first - a.first) * k) to (a.second + (b.second - a.second) * k) } }
        },
    )

    @Test fun cylinder() = assertEquals(
        DrawTool.CYLINDER,
        SolidSketch.recognise(listOf(oval(400f, 200f, 150f, 40f), line(250f, 200f, 252f, 600f), line(550f, 200f, 548f, 600f), oval(400f, 600f, 150f, 40f))),
    )

    @Test fun cone() = assertEquals(
        DrawTool.CONE,
        SolidSketch.recognise(listOf(oval(400f, 600f, 150f, 40f), line(250f, 600f, 402f, 200f), line(550f, 600f, 398f, 202f))),
    )

    @Test fun cube() = assertEquals(
        DrawTool.CUBE,
        SolidSketch.recognise(listOf(square(200f, 300f, 300f), square(300f, 200f, 300f), line(200f, 300f, 300f, 200f))),
    )

    @Test fun boxCoversTheWholeSketch() {
        val box = SolidSketch.boxOf(listOf(oval(400f, 200f, 150f, 40f), line(250f, 200f, 252f, 600f)))
        assertEquals(250f, box[0], 1f); assertEquals(160f, box[1], 1f); assertEquals(600f, box[3], 1f)
    }

    @Test fun otherDrawingsStayInk() {
        assertNull(SolidSketch.recognise(listOf(oval(400f, 200f, 150f, 40f))))
        assertNull(SolidSketch.recognise(listOf(line(100f, 100f, 500f, 100f), line(100f, 100f, 100f, 500f))))
        // Two squares side by side are not a cube's faces.
        assertNull(SolidSketch.recognise(listOf(square(100f, 100f, 200f), square(500f, 100f, 200f))))
        // An oval with two short ticks is not a cylinder.
        assertNull(SolidSketch.recognise(listOf(oval(400f, 200f, 150f, 40f), line(250f, 200f, 250f, 230f), line(550f, 200f, 550f, 230f))))
    }
}
