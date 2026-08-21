package com.smartboard.teach.data.file

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transparency must survive import.
 *
 * A cut-out diagram or a logo dropped on the board came out with a BLACK
 * rectangle behind it: the decode asked for RGB_565 (16-bit, no alpha channel
 * at all) and the file was then written as JPEG, which has no alpha either.
 * Two independent ways to lose it, both silent.
 */
class ImageAlphaTest {

    @Test
    fun `formats that carry alpha are recognised`() {
        assertTrue(SafImporter.mayHaveAlpha("image/png"))
        assertTrue(SafImporter.mayHaveAlpha("image/webp"))
        assertTrue(SafImporter.mayHaveAlpha("image/gif"))
    }

    @Test
    fun `photo formats are not treated as transparent`() {
        // JPEG cannot carry alpha, so paying PNG's file size for one is waste.
        assertFalse(SafImporter.mayHaveAlpha("image/jpeg"))
        assertFalse(SafImporter.mayHaveAlpha("image/heif"))
    }

    @Test
    fun `an unknown or missing mime type falls back to opaque`() {
        // Wrong in the safe direction: a photo saved as JPEG, not a PNG
        // silently flattened.
        assertFalse(SafImporter.mayHaveAlpha(null))
        assertFalse(SafImporter.mayHaveAlpha("application/octet-stream"))
    }

    @Test
    fun `a transparent image decodes into a config that has an alpha channel`() {
        // RGB_565 is 16-bit with no alpha; asking for it is what dropped
        // transparency before the bitmap was even written.
        assertEquals(Bitmap.Config.ARGB_8888, SafImporter.configFor(transparent = true))
        assertEquals(Bitmap.Config.RGB_565, SafImporter.configFor(transparent = false))
    }

    @Test
    fun `a transparent image is written as PNG, never JPEG`() {
        assertEquals(Bitmap.CompressFormat.PNG, SafImporter.formatFor(transparent = true))
        assertEquals(Bitmap.CompressFormat.JPEG, SafImporter.formatFor(transparent = false))
    }

    @Test
    fun `the file extension matches the format written`() {
        // A PNG saved as .jpg still decodes, but the mismatch misleads anyone
        // reading the imports directory — and export names come from here.
        assertEquals("png", SafImporter.extensionFor(transparent = true))
        assertEquals("jpg", SafImporter.extensionFor(transparent = false))
    }
}
