package com.smartboard.teach.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

object BitmapUtils {

    /**
     * Long-edge target for images sent to the vision model.
     *
     * 1536px matches the tile grid the model uses at high detail. Sending more
     * pixels costs tokens and latency without improving transcription, which
     * matters when a teacher may snapshot the board every few minutes all day.
     */
    const val AI_LONG_EDGE_PX = 1536

    const val AI_JPEG_QUALITY = 85

    /** Scales so the long edge is at most [maxEdgePx]. Never upscales. */
    fun downscale(source: Bitmap, maxEdgePx: Int = AI_LONG_EDGE_PX): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= maxEdgePx) return source

        val scale = maxEdgePx.toFloat() / longEdge
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    /**
     * Decodes [path] no larger than about [maxEdgePx] on its long edge, via a
     * power-of-two sample size — a 12 MP photo decoded at full size is ~48 MB,
     * a good share of a 2 GB board's heap. Null if the file is unreadable.
     */
    fun decodeSampled(path: String, maxEdgePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdgePx) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun toJpegBytes(bitmap: Bitmap, quality: Int = AI_JPEG_QUALITY): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    /**
     * Data URL for the OpenAI image content part.
     *
     * NO_WRAP matters: the default Base64 flags insert newlines, which corrupt
     * the URL.
     */
    fun toDataUrl(jpegBytes: ByteArray): String =
        "data:image/jpeg;base64," + Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
}
