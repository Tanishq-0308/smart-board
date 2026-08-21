package com.smartboard.teach.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardCanvasStyleTest {

    // --- grid spacing under zoom ---

    @Test
    fun `spacing is unchanged at normal zoom`() {
        assertEquals(48f, gridSpacingForZoom(48f, zoom = 1f), 0.01f)
    }

    @Test
    fun `spacing doubles as the board is zoomed out`() {
        // Without this the grid becomes a solid grey wash and costs thousands
        // of draw calls for lines a pixel apart.
        val far = gridSpacingForZoom(48f, zoom = 0.1f)
        assertTrue("should have grown", far > 48f)
        assertTrue("gap must be legible", far * 0.1f >= 14f)
    }

    @Test
    fun `spacing only ever DOUBLES, so lines stay on the original lattice`() {
        // A grid that rescaled by an arbitrary factor would draw lines that
        // were never part of the lattice the teacher's graph sits on.
        val spacing = gridSpacingForZoom(48f, zoom = 0.05f)
        val ratio = spacing / 48f
        assertEquals(0f, ratio % 2f, 0.001f)
    }

    @Test
    fun `zooming IN never subdivides the lattice`() {
        // Squares get bigger on screen; new lines between them would change
        // what a square means mid-lesson.
        assertEquals(48f, gridSpacingForZoom(48f, zoom = 8f), 0.01f)
    }

    @Test
    fun `an absurd zoom terminates rather than spinning`() {
        val spacing = gridSpacingForZoom(48f, zoom = 1e-9f)
        assertTrue(spacing.isFinite())
        assertTrue(spacing > 0f)
    }

    @Test
    fun `a zero base spacing does not divide by zero`() {
        assertEquals(0f, gridSpacingForZoom(0f, zoom = 1f), 0.01f)
    }

    // --- grid colour against the paper ---

    @Test
    fun `grid lines are light on dark paper and dark on pale paper`() {
        val onDark = defaultGridColor(0xFF1E3A5F.toInt())
        val onPale = defaultGridColor(0xFFEDF1F7.toInt())
        assertTrue("light lines on dark paper", (onDark shr 16 and 0xFF) > 128)
        assertTrue("dark lines on pale paper", (onPale shr 16 and 0xFF) < 128)
    }

    @Test
    fun `lightness is perceptual, not a channel average`() {
        // Averaging channels calls the reference's quiet blue "light" and
        // picks an invisible grid for it.
        assertTrue("quiet blue is dark", isDarkColor(0xFF1E3A5F.toInt()))
        // Pure green averages to 85 but looks bright.
        assertFalse("saturated green is light", isDarkColor(0xFF00FF00.toInt()))
    }

    @Test
    fun `every palette colour gets a grid it can actually show`() {
        BoardCanvasStyle.PALETTE.forEach { paper ->
            val grid = defaultGridColor(paper)
            assertTrue(
                "grid must contrast with paper",
                isDarkColor(paper) != isDarkColor(grid or 0xFF000000.toInt()),
            )
        }
    }

    // --- defaults ---

    @Test
    fun `a fresh page is plain paper, so upgrading changes nothing on screen`() {
        val style = BoardCanvasStyle()
        assertEquals(GridStyle.NONE, style.grid)
        assertFalse(style.grid.isVisible)
        assertEquals(BoardCanvasStyle.DEFAULT_COLOR_ARGB, style.colorArgb)
    }

    @Test
    fun `every grid style except NONE draws something`() {
        GridStyle.entries.forEach { style ->
            assertEquals(style != GridStyle.NONE, style.isVisible)
        }
    }
}
