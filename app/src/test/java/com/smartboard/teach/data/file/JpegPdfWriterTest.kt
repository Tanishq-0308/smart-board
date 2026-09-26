package com.smartboard.teach.data.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** A PDF whose xref offsets are off by one byte will not open in most readers. */
class JpegPdfWriterTest {

    @Test
    fun xrefOffsetsPointAtTheirObjects() {
        val pages = (1..3).map { n ->
            val f = File.createTempFile("page$n", ".jpg").apply { writeBytes(ByteArray(100 * n) { it.toByte() }) }
            JpegPage(f, 1600, 900)
        }
        val bytes = ByteArrayOutputStream().also { JpegPdfWriter.write(it, pages) }.toByteArray()
        val text = String(bytes, Charsets.ISO_8859_1)

        val xrefAt = text.substringAfterLast("startxref\n").substringBefore('\n').trim().toInt()
        assertTrue(text.startsWith("xref", xrefAt))

        val entries = text.substring(xrefAt).lines().drop(3).takeWhile { it.endsWith(" n ") }
        assertEquals(2 + 3 * pages.size, entries.size)
        entries.forEachIndexed { i, line ->
            val offset = line.take(10).toInt()
            assertTrue("object ${i + 1}", text.startsWith("${i + 1} 0 obj", offset))
        }
        assertTrue(text.contains("/Count 3"))
        pages.forEach { it.file.delete() }
    }
}
