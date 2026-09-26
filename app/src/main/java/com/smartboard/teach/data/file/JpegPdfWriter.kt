package com.smartboard.teach.data.file

import java.io.File
import java.io.OutputStream

/** One page of a lesson export: a JPEG on disk and its pixel size. */
data class JpegPage(val file: File, val widthPx: Int, val heightPx: Int)

/**
 * Writes a PDF with one full-page JPEG per page, streaming each image from
 * disk.
 *
 * Why not framework PdfDocument: it keeps every finished page in native memory
 * until writeTo(), and stores bitmaps losslessly. A 60-page lesson of
 * annotated textbook pages would then sit in memory at once — exactly the
 * crash a 2 GB board must never have. Here memory is one copy buffer, and
 * the JPEG bytes pass straight through (DCTDecode), so files stay small.
 */
object JpegPdfWriter {

    /** Every page is this wide in points (A4 landscape); height follows the image. */
    private const val PAGE_WIDTH_PT = 842f

    fun write(out: OutputStream, pages: List<JpegPage>) {
        require(pages.isNotEmpty()) { "A PDF needs at least one page" }
        val pdf = CountingStream(out)
        val offsets = mutableListOf<Long>()
        fun obj(body: () -> Unit) {
            offsets += pdf.count
            pdf.text("${offsets.size} 0 obj\n")
            body()
            pdf.text("\nendobj\n")
        }

        pdf.text("%PDF-1.4\n%âãÏÓ\n")
        // Objects: 1 catalog, 2 page tree, then per page: page, content, image.
        val pageIds = pages.indices.map { 3 + it * 3 }
        obj { pdf.text("<< /Type /Catalog /Pages 2 0 R >>") }
        obj {
            pdf.text(
                "<< /Type /Pages /Count ${pages.size} /Kids [" +
                    pageIds.joinToString(" ") { "$it 0 R" } + "] >>",
            )
        }
        pages.forEachIndexed { i, page ->
            val w = PAGE_WIDTH_PT
            val h = PAGE_WIDTH_PT * page.heightPx / page.widthPx
            val id = pageIds[i]
            obj {
                pdf.text(
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${fmt(w)} ${fmt(h)}] " +
                        "/Resources << /XObject << /Im0 ${id + 2} 0 R >> >> /Contents ${id + 1} 0 R >>",
                )
            }
            val drawing = "q ${fmt(w)} 0 0 ${fmt(h)} 0 0 cm /Im0 Do Q"
            obj {
                pdf.text("<< /Length ${drawing.length} >>\nstream\n$drawing\nendstream")
            }
            obj {
                pdf.text(
                    "<< /Type /XObject /Subtype /Image /Width ${page.widthPx} /Height ${page.heightPx} " +
                        "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
                        "/Length ${page.file.length()} >>\nstream\n",
                )
                page.file.inputStream().use { it.copyTo(pdf) }
                pdf.text("\nendstream")
            }
        }

        val xref = pdf.count
        pdf.text("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { pdf.text("%010d 00000 n \n".format(it)) }
        pdf.text("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        pdf.flush()
    }

    private fun fmt(v: Float) = "%.2f".format(java.util.Locale.US, v)

    /** Tracks the byte offset, which the xref table must record exactly. */
    private class CountingStream(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
        override fun flush() = out.flush()
        fun text(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))
    }
}
