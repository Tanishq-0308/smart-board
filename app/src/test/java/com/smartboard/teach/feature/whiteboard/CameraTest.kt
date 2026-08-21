package com.smartboard.teach.feature.whiteboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraTest {

    @Test
    fun `identity camera maps world to screen one to one`() {
        val c = Camera()
        assertEquals(100f, c.worldToScreenX(100f), 0.001f)
        assertEquals(50f, c.worldToScreenY(50f), 0.001f)
    }

    @Test
    fun `world and screen conversions are exact inverses`() {
        val c = Camera()
        c.pan(-120f, -80f)
        c.zoomBy(2.3f, 400f, 300f)

        val worldX = 1234.5f
        val worldY = -678.25f
        assertEquals(worldX, c.screenToWorldX(c.worldToScreenX(worldX)), 0.01f)
        assertEquals(worldY, c.screenToWorldY(c.worldToScreenY(worldY)), 0.01f)
    }

    @Test
    fun `panning moves content with the finger at zoom one`() {
        val c = Camera()
        // Dragging right by 50px should bring content 50px right on screen.
        val before = c.worldToScreenX(0f)
        c.pan(50f, 0f)
        assertEquals(before + 50f, c.worldToScreenX(0f), 0.001f)
    }

    @Test
    fun `panning tracks the finger at any zoom`() {
        val c = Camera()
        c.zoomBy(3f, 0f, 0f)
        val before = c.worldToScreenX(10f)
        c.pan(60f, 0f)
        // Still exactly 60 screen px, not 60*zoom.
        assertEquals(before + 60f, c.worldToScreenX(10f), 0.01f)
    }

    @Test
    fun `panning is reversible`() {
        val c = Camera()
        c.pan(37f, -19f)
        c.pan(-37f, 19f)
        assertEquals(0f, c.offsetX, 0.001f)
        assertEquals(0f, c.offsetY, 0.001f)
    }

    @Test
    fun `zoom keeps the anchor point pinned under the finger`() {
        val c = Camera()
        val anchorX = 640f
        val anchorY = 360f
        val worldAtAnchorBefore = c.screenToWorldX(anchorX)

        c.zoomBy(2f, anchorX, anchorY)

        // The same world point must still sit at the same pixel.
        assertEquals(anchorX, c.worldToScreenX(worldAtAnchorBefore), 0.01f)
    }

    @Test
    fun `zoom anchor holds across repeated pinches`() {
        val c = Camera()
        val anchorX = 500f
        val anchorY = 400f
        val worldBefore = c.screenToWorldY(anchorY)

        repeat(5) { c.zoomBy(1.3f, anchorX, anchorY) }

        assertEquals(anchorY, c.worldToScreenY(worldBefore), 0.05f)
    }

    @Test
    fun `zoom is clamped to the maximum`() {
        val c = Camera()
        repeat(50) { c.zoomBy(2f, 0f, 0f) }
        assertEquals(Camera.MAX_ZOOM, c.zoom, 0.001f)
    }

    @Test
    fun `zoom is clamped to the minimum`() {
        val c = Camera()
        repeat(50) { c.zoomBy(0.5f, 0f, 0f) }
        assertEquals(Camera.MIN_ZOOM, c.zoom, 0.001f)
    }

    @Test
    fun `setZoom reaches the requested level`() {
        val c = Camera()
        c.setZoom(2.5f, 100f, 100f)
        assertEquals(2.5f, c.zoom, 0.001f)
    }

    @Test
    fun `reset returns to the origin at zoom one`() {
        val c = Camera()
        c.pan(500f, -300f)
        c.zoomBy(4f, 10f, 10f)
        c.reset()
        assertEquals(0f, c.offsetX, 0.001f)
        assertEquals(0f, c.offsetY, 0.001f)
        assertEquals(1f, c.zoom, 0.001f)
    }

    @Test
    fun `screen distances convert to world by dividing by zoom`() {
        val c = Camera()
        c.zoomBy(2f, 0f, 0f)
        // A 30px eraser on screen is 15 world units when zoomed 2x.
        assertEquals(15f, c.screenToWorldDistance(30f), 0.001f)
    }

    @Test
    fun `fitTo centres the content in the viewport`() {
        val c = Camera()
        c.fitTo(
            worldLeft = 0f, worldTop = 0f,
            worldRight = 1000f, worldBottom = 500f,
            viewportWidth = 1920f, viewportHeight = 1080f,
            paddingPx = 0f,
        )

        // Content centre should land at viewport centre.
        assertEquals(960f, c.worldToScreenX(500f), 0.5f)
        assertEquals(540f, c.worldToScreenY(250f), 0.5f)
    }

    @Test
    fun `fitTo chooses a zoom that fits both axes`() {
        val c = Camera()
        // Very wide content: width is the limiting dimension.
        c.fitTo(0f, 0f, 4000f, 100f, 1920f, 1080f, paddingPx = 0f)
        assertTrue("content must fit horizontally", 4000f * c.zoom <= 1920f + 1f)
        assertTrue("content must fit vertically", 100f * c.zoom <= 1080f + 1f)
    }

    @Test
    fun `fitTo ignores empty content`() {
        val c = Camera()
        val zoomBefore = c.zoom
        c.fitTo(10f, 10f, 10f, 10f, 1920f, 1080f)
        assertEquals(zoomBefore, c.zoom, 0.001f)
    }

    @Test
    fun `visible bounds match the viewport at zoom one`() {
        val c = Camera()
        val b = c.visibleWorldBounds(1920f, 1080f)
        assertEquals(0f, b[0], 0.001f)
        assertEquals(0f, b[1], 0.001f)
        assertEquals(1920f, b[2], 0.001f)
        assertEquals(1080f, b[3], 0.001f)
    }

    @Test
    fun `visible bounds shrink in world space as zoom increases`() {
        val c = Camera()
        c.zoomBy(2f, 0f, 0f)
        val b = c.visibleWorldBounds(1920f, 1080f)
        // At 2x you see half as much world.
        assertEquals(960f, b[2] - b[0], 0.01f)
        assertEquals(540f, b[3] - b[1], 0.01f)
    }

    @Test
    fun `visible bounds include the requested margin`() {
        val c = Camera()
        val b = c.visibleWorldBounds(1000f, 1000f, marginPx = 100f)
        assertEquals(-100f, b[0], 0.001f)
        assertEquals(1100f, b[2], 0.001f)
    }

    @Test
    fun `restore clamps an out of range zoom`() {
        val c = Camera()
        c.restore(10f, 20f, 999f)
        assertEquals(Camera.MAX_ZOOM, c.zoom, 0.001f)
        assertEquals(10f, c.offsetX, 0.001f)
    }

    @Test
    fun `a pan then zoom then pan sequence stays consistent`() {
        val c = Camera()
        c.pan(-200f, -150f)
        c.zoomBy(1.75f, 300f, 300f)
        c.pan(40f, 60f)

        val world = 512f
        val screen = c.worldToScreenX(world)
        assertEquals(world, c.screenToWorldX(screen), 0.01f)
    }
}

/**
 * Regression tests for the zoomed-out stroke width guard.
 *
 * A long thin line rendered at low zoom turned into a solid blob, because the
 * minimum-visible-width floor (MIN / zoom) grows without bound as zoom falls.
 * The floor is now capped relative to the stroke's own width.
 */
class VisibleWidthTest {

    /** Mirrors BoardRenderer.visibleWidth, which is private. */
    private fun visibleWidth(worldWidth: Float, zoom: Float): Float {
        val safeZoom = zoom.coerceAtLeast(0.01f)
        val floor = (1.2f / safeZoom).coerceAtMost(worldWidth * 3f)
        return maxOf(worldWidth, floor)
    }

    @Test
    fun `width is unchanged at normal zoom`() {
        assertEquals(6f, visibleWidth(6f, 1f), 0.01f)
    }

    @Test
    fun `a hairline is lifted so it stays visible when zoomed out`() {
        // 0.3 world units at 0.25x would render at 0.075px: invisible.
        assertTrue(visibleWidth(0.3f, 0.25f) > 0.3f)
    }

    @Test
    fun `the boost is capped so a line never becomes a blob`() {
        val width = 6f
        // The bug: at 0.1x zoom the raw floor is 12 units, twice the stroke.
        assertTrue(
            "width must not exceed 3x its own value",
            visibleWidth(width, 0.1f) <= width * 3f + 0.001f,
        )
    }

    @Test
    fun `extreme zoom out still respects the cap`() {
        val width = 4f
        assertTrue(visibleWidth(width, Camera.MIN_ZOOM) <= width * 3f + 0.001f)
    }

    @Test
    fun `width never shrinks below the stroke's own width`() {
        assertTrue(visibleWidth(20f, 8f) >= 20f)
    }
}
