package com.smartboard.teach.data.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A copied video and the poster frame extracted from it. */
data class VideoImport(val video: File, val poster: File)

/**
 * Where a video's poster frame lives, derived from the video's own path.
 *
 * Derived rather than stored so a video container needs no extra column, but
 * that only holds while ONE rule decides the name — hence this function, used
 * by both the importer that writes it and the loader that reads it back.
 */
fun posterPathFor(videoPath: String): String =
    videoPath.substringBeforeLast('.') + "_poster.jpg"

/**
 * Copies files chosen through the system picker into app storage.
 *
 * The copy is not optional for PDFs: PdfRenderer needs a SEEKABLE
 * ParcelFileDescriptor, and a descriptor obtained straight from a SAF Uri is
 * not reliably seekable across providers — especially the cut-down document
 * providers that ship on education boards. Copying first turns an
 * unpredictable provider behaviour into a plain local file.
 */
@Singleton
class SafImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val httpClient: OkHttpClient,
) {

    suspend fun importPdf(uri: Uri): AppResult<File> = withContext(ioDispatcher) {
        copyToAppStorage(uri, "imports", "pdf")
    }

    /**
     * Copies a video into app storage and extracts a poster frame beside it.
     *
     * Copied rather than referenced, like every other import: a lesson video
     * on a USB stick that gets pulled out, or a file the teacher later moves,
     * would otherwise fail in front of a class — and Android's grant on the
     * source URI may not survive a restart either.
     *
     * The poster is what the board draws; the video file is only opened when
     * the teacher plays it. Returns both, or a failure if the file is not a
     * video the panel can read — better to say so at import than to leave a
     * blank rectangle that fails when tapped mid-lesson.
     */
    suspend fun importVideo(uri: Uri): AppResult<VideoImport> = withContext(ioDispatcher) {
        val copied = when (val result = copyToAppStorage(uri, "imports", "mp4")) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
        }

        val retriever = MediaMetadataRetriever()
        val poster = try {
            retriever.setDataSource(copied.absolutePath)
            // First frame rather than a seek: a seek past the end of a short
            // clip returns null, and frame 0 always exists.
            retriever.getFrameAtTime(0)
        } catch (error: RuntimeException) {
            // MediaMetadataRetriever throws RuntimeException for unreadable or
            // unsupported files rather than returning null.
            null
        } finally {
            runCatching { retriever.release() }
        }

        if (poster == null) {
            copied.delete()
            return@withContext AppResult.Failure(
                AppError.Storage("That file is not a video the board can play."),
            )
        }

        val posterFile = File(posterPathFor(copied.absolutePath))
        try {
            posterFile.outputStream().use { out ->
                poster.compress(Bitmap.CompressFormat.JPEG, POSTER_QUALITY, out)
            }
        } catch (error: IOException) {
            poster.recycle()
            copied.delete()
            return@withContext AppResult.Failure(
                AppError.Storage("Could not save the video's preview frame."),
            )
        }
        poster.recycle()

        AppResult.Success(VideoImport(video = copied, poster = posterFile))
    }

    /**
     * Decodes one frame from an already-imported video, at [positionMs].
     *
     * Read from the FILE rather than off the screen: a [android.widget.VideoView]
     * draws on a SurfaceView, which screen capture reads as black. Decoding
     * also gives the video's own resolution rather than the panel's, so a
     * captured frame is worth annotating and exporting.
     *
     * `OPTION_CLOSEST` rather than the default `OPTION_CLOSEST_SYNC`: sync
     * frames can be seconds apart, and a teacher pausing on a specific moment
     * must get THAT moment, not the last keyframe before it.
     */
    suspend fun captureFrame(videoPath: String, positionMs: Int): AppResult<File> =
        withContext(ioDispatcher) {
            val source = File(videoPath)
            if (!source.exists()) {
                return@withContext AppResult.Failure(
                    AppError.Storage("That video is no longer on this board's storage."),
                )
            }

            val retriever = MediaMetadataRetriever()
            val frame = try {
                retriever.setDataSource(videoPath)
                retriever.getFrameAtTime(
                    positionMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                )
            } catch (error: RuntimeException) {
                null
            } finally {
                runCatching { retriever.release() }
            }

            if (frame == null) {
                return@withContext AppResult.Failure(
                    AppError.Storage("That moment could not be captured from the video."),
                )
            }

            val target = File(dir("imports"), "frame_${UUID.randomUUID()}.jpg")
            try {
                target.outputStream().use { out ->
                    frame.compress(Bitmap.CompressFormat.JPEG, FRAME_QUALITY, out)
                }
            } catch (error: IOException) {
                frame.recycle()
                return@withContext AppResult.Failure(
                    AppError.Storage("Could not save the captured frame."),
                )
            }
            frame.recycle()
            AppResult.Success(target)
        }

    /**
     * Fetches an image from the web onto local storage.
     *
     * Handles `data:` URIs as well as http(s): image search results are very
     * often base64 thumbnails embedded in the page, and a download that only
     * spoke HTTP would fail on exactly the images a teacher long-presses.
     *
     * Downsampled on the way in like [importImage] — a full-resolution press
     * photo off the web will OOM a 2 GB board just as surely as one from the
     * gallery.
     */
    suspend fun downloadImage(
        url: String,
        maxEdgePx: Int = MAX_IMAGE_EDGE_PX,
    ): AppResult<File> = withContext(ioDispatcher) {
        try {
            val bytes = if (url.startsWith("data:")) {
                val comma = url.indexOf(',')
                if (comma < 0 || !url.substring(0, comma).contains("base64")) {
                    return@withContext AppResult.Failure(
                        AppError.Storage("That image is in a format the board cannot read."),
                    )
                }
                android.util.Base64.decode(url.substring(comma + 1), android.util.Base64.DEFAULT)
            } else {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext AppResult.Failure(
                            AppError.Storage("That image could not be downloaded."),
                        )
                    }
                    response.body?.bytes()
                } ?: return@withContext AppResult.Failure(
                    AppError.Storage("That image came back empty."),
                )
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext AppResult.Failure(
                    AppError.Storage("That file is not a readable image."),
                )
            }

            val transparent = mayHaveAlpha(bounds.outMimeType)
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
                inPreferredConfig = configFor(transparent)
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: return@withContext AppResult.Failure(
                    AppError.Storage("Could not decode that image."),
                )

            val target = File(dir("imports"), "web_${UUID.randomUUID()}.${extensionFor(transparent)}")
            target.outputStream().use { out ->
                bitmap.compress(formatFor(transparent), IMAGE_QUALITY, out)
            }
            bitmap.recycle()
            AppResult.Success(target)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage("Could not fetch that image: ${t.message}"))
        }
    }

    /**
     * Imports an image, downsampling while decoding.
     *
     * A 12 MP photo decoded at full size is ~48 MB as ARGB_8888 and will OOM a
     * 2 GB board, so bounds are read first and inSampleSize computed before
     * any pixels are allocated.
     */
    suspend fun importImage(uri: Uri, maxEdgePx: Int = MAX_IMAGE_EDGE_PX): AppResult<File> =
        withContext(ioDispatcher) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                // The elvis MUST test the stream, not the decode result: a
                // bounds-only decode returns null by contract, so testing the
                // `use {}` value rejected every image that opened fine.
                val boundsStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext AppResult.Failure(
                        AppError.Storage("Could not open the selected image."),
                    )
                boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return@withContext AppResult.Failure(
                        AppError.Storage("That file is not a readable image."),
                    )
                }

                val transparent = mayHaveAlpha(bounds.outMimeType)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
                    inPreferredConfig = configFor(transparent)
                }
                val pixelStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext AppResult.Failure(
                        AppError.Storage("Could not open the selected image."),
                    )
                val bitmap = pixelStream.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return@withContext AppResult.Failure(
                    AppError.Storage("Could not decode the selected image."),
                )

                val target = File(
                    dir("backgrounds"),
                    "img_${UUID.randomUUID()}.${extensionFor(transparent)}",
                )
                target.outputStream().use { out ->
                    bitmap.compress(formatFor(transparent), IMAGE_QUALITY, out)
                }
                bitmap.recycle()
                AppResult.Success(target)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Storage("Could not import the image: ${t.message}"))
            }
        }

    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun copyToAppStorage(uri: Uri, subDir: String, extension: String): AppResult<File> =
        try {
            val target = File(dir(subDir), "${UUID.randomUUID()}.$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return AppResult.Failure(AppError.Storage("Could not open the selected file."))

            if (target.length() == 0L) {
                target.delete()
                AppResult.Failure(AppError.Storage("The selected file was empty."))
            } else {
                AppResult.Success(target)
            }
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage("Could not import the file: ${t.message}"))
        }

    private fun dir(name: String): File = File(context.filesDir, name).apply { mkdirs() }

    companion object {
        const val MAX_IMAGE_EDGE_PX = 2048

        /** Poster frames are a board-sized still, not an archival image. */
        private const val POSTER_QUALITY = 85

        /** Captured frames get annotated and exported, so quality matters more. */
        private const val FRAME_QUALITY = 92

        /**
         * True when the source format can carry an alpha channel.
         *
         * A cut-out diagram or a logo saved as PNG or WebP is transparent, and
         * a whiteboard is exactly where that matters — a black rectangle round
         * a kidney diagram ruins the picture. Photos stay JPEG, which is much
         * smaller, so this only pays for transparency when it is real.
         *
         * Decided from the MIME type reported by the bounds decode rather than
         * by scanning pixels: an alpha channel that happens to be fully opaque
         * still round-trips fine, and scanning a large bitmap to find out
         * costs more than the occasional larger file.
         */
        fun mayHaveAlpha(mimeType: String?): Boolean = when (mimeType) {
            "image/png", "image/webp", "image/gif" -> true
            else -> false
        }

        /** ARGB_8888 keeps alpha; RGB_565 is half the memory but has none. */
        fun configFor(transparent: Boolean): Bitmap.Config =
            if (transparent) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565

        /** JPEG has no alpha channel: transparent pixels come out BLACK. */
        fun formatFor(transparent: Boolean): Bitmap.CompressFormat =
            if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

        fun extensionFor(transparent: Boolean): String = if (transparent) "png" else "jpg"

        /** Ignored by PNG, which is lossless. */
        const val IMAGE_QUALITY = 90

        fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
            var sample = 1
            var w = width
            var h = height
            while (w / 2 >= maxEdge || h / 2 >= maxEdge) {
                w /= 2
                h /= 2
                sample *= 2
            }
            return sample
        }
    }
}
