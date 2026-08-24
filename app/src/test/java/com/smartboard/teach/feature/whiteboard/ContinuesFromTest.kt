package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.TextBox
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the rule that decides whether new handwriting continues the last
 * conversion or starts a new one.
 *
 * Getting this wrong is only visible after a pause mid-word, which is exactly
 * when a teacher is least likely to be watching for it: too loose and separate
 * words run together, too tight and "hello" lands as "h" and "ello".
 */
class ContinuesFromTest {

    private val font = 40f

    /** Where the handwriting behind [box] ended — its real right edge. */
    private val INK_RIGHT = 160f

    /** A box at (100, 200), 60 wide — roughly one written letter. */
    private fun box() = TextBox(
        id = "b",
        x = 100f,
        y = 200f,
        widthPx = 60f,
        text = "h",
        colorArgb = 0xFF000000.toInt(),
        fontSizeSp = 20f,
    )

    private fun bounds(left: Float, top: Float, right: Float, bottom: Float) =
        floatArrayOf(left, top, right, bottom)

    @Test
    fun `ink just after the box on the same line continues it`() {
        assertTrue(continuesFrom(box(), INK_RIGHT, bounds(175f, 205f, 260f, 235f), font))
    }

    @Test
    fun `ink touching the box continues it`() {
        assertTrue(continuesFrom(box(), INK_RIGHT, bounds(160f, 205f, 240f, 235f), font))
    }

    @Test
    fun `ink far to the right starts a new word`() {
        // Well beyond CONTINUE_GAP_RATIO font sizes.
        assertFalse(continuesFrom(box(), INK_RIGHT, bounds(400f, 205f, 480f, 235f), font))
    }

    @Test
    fun `ink on the line below starts a new word`() {
        assertFalse(continuesFrom(box(), INK_RIGHT, bounds(175f, 300f, 260f, 340f), font))
    }

    @Test
    fun `ink well to the left starts a new word`() {
        // Going back to annotate earlier work must not append to it.
        assertFalse(continuesFrom(box(), INK_RIGHT, bounds(10f, 205f, 60f, 235f), font))
    }

    @Test
    fun `a slight overlap back into the box still continues it`() {
        // Handwriting is not precise; a letter that tucks under the previous
        // one is still the same word.
        assertTrue(continuesFrom(box(), INK_RIGHT, bounds(150f, 205f, 230f, 235f), font))
    }

    @Test
    fun `the gap rule scales with font size`() {
        // Ink starting 50px past the box, on the same line either way.
        val ink = bounds(210f, 202f, 280f, 218f)
        // At font 40 the allowance is 60px, so 50 is inside it.
        assertTrue(continuesFrom(box(), INK_RIGHT, ink, 40f))
        // At font 20 the allowance is 30px, so the same ink is a new word.
        assertFalse(continuesFrom(box(), INK_RIGHT, ink, 20f))
    }
}
