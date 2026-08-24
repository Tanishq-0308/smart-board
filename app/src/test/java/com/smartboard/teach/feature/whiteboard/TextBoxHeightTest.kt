package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.TextBox
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Guards the sp-to-world conversion for text boxes.
 *
 * Treating a box's sp font size as world pixels made its rect roughly a third
 * of its real height on a 2.6x-density panel. Nothing looked wrong — the text
 * still drew correctly — but taps below the first line missed it entirely and
 * fit-to-content cropped the bottom off.
 */
class TextBoxHeightTest {

    /** A typical panel density: 1sp is 2.6 world px. */
    private val scale = 2.6f

    @Before
    fun setUp() {
        Selection.spToWorldPx = scale
    }

    @After
    fun tearDown() {
        Selection.spToWorldPx = 1f
    }

    private fun box(text: String, fontSizeSp: Float = 20f) = TextBox(
        id = "b",
        x = 100f,
        y = 200f,
        widthPx = 300f,
        text = text,
        colorArgb = 0xFF000000.toInt(),
        fontSizeSp = fontSizeSp,
    )

    @Test
    fun `height scales sp into world pixels`() {
        // 20sp * 2.6 * 1.3 line height = 67.6
        assertEquals(67.6f, Selection.textBoxHeight(box("hi")), 0.01f)
    }

    @Test
    fun `every wrapped line adds its own height`() {
        val one = Selection.textBoxHeight(box("a"))
        val three = Selection.textBoxHeight(box("a\nb\nc"))
        assertEquals(one * 3f, three, 0.01f)
    }

    @Test
    fun `a tap on the last line still hits the box`() {
        val b = box("a\nb")
        // Just inside the bottom edge. With sp treated as px this point sat
        // far below the rect and the tap fell through to the canvas.
        val y = b.y + Selection.textBoxHeight(b) - 1f
        assertNotNull(Selection.textBoxAt(listOf(b), b.x + 10f, y))
    }

    @Test
    fun `a tap below the box still misses`() {
        val b = box("a\nb")
        val y = b.y + Selection.textBoxHeight(b) + 1f
        assertNull(Selection.textBoxAt(listOf(b), b.x + 10f, y))
    }

    @Test
    fun `bounds cover the full text height`() {
        val b = box("a\nb")
        val bounds = Selection.boundsOf(emptyList(), listOf(b))
        assertEquals(b.y + Selection.textBoxHeight(b), bounds[3], 0.01f)
    }
}
