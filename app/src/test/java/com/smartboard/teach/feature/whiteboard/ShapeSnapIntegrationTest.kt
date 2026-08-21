package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Substitution behaviour: which strokes get snapped, and what is preserved
 * when they are.
 */
class ShapeSnapIntegrationTest {

    private fun circleStroke(
        tool: DrawTool = DrawTool.PEN,
        colour: Int = 0xFF2F6FED.toInt(),
        width: Float = 9f,
    ): Stroke {
        val pts = FloatArray(48 * 3)
        for (i in 0 until 48) {
            val a = 2 * PI * i / 48
            pts[i * 3] = (200 + 100 * cos(a)).toFloat()
            pts[i * 3 + 1] = (200 + 100 * sin(a)).toFloat()
            pts[i * 3 + 2] = 1f
        }
        return Stroke("original-id", tool, StrokeStyle(colour, width), pts)
    }

    private fun stateWith(recognition: Boolean) = BoardState().apply {
        shapeRecognition = recognition
    }

    @Test
    fun `a pen circle is snapped to a circle shape`() {
        val result = maybeSnapToShape(stateWith(true), circleStroke())
        assertEquals(DrawTool.CIRCLE, result.tool)
        // Shapes store exactly two points.
        assertEquals(2, result.pointCount)
    }

    @Test
    fun `snapping is skipped when the setting is off`() {
        val original = circleStroke()
        val result = maybeSnapToShape(stateWith(false), original)
        assertSame("must return the very same stroke untouched", original, result)
    }

    @Test
    fun `the highlighter is never snapped`() {
        val original = circleStroke(tool = DrawTool.HIGHLIGHTER)
        assertSame(original, maybeSnapToShape(stateWith(true), original))
    }

    @Test
    fun `an existing shape stroke is not re-snapped`() {
        val original = circleStroke(tool = DrawTool.RECT)
        assertSame(original, maybeSnapToShape(stateWith(true), original))
    }

    @Test
    fun `the snapped shape keeps the teacher's colour and width`() {
        val original = circleStroke(colour = 0xFFC8382F.toInt(), width = 14f)
        val result = maybeSnapToShape(stateWith(true), original)
        assertEquals(0xFFC8382F.toInt(), result.style.colorArgb)
        assertEquals(14f, result.style.baseWidthPx, 0.01f)
    }

    @Test
    fun `the snapped shape keeps the original id so undo is one step`() {
        val result = maybeSnapToShape(stateWith(true), circleStroke())
        assertEquals("original-id", result.id)
    }

    @Test
    fun `an unrecognisable stroke is returned unchanged`() {
        val squiggle = FloatArray(30 * 3)
        for (i in 0 until 30) {
            squiggle[i * 3] = (i * 7 % 53).toFloat()
            squiggle[i * 3 + 1] = (i * 13 % 37).toFloat()
            squiggle[i * 3 + 2] = 1f
        }
        val original = Stroke("x", DrawTool.PEN, StrokeStyle(0, 4f), squiggle)
        assertSame(original, maybeSnapToShape(stateWith(true), original))
    }
}
