package com.smartboard.teach.data.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The poster path is DERIVED, not stored, so a video container needs no extra
 * column. That only holds while the importer that writes the file and the
 * loader that reads it back agree exactly — a mismatch shows up as videos
 * losing their preview frame after a reload, never at import.
 */
class PosterPathTest {

    @Test
    fun `poster sits beside the video with a jpg extension`() {
        assertEquals(
            "/data/imports/abc_poster.jpg",
            posterPathFor("/data/imports/abc.mp4"),
        )
    }

    @Test
    fun `only the final extension is replaced`() {
        // A UUID filename has no dots, but a copied file could; cutting at the
        // FIRST dot would put the poster somewhere the loader never looks.
        assertEquals(
            "/data/imports/lesson.part1_poster.jpg",
            posterPathFor("/data/imports/lesson.part1.mp4"),
        )
    }

    @Test
    fun `a path with no extension still gets a distinct poster`() {
        val path = "/data/imports/clip"
        assertNotEquals(path, posterPathFor(path))
        assertEquals("/data/imports/clip_poster.jpg", posterPathFor(path))
    }

    @Test
    fun `two different videos never share a poster`() {
        assertNotEquals(
            posterPathFor("/data/imports/a.mp4"),
            posterPathFor("/data/imports/b.mp4"),
        )
    }
}
