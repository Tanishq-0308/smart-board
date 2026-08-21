package com.smartboard.teach.data.local

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary encoding for stroke point arrays.
 *
 * Layout: [version:Int][pointCount:Int][x,y,pressure]* — all little-endian.
 *
 * Chosen over JSON because a 400-point stroke is ~4.8 KB here versus ~15-20 KB
 * as text, and decodes roughly an order of magnitude faster. Page flips must
 * feel instant when a teacher moves between board pages mid-lesson.
 *
 * The version header means the point format can gain fields later (tilt,
 * timestamp) by branching in [decode], with no Room migration.
 */
object StrokeSerializer {

    const val VERSION_1 = 1
    private const val HEADER_BYTES = 8 // two ints
    private const val FLOAT_BYTES = 4
    private const val STRIDE = 3 // x, y, pressure

    fun encode(points: FloatArray): ByteArray {
        require(points.size % STRIDE == 0) {
            "points must be a multiple of $STRIDE (x,y,pressure), got ${points.size}"
        }
        val pointCount = points.size / STRIDE
        val buffer = ByteBuffer
            .allocate(HEADER_BYTES + points.size * FLOAT_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(VERSION_1)
        buffer.putInt(pointCount)
        buffer.asFloatBuffer().put(points)
        return buffer.array()
    }

    /**
     * Returns an empty array for malformed or truncated input rather than
     * throwing: a single corrupt stroke row should cost one stroke, not take
     * down the whole page mid-lesson.
     */
    fun decode(bytes: ByteArray): FloatArray {
        if (bytes.size < HEADER_BYTES) return FloatArray(0)

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = buffer.int
        val pointCount = buffer.int

        if (pointCount <= 0) return FloatArray(0)

        return when (version) {
            VERSION_1 -> {
                val floatCount = pointCount * STRIDE
                val available = (bytes.size - HEADER_BYTES) / FLOAT_BYTES
                if (available < floatCount) return FloatArray(0)
                FloatArray(floatCount).also { buffer.asFloatBuffer().get(it) }
            }
            // Unknown/newer version written by a later build — skip the stroke
            // rather than render garbage.
            else -> FloatArray(0)
        }
    }
}
