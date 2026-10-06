package com.smartboard.teach.feature.whiteboard.container

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TableSketchTest {

    private var n = 0
    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, wobble: Float = 3f): Stroke {
        val pts = ArrayList<Float>()
        for (i in 0..20) {
            val t = i / 20f
            val jitter = if (i % 2 == 0) wobble else -wobble
            val horizontal = kotlin.math.abs(x1 - x0) > kotlin.math.abs(y1 - y0)
            pts += x0 + (x1 - x0) * t + if (horizontal) 0f else jitter
            pts += y0 + (y1 - y0) * t + if (horizontal) jitter else 0f
            pts += 1f
        }
        return Stroke("s${n++}", DrawTool.PEN, StrokeStyle(0xFF000000.toInt(), 4f), pts.toFloatArray())
    }

    /** A 2-row, 3-column hand-drawn grid, slightly wobbly and not quite aligned. */
    private fun grid(): List<Stroke> = listOf(
        line(100f, 100f, 702f, 104f), line(98f, 300f, 700f, 296f), line(103f, 501f, 698f, 499f),
        line(100f, 100f, 102f, 500f), line(300f, 97f, 302f, 503f), line(501f, 102f, 499f, 498f), line(700f, 100f, 703f, 502f),
    )

    @Test
    fun handDrawnGridIsATable() {
        val result = TableSketch.recognise(grid())
        assertNotNull(result)
        assertEquals(2, result!!.rows)
        assertEquals(3, result.cols)
        assertEquals(7, result.lineIds.size)
    }

    @Test
    fun writingInsideMovesToItsCell() {
        val table = TableSketch.toTable(TableSketch.recognise(grid())!!)
        val word = line(350f, 380f, 440f, 390f) // inside row 1, column 1
        assertEquals(1 * 3 + 1, TableSketch.cellFor(table, word))
        assertEquals(-1, TableSketch.cellFor(table, line(900f, 900f, 990f, 905f)))
    }

    @Test
    fun ruleDrawnInTwoPiecesIsOneRule() {
        val strokes = grid().toMutableList()
        strokes[1] = line(98f, 300f, 400f, 298f)
        strokes += line(398f, 302f, 700f, 299f)
        val result = TableSketch.recognise(strokes)!!
        assertEquals(2, result.rows)
        assertEquals(8, result.lineIds.size)
    }

    @Test
    fun aFewStraightLinesInASketchAreNotATable() {
        assertNull(TableSketch.recognise(listOf(line(100f, 100f, 600f, 100f), line(100f, 100f, 100f, 400f))))
        // Short crossing ticks do not make a grid.
        assertNull(TableSketch.recognise(listOf(
            line(100f, 100f, 700f, 100f), line(100f, 400f, 180f, 400f),
            line(100f, 100f, 100f, 400f), line(700f, 100f, 700f, 400f),
        )))
    }

    @Test
    fun curvesAreNotRules() {
        val curve = Stroke("c", DrawTool.PEN, StrokeStyle(0, 4f),
            FloatArray(63) { i -> when (i % 3) { 0 -> 100f + i * 10f; 1 -> 100f + kotlin.math.sin(i / 3f) * 80f; else -> 1f } })
        assertNull(TableSketch.recognise(listOf(curve) + grid().drop(3).take(2)))
    }
}
