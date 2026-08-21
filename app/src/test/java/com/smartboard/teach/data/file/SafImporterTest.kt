package com.smartboard.teach.data.file

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Downsampling maths for image import.
 *
 * The bug this file exists for was NOT in the maths: `importImage` applied
 * `?:` to the result of `use { decodeStream(...) }` while `inJustDecodeBounds`
 * was set. A bounds-only decode returns null by contract, so the elvis fired
 * on every image and the import failed with "Could not open the selected
 * image" even though the stream had opened fine.
 *
 * The fix tests the STREAM for null and keeps the decode result separate.
 * That path needs a real ContentResolver so it cannot run here, but the rule
 * is worth stating: never let `?:` straddle a `use {}` whose lambda can
 * legitimately return null.
 */
class SafImporterTest {

    @Test
    fun `no downsampling when the image already fits`() {
        assertEquals(1, SafImporter.calculateInSampleSize(1024, 768, 2048))
    }

    @Test
    fun `halves until within the max edge`() {
        // 4096 -> 2048 needs one halving.
        assertEquals(2, SafImporter.calculateInSampleSize(4096, 3072, 2048))
        // 8192 -> 2048 needs two.
        assertEquals(4, SafImporter.calculateInSampleSize(8192, 6144, 2048))
    }

    @Test
    fun `samples on the longer edge`() {
        // A tall panorama must be judged by its height, not its width.
        assertEquals(4, SafImporter.calculateInSampleSize(500, 9000, 2048))
    }

    @Test
    fun `sampling never leaves an edge more than twice the max`() {
        // The real contract: it halves while w/2 >= maxEdge, so the decoded
        // edge can sit just under 2x maxEdge but never above it. A 12MP
        // phone photo (4032x3024) therefore decodes at sample=1.
        val cases = listOf(
            4032 to 3024,
            6000 to 4000,
            1920 to 1080,
            12000 to 9000,
        )
        cases.forEach { (w, h) ->
            val sample = SafImporter.calculateInSampleSize(w, h, 2048)
            val longEdge = maxOf(w / sample, h / sample)
            org.junit.Assert.assertTrue(
                "${w}x$h sampled to $longEdge",
                longEdge < 2048 * 2,
            )
        }
    }
}
