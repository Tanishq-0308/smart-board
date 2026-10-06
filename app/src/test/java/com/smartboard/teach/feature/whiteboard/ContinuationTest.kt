package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards how new handwriting joins the last converted box: the same word, the
 * next word (with a space), the next line, or a new box. Too loose and separate
 * notes run together; too tight and "hello world" lands as two boxes.
 */
class ContinuationTest {

    private val font = 40f

    /** Where the handwriting behind the box ended. */
    private val inkRight = 160f

    /** A one-line box at (100, 200); at font 40 a line is 50 tall. */
    private fun box(text: String = "hello") =
        TextBox(id = "b", x = 100f, y = 200f, widthPx = 400f, text = text, colorArgb = 0, fontSizeSp = 20f)

    private fun ink(left: Float, top: Float, right: Float, bottom: Float) = floatArrayOf(left, top, right, bottom)

    private fun of(b: TextBox, bounds: FloatArray, inkFont: Float = font, boxFont: Float = font) =
        continuationOf(b, boxFont, inkRight, bounds, inkFont)

    @Test fun `a pause mid-word joins without a space`() =
        assertEquals(Continuation.SAME_WORD, of(box(), ink(165f, 205f, 240f, 235f)))

    @Test fun `the next word on the same line joins after a space`() =
        assertEquals(Continuation.NEXT_WORD, of(box(), ink(200f, 205f, 300f, 235f)))

    @Test fun `normal word spacing on a board still counts as the same line`() =
        // Two font sizes of gap: the old rule (1.5) split "hello world" here.
        assertEquals(Continuation.NEXT_WORD, of(box(), ink(240f, 205f, 340f, 235f)))

    @Test fun `writing on the next line near the left edge is a new line of the same box`() =
        assertEquals(Continuation.NEXT_LINE, of(box(), ink(110f, 270f, 260f, 300f)))

    @Test fun `the line after a new line continues that line`() =
        assertEquals(Continuation.NEXT_WORD, of(box("hello\nworld"), ink(200f, 255f, 300f, 290f)))

    @Test fun `far to the right is a new box`() = assertNull(of(box(), ink(400f, 205f, 480f, 235f)))

    @Test fun `far below is a new box`() = assertNull(of(box(), ink(110f, 450f, 260f, 480f)))

    @Test fun `below but far to the right is a new box`() = assertNull(of(box(), ink(500f, 270f, 600f, 300f)))

    @Test fun `going back to the left of the box is a new box`() = assertNull(of(box(), ink(10f, 205f, 60f, 235f)))

    @Test fun `the rules scale with font size`() {
        val gap = ink(240f, 202f, 300f, 218f) // 80 px after the ink
        assertEquals(Continuation.NEXT_WORD, of(box(), gap, inkFont = 40f, boxFont = 40f))
        assertNull(of(box(), gap, inkFont = 20f, boxFont = 20f))
    }
}
