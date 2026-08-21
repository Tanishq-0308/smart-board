package com.smartboard.teach.data.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.di.PdfDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders PDF pages to bitmaps using the framework PdfRenderer.
 *
 * Two hard constraints drive this design:
 *
 *  1. **PdfRenderer is not thread-safe and allows only ONE open page at a
 *     time.** Every call is therefore serialized behind both a single-threaded
 *     dispatcher and a mutex, and page/renderer/descriptor are always closed
 *     in `finally`. Getting this wrong produces native crashes, not
 *     exceptions.
 *
 *  2. **Memory.** An ARGB_8888 bitmap at 3840x2160 is ~33 MB; two of those
 *     will OOM a 2 GB board. Pages render at most [MAX_RENDER_EDGE_PX] on the
 *     long edge and the canvas scales them up at draw time. PDF text at 2048px
 *     is perfectly legible from the back of a classroom.
 */
@Singleton
class PdfPageRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:PdfDispatcher private val pdfDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()

    suspend fun pageCount(pdf: File): AppResult<Int> = withContext(pdfDispatcher) {
        mutex.withLock {
            var descriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(descriptor)
                AppResult.Success(renderer.pageCount)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Storage("Could not read the PDF: ${t.message}"))
            } finally {
                runCatching { renderer?.close() }
                runCatching { descriptor?.close() }
            }
        }
    }

    /**
     * Renders one page and caches it as a JPEG, returning the cached file.
     * Re-opening the same page later reuses the cache rather than re-rendering.
     */
    suspend fun renderPageToFile(
        pdf: File,
        pageIndex: Int,
        maxEdgePx: Int = MAX_RENDER_EDGE_PX,
    ): AppResult<File> = withContext(pdfDispatcher) {
        mutex.withLock {
            val cached = File(cacheDir(), "${pdf.nameWithoutExtension}_p$pageIndex.jpg")
            if (cached.exists() && cached.length() > 0) {
                return@withLock AppResult.Success(cached)
            }

            var descriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            var page: PdfRenderer.Page? = null
            var bitmap: Bitmap? = null
            try {
                descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(descriptor)

                if (pageIndex !in 0 until renderer.pageCount) {
                    return@withLock AppResult.Failure(AppError.NotFound("That page does not exist."))
                }

                page = renderer.openPage(pageIndex)
                val scale = minOf(
                    maxEdgePx.toFloat() / page.width,
                    maxEdgePx.toFloat() / page.height,
                ).coerceAtMost(MAX_UPSCALE)

                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)

                // ARGB_8888 because PdfRenderer requires it, but the page is
                // painted white first so the JPEG has no transparent regions.
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                cached.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                AppResult.Success(cached)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Storage("Could not render that page: ${t.message}"))
            } finally {
                runCatching { page?.close() }
                runCatching { renderer?.close() }
                runCatching { descriptor?.close() }
                bitmap?.recycle()
            }
        }
    }

    private fun cacheDir(): File = File(context.filesDir, "backgrounds").apply { mkdirs() }

    companion object {
        /** Long-edge cap. See the memory note in the class docs. */
        const val MAX_RENDER_EDGE_PX = 2048

        /** Never blow a small page up beyond 3x; it only adds memory, not detail. */
        const val MAX_UPSCALE = 3f
    }
}
