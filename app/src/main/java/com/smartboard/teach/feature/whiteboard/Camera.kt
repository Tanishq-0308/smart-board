package com.smartboard.teach.feature.whiteboard

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Camera over an infinite world.
 *
 * Every stroke, text box and background is stored in WORLD coordinates and is
 * unbounded. The camera decides which part of that world the fixed-size screen
 * currently shows.
 *
 *     screen = (world - offset) * zoom
 *     world  = screen / zoom + offset
 *
 * Storing world coordinates rather than screen coordinates is what makes the
 * board infinite, and it is also why ink drawn at one zoom level stays the
 * right size relative to everything else when the teacher zooms later.
 *
 * All fields are Compose state so the canvas invalidates on change — but they
 * are read in the DRAW phase only, for the same reason the stroke versions are:
 * a composition-phase read would recompose the whole canvas on every pan frame.
 */
@Stable
class Camera {

    /** World coordinate currently at the top-left of the viewport. */
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set

    var zoom by mutableFloatStateOf(1f)
        private set

    /** Bumped on any camera change, so the renderer can detect staleness. */
    var version by mutableFloatStateOf(0f)
        private set

    fun worldToScreenX(worldX: Float): Float = (worldX - offsetX) * zoom
    fun worldToScreenY(worldY: Float): Float = (worldY - offsetY) * zoom

    fun screenToWorldX(screenX: Float): Float = screenX / zoom + offsetX
    fun screenToWorldY(screenY: Float): Float = screenY / zoom + offsetY

    /** Screen-space distance converted to world space (for eraser radius etc). */
    fun screenToWorldDistance(distance: Float): Float = distance / zoom

    fun pan(screenDx: Float, screenDy: Float) {
        // Divide by zoom so a finger drag moves content exactly with the
        // finger regardless of zoom level.
        offsetX -= screenDx / zoom
        offsetY -= screenDy / zoom
        version++
    }

    /**
     * Zooms about a screen point, keeping the world point under that pixel
     * fixed. Without the anchor correction, pinching drifts the content away
     * from the fingers and feels broken.
     */
    fun zoomBy(factor: Float, anchorScreenX: Float, anchorScreenY: Float) {
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == zoom) return

        val worldAnchorX = screenToWorldX(anchorScreenX)
        val worldAnchorY = screenToWorldY(anchorScreenY)

        zoom = newZoom

        // Re-derive the offset so the anchor lands back on the same pixel.
        offsetX = worldAnchorX - anchorScreenX / newZoom
        offsetY = worldAnchorY - anchorScreenY / newZoom
        version++
    }

    fun setZoom(newZoom: Float, anchorScreenX: Float, anchorScreenY: Float) {
        val clamped = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (clamped == zoom) return
        zoomBy(clamped / zoom, anchorScreenX, anchorScreenY)
    }

    fun reset() {
        offsetX = 0f
        offsetY = 0f
        zoom = 1f
        version++
    }

    /** Centres the viewport on a world rect, at a zoom that fits it. */
    fun fitTo(
        worldLeft: Float,
        worldTop: Float,
        worldRight: Float,
        worldBottom: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        paddingPx: Float = 64f,
    ) {
        val contentWidth = worldRight - worldLeft
        val contentHeight = worldBottom - worldTop
        if (contentWidth <= 0f || contentHeight <= 0f ||
            viewportWidth <= 0f || viewportHeight <= 0f
        ) {
            return
        }

        val usableWidth = (viewportWidth - paddingPx * 2).coerceAtLeast(1f)
        val usableHeight = (viewportHeight - paddingPx * 2).coerceAtLeast(1f)

        zoom = minOf(usableWidth / contentWidth, usableHeight / contentHeight)
            .coerceIn(MIN_ZOOM, MAX_ZOOM)

        // Centre the content in the viewport.
        val centreWorldX = (worldLeft + worldRight) / 2f
        val centreWorldY = (worldTop + worldBottom) / 2f
        offsetX = centreWorldX - viewportWidth / (2f * zoom)
        offsetY = centreWorldY - viewportHeight / (2f * zoom)
        version++
    }

    /** World-space rect currently visible, inflated by [marginPx] screen px. */
    fun visibleWorldBounds(
        viewportWidth: Float,
        viewportHeight: Float,
        marginPx: Float = 0f,
    ): FloatArray {
        val margin = marginPx / zoom
        return floatArrayOf(
            offsetX - margin,
            offsetY - margin,
            offsetX + viewportWidth / zoom + margin,
            offsetY + viewportHeight / zoom + margin,
        )
    }

    fun copyFrom(other: Camera) {
        offsetX = other.offsetX
        offsetY = other.offsetY
        zoom = other.zoom
        version++
    }

    fun restore(offsetX: Float, offsetY: Float, zoom: Float) {
        this.offsetX = offsetX
        this.offsetY = offsetY
        this.zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        version++
    }

    companion object {
        /**
         * 10% to 800%. Below 10% ink is a smudge; above 800% a teacher has
         * lost all context of the lesson.
         */
        const val MIN_ZOOM = 0.1f
        const val MAX_ZOOM = 8f

        val ZOOM_STEPS = floatArrayOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f)
    }
}
