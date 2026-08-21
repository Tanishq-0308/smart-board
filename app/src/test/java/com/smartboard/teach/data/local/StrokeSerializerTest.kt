package com.smartboard.teach.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StrokeSerializerTest {

    @Test
    fun `round trips a typical stroke`() {
        val points = FloatArray(300 * 3) { i -> i * 0.37f }
        val decoded = StrokeSerializer.decode(StrokeSerializer.encode(points))
        assertArrayEquals(points, decoded, 0f)
    }

    @Test
    fun `round trips a single point`() {
        val points = floatArrayOf(12.5f, -8.25f, 0.63f)
        assertArrayEquals(points, StrokeSerializer.decode(StrokeSerializer.encode(points)), 0f)
    }

    @Test
    fun `round trips an empty stroke`() {
        assertEquals(0, StrokeSerializer.decode(StrokeSerializer.encode(FloatArray(0))).size)
    }

    @Test
    fun `preserves pressure values exactly`() {
        val points = floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f, 2f, 2f, 0.5f)
        val decoded = StrokeSerializer.decode(StrokeSerializer.encode(points))
        assertEquals(0f, decoded[2], 0f)
        assertEquals(1f, decoded[5], 0f)
        assertEquals(0.5f, decoded[8], 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a point array that is not a multiple of stride`() {
        StrokeSerializer.encode(floatArrayOf(1f, 2f))
    }

    @Test
    fun `binary encoding is materially smaller than json would be`() {
        val points = FloatArray(400 * 3) { it * 1.1f }
        val encoded = StrokeSerializer.encode(points)
        // 400 points * 3 floats * 4 bytes + 8 byte header
        assertEquals(4808, encoded.size)
    }

    // --- Corruption tolerance: one bad row must cost one stroke, not the page ---

    @Test
    fun `returns empty for truncated header`() {
        assertEquals(0, StrokeSerializer.decode(byteArrayOf(1, 0, 0)).size)
    }

    @Test
    fun `returns empty for empty input`() {
        assertEquals(0, StrokeSerializer.decode(ByteArray(0)).size)
    }

    @Test
    fun `returns empty when payload is shorter than the declared point count`() {
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(StrokeSerializer.VERSION_1)
            .putInt(1000) // claims 1000 points
            .array()
        assertEquals(0, StrokeSerializer.decode(header).size)
    }

    @Test
    fun `returns empty for an unknown future version`() {
        val bytes = ByteBuffer.allocate(8 + 12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(99)
            .putInt(1)
            .putFloat(1f).putFloat(2f).putFloat(3f)
            .array()
        assertEquals(0, StrokeSerializer.decode(bytes).size)
    }

    @Test
    fun `returns empty for a negative point count`() {
        val bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(StrokeSerializer.VERSION_1)
            .putInt(-5)
            .array()
        assertEquals(0, StrokeSerializer.decode(bytes).size)
    }

    @Test
    fun `encoding is stable across calls`() {
        val points = floatArrayOf(1f, 2f, 0.5f, 3f, 4f, 0.9f)
        assertArrayEquals(StrokeSerializer.encode(points), StrokeSerializer.encode(points))
    }

    @Test
    fun `handles a very large stroke`() {
        val points = FloatArray(5000 * 3) { it.toFloat() }
        val decoded = StrokeSerializer.decode(StrokeSerializer.encode(points))
        assertEquals(15000, decoded.size)
        assertTrue(decoded.last() == 14999f)
    }
}
