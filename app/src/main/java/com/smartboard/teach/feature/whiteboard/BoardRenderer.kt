package com.smartboard.teach.feature.whiteboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.ContainerKind
import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.feature.whiteboard.container.MindmapLayout
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Flattened text box, in world coordinates, for export rendering. */
data class TextBoxRender(
    val x: Float,
    val y: Float,
    val text: String,
    val colorArgb: Int,
    val fontSizePx: Float,
)

/**
 * Renders board content, with a viewport-sized cache of committed strokes.
 *
 * The board is infinite, so a bitmap covering "the whole board" cannot exist.
 * Instead the cache covers the CURRENT VIEWPORT plus a margin, captured at the
 * camera position it was rendered for:
 *
 *   - Camera unchanged (the common case: teacher writing) -> blit the cache,
 *     draw only the live stroke. Same cost as the old fixed board.
 *   - Camera moved a little (within the margin) -> blit the cache at an offset.
 *     Panning stays smooth without re-rasterizing every frame.
 *   - Camera moved beyond the margin, or zoomed -> the cache is stale; strokes
 *     are drawn directly until [rebuildCache] runs on pan settle.
 *
 * Not thread-safe; confined to the UI thread except [exportBitmap], which
 * allocates its own output.
 */
class BoardRenderer {

    var cacheBitmap: Bitmap? = null
        private set

    private var cacheCanvas: Canvas? = null

    /** Camera state the cache was rendered at. */
    private var cacheOffsetX = 0f
    private var cacheOffsetY = 0f
    private var cacheZoom = 1f
    private var cacheValid = false

    private var viewportWidth = 0
    private var viewportHeight = 0

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val reusablePath = Path()

    /**
     * Separate from [strokePaint], which is re-configured for every stroke —
     * sharing it would mean re-setting cap, join and alpha on each frame draw.
     */
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.MITER
    }
    private val frameRect = RectF()

    /**
     * Own paint and path for the video play badge.
     *
     * Sharing [reusablePath] would be a live hazard: it is reset and rebuilt
     * for the stroke currently being drawn, and container frames are painted
     * in the middle of that same pass.
     */
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgePath = Path()

    /** Dash effects by on-length; see dashEffect(). */
    private val dashCache = HashMap<Float, DashPathEffect>()

    /** Bumped whenever the cache changes, to invalidate the draw pass. */
    var version: Int = 0
        private set

    /**
     * The cache is larger than the viewport so small pans can be served by
     * blitting at an offset instead of re-rasterizing.
     */
    private val marginPx get() = CACHE_MARGIN_PX

    fun ensureSurface(widthPx: Int, heightPx: Int): Boolean {
        if (widthPx <= 0 || heightPx <= 0) return false
        val cacheW = widthPx + marginPx * 2
        val cacheH = heightPx + marginPx * 2

        val existing = cacheBitmap
        if (existing != null && existing.width == cacheW && existing.height == cacheH) {
            viewportWidth = widthPx
            viewportHeight = heightPx
            return false
        }

        existing?.recycle()
        val bitmap = Bitmap.createBitmap(cacheW, cacheH, Bitmap.Config.ARGB_8888)
        cacheBitmap = bitmap
        cacheCanvas = Canvas(bitmap)
        viewportWidth = widthPx
        viewportHeight = heightPx
        cacheValid = false
        version++
        return true
    }

    /**
     * Re-rasterizes visible strokes for the current camera.
     *
     * Only strokes intersecting the visible world bounds are drawn, so a board
     * with thousands of strokes costs the same as one screenful.
     */
    fun rebuildCache(
        strokes: List<Stroke>,
        camera: Camera,
        containers: List<Container> = emptyList(),
        media: Map<String, Bitmap> = emptyMap(),
    ) {
        val canvas = cacheCanvas ?: return
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)

        val bounds = camera.visibleWorldBounds(
            viewportWidth.toFloat(),
            viewportHeight.toFloat(),
            marginPx.toFloat(),
        )

        canvas.save()
        // The cache is offset by the margin, then scaled and translated to
        // match the camera.
        canvas.translate(marginPx.toFloat(), marginPx.toFloat())
        canvas.scale(camera.zoom, camera.zoom)
        canvas.translate(-camera.offsetX, -camera.offsetY)

        // Frames first: contained ink is drawn ON them, so a grid line never
        // crosses the handwriting it holds.
        drawContainers(canvas, containers, bounds, camera.zoom, media)
        drawStrokesClipped(canvas, strokes, containers, camera.zoom, bounds)
        canvas.restore()

        cacheOffsetX = camera.offsetX
        cacheOffsetY = camera.offsetY
        cacheZoom = camera.zoom
        cacheValid = true
        version++
    }

    /**
     * Can the cache still be used for this camera?
     *
     * Zoom must match exactly (a scaled blit would look soft), and the pan
     * delta must stay inside the margin.
     */
    fun isCacheUsable(camera: Camera): Boolean {
        if (!cacheValid) return false
        if (camera.zoom != cacheZoom) return false
        val dxScreen = (cacheOffsetX - camera.offsetX) * camera.zoom
        val dyScreen = (cacheOffsetY - camera.offsetY) * camera.zoom
        return kotlin.math.abs(dxScreen) <= marginPx && kotlin.math.abs(dyScreen) <= marginPx
    }

    /** Screen-space offset at which to blit the cache for the given camera. */
    fun cacheBlitOffset(camera: Camera): FloatArray = floatArrayOf(
        -marginPx + (cacheOffsetX - camera.offsetX) * camera.zoom,
        -marginPx + (cacheOffsetY - camera.offsetY) * camera.zoom,
    )

    fun invalidateCache() {
        cacheValid = false
        version++
    }

    fun release() {
        cacheBitmap?.recycle()
        cacheBitmap = null
        cacheCanvas = null
        cacheValid = false
    }

    /**
     * Draws strokes straight to the screen canvas, in world space.
     *
     * Used while panning or zooming (when the cache is stale) and by export.
     * The caller has already applied the camera transform to [canvas].
     */
    fun drawStrokesDirect(
        canvas: Canvas,
        strokes: List<Stroke>,
        camera: Camera,
        worldBounds: FloatArray,
        containers: List<Container> = emptyList(),
        media: Map<String, Bitmap> = emptyMap(),
    ) {
        drawContainers(canvas, containers, worldBounds, camera.zoom, media)
        drawStrokesClipped(canvas, strokes, containers, camera.zoom, worldBounds)
    }

    /**
     * Draws strokes, clipping contained ink to its own cell.
     *
     * A stroke whose container has gone (a dangling tag after an interrupted
     * write) falls through to the unclipped branch and renders as free ink.
     * Losing the clip is a cosmetic degradation; losing the ink would not be.
     */
    private fun drawStrokesClipped(
        canvas: Canvas,
        strokes: List<Stroke>,
        containers: List<Container>,
        zoom: Float,
        worldBounds: FloatArray,
    ) {
        // Built once per pass rather than per stroke: a page of table ink would
        // otherwise be a linear scan of the container list for every stroke.
        val byId = if (containers.isEmpty()) {
            emptyMap()
        } else {
            containers.associateBy { it.id }
        }

        strokes.forEach { stroke ->
            if (!Selection.rectsIntersect(stroke.bounds(), worldBounds)) return@forEach

            val cell = stroke.containerId
                ?.let { byId[it] }
                ?.cellAt(stroke.cellIndex)

            if (cell == null) {
                drawStroke(canvas, stroke, zoom)
            } else {
                canvas.save()
                canvas.clipRect(cell.left, cell.top, cell.right, cell.bottom)
                drawStroke(canvas, stroke, zoom)
                canvas.restore()
            }
        }
    }

    /**
     * Draws container frames in world space.
     *
     * Native canvas rather than a Compose layer, because the frame has to sit
     * UNDER the ink it holds — and that ink is drawn here. There is no Compose
     * layer between the strokes and the canvas they are drawn on.
     */
    fun drawContainers(
        canvas: Canvas,
        containers: List<Container>,
        worldBounds: FloatArray,
        zoom: Float = 1f,
        /** Decoded media by container id; the canvas owns the cache. */
        media: Map<String, Bitmap> = emptyMap(),
    ) {
        if (containers.isEmpty()) return

        containers.forEach { container ->
            if (!Selection.rectsIntersect(container.bounds(), worldBounds)) return@forEach

            framePaint.color = container.strokeColorArgb
            framePaint.strokeWidth = visibleWidth(container.lineWidthPx, zoom)

            when (container.kind) {
                ContainerKind.TABLE -> container.cells.forEach { cell ->
                    canvas.drawRect(cell.left, cell.top, cell.right, cell.bottom, framePaint)
                }

                ContainerKind.MINDMAP -> {
                    // Connectors first, so a line meeting a node is covered by
                    // the node's own edge rather than crossing into the box.
                    container.cells.forEachIndexed { index, cell ->
                        val parent = cell.col
                        if (parent < 0) return@forEachIndexed
                        val path = MindmapLayout.connectorPath(container, parent, index)
                        var i = 0
                        while (i + 3 < path.size) {
                            canvas.drawLine(path[i], path[i + 1], path[i + 2], path[i + 3], framePaint)
                            i += 2
                        }
                    }
                    container.cells.forEach { cell ->
                        frameRect.set(cell.left, cell.top, cell.right, cell.bottom)
                        canvas.drawRoundRect(frameRect, NODE_RADIUS, NODE_RADIUS, framePaint)
                    }
                }

                ContainerKind.IMAGE, ContainerKind.VIDEO -> {
                    val cell = container.cells.firstOrNull() ?: return@forEach
                    frameRect.set(cell.left, cell.top, cell.right, cell.bottom)
                    val bitmap = media[container.id]
                    if (bitmap != null) {
                        canvas.drawBitmap(bitmap, null, frameRect, null)
                    } else {
                        // Still decoding, or the file has gone: an outline
                        // keeps the object visible and selectable rather than
                        // leaving a hole where a teacher put a picture.
                        canvas.drawRect(frameRect, framePaint)
                    }
                    // A video is a still frame until it is opened, so it needs
                    // a badge or it is indistinguishable from a photograph.
                    if (container.kind == ContainerKind.VIDEO) {
                        drawPlayBadge(canvas, cell.centerX, cell.centerY, zoom)
                    }
                }
            }
        }
    }

    /**
     * Renders a stroke. [zoom] scales the minimum-width guard only: at low zoom
     * a hairline would otherwise vanish, and a teacher zooming out to see the
     * whole lesson expects to still see their ink.
     */
    fun drawStroke(canvas: Canvas, stroke: Stroke, zoom: Float = 1f) {
        if (stroke.pointCount == 0) return

        strokePaint.color = stroke.style.colorArgb
        strokePaint.alpha = (stroke.style.alpha * 255).toInt().coerceIn(0, 255)
        strokePaint.strokeCap =
            if (stroke.tool == DrawTool.HIGHLIGHTER) Paint.Cap.SQUARE else Paint.Cap.ROUND

        strokePaint.strokeWidth = visibleWidth(stroke.style.baseWidthPx, zoom)

        if (stroke.tool.isShape) {
            drawShape(canvas, stroke)
            return
        }

        if (stroke.pointCount == 1) {
            val r = visibleWidth(
                InkSmoothing.widthForPressure(
                    stroke.style.baseWidthPx,
                    stroke.pressure(0),
                    stroke.style.isPressureSensitive,
                ),
                zoom,
            ) / 2f
            strokePaint.style = Paint.Style.FILL
            canvas.drawCircle(stroke.x(0), stroke.y(0), r, strokePaint)
            strokePaint.style = Paint.Style.STROKE
            return
        }

        if (stroke.style.isPressureSensitive) {
            drawPressureVaryingStroke(canvas, stroke, zoom)
        } else {
            buildSmoothPath(stroke, reusablePath)
            canvas.drawPath(reusablePath, strokePaint)
        }
    }

    /**
     * Pressure-varying ink cannot be one Path — a Path carries a single width.
     * Each segment is drawn at its own width; the distance filter keeps the
     * segment count modest.
     */
    private fun drawPressureVaryingStroke(canvas: Canvas, stroke: Stroke, zoom: Float) {
        val base = stroke.style.baseWidthPx
        for (i in 0 until stroke.pointCount - 1) {
            val avgPressure = (stroke.pressure(i) + stroke.pressure(i + 1)) / 2f
            strokePaint.strokeWidth = visibleWidth(
                InkSmoothing.widthForPressure(base, avgPressure, true),
                zoom,
            )
            canvas.drawLine(
                stroke.x(i), stroke.y(i),
                stroke.x(i + 1), stroke.y(i + 1),
                strokePaint,
            )
        }
    }

    /** Catmull-Rom through the sampled points, emitted as cubic Beziers. */
    fun buildSmoothPath(stroke: Stroke, out: Path): Path {
        out.reset()
        val n = stroke.pointCount
        if (n == 0) return out

        out.moveTo(stroke.x(0), stroke.y(0))
        if (n == 1) return out
        if (n == 2) {
            out.lineTo(stroke.x(1), stroke.y(1))
            return out
        }

        for (i in 0 until n - 1) {
            val i0 = if (i == 0) 0 else i - 1
            val i3 = if (i + 2 > n - 1) n - 1 else i + 2
            val cp = InkSmoothing.catmullRomControlPoints(
                stroke.x(i0), stroke.y(i0),
                stroke.x(i), stroke.y(i),
                stroke.x(i + 1), stroke.y(i + 1),
                stroke.x(i3), stroke.y(i3),
            )
            out.cubicTo(cp[0], cp[1], cp[2], cp[3], stroke.x(i + 1), stroke.y(i + 1))
        }
        return out
    }

    private fun drawShape(canvas: Canvas, stroke: Stroke) {
        // A polygon stores every vertex, not two endpoints, so it is drawn as
        // a closed path rather than derived from a bounding box.
        if (stroke.tool == DrawTool.POLYGON) {
            if (stroke.pointCount < 3) return
            reusablePath.reset()
            reusablePath.moveTo(stroke.x(0), stroke.y(0))
            for (i in 1 until stroke.pointCount) {
                reusablePath.lineTo(stroke.x(i), stroke.y(i))
            }
            reusablePath.close()
            canvas.drawPath(reusablePath, strokePaint)
            return
        }

        if (stroke.pointCount < 2) return
        val x0 = stroke.x(0); val y0 = stroke.y(0)
        val x1 = stroke.x(1); val y1 = stroke.y(1)

        val outline = ShapeGeometry.outlineFor(stroke.tool, x0, y0, x1, y1)

        // Dashes scale with stroke width so they stay readable at any zoom.
        val dashOn = stroke.style.baseWidthPx * 3.5f
        val previousEffect = strokePaint.pathEffect
        if (stroke.tool.isDashed) {
            strokePaint.pathEffect = dashEffect(dashOn)
        }

        outline.visible.forEach { drawPolyline(canvas, it) }
        outline.ovals.forEach {
            canvas.drawOval(RectF(it.left, it.top, it.right, it.bottom), strokePaint)
        }
        outline.arcs.forEach {
            canvas.drawArc(
                RectF(it.left, it.top, it.right, it.bottom),
                it.startDegrees, it.sweepDegrees, false, strokePaint,
            )
        }

        // Hidden edges: dashed and faded, so a wireframe reads as a solid with
        // a back rather than a tangle of equally-weighted lines.
        if (outline.hidden.isNotEmpty() || outline.hiddenOvals.isNotEmpty()) {
            val fullAlpha = strokePaint.alpha
            strokePaint.pathEffect = dashEffect(dashOn * 0.6f)
            strokePaint.alpha = (fullAlpha * 0.55f).toInt().coerceIn(0, 255)
            outline.hidden.forEach { drawPolyline(canvas, it) }
            outline.hiddenOvals.forEach {
                canvas.drawOval(RectF(it.left, it.top, it.right, it.bottom), strokePaint)
            }
            strokePaint.alpha = fullAlpha
        }

        strokePaint.pathEffect = previousEffect

        if (stroke.tool.hasArrowHead) {
            val angle = atan2(y1 - y0, x1 - x0)
            val head = (hypot(x1 - x0, y1 - y0) * 0.22f)
                .coerceIn(stroke.style.baseWidthPx * 2.5f, 56f)
            val spread = 0.45f
            // The head stays solid even on a dashed arrow: a dashed head reads
            // as a rendering fault rather than a style.
            canvas.drawLine(
                x1, y1,
                x1 - head * cos(angle - spread), y1 - head * sin(angle - spread),
                strokePaint,
            )
            canvas.drawLine(
                x1, y1,
                x1 - head * cos(angle + spread), y1 - head * sin(angle + spread),
                strokePaint,
            )
        }
    }

    private fun drawPolyline(canvas: Canvas, line: ShapeGeometry.Polyline) {
        val pts = line.points
        if (pts.size < 4) return
        reusablePath.reset()
        reusablePath.moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) {
            reusablePath.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        if (line.closed) reusablePath.close()
        canvas.drawPath(reusablePath, strokePaint)
    }

    /**
     * Cached per dash length.
     *
     * A fresh DashPathEffect per stroke would allocate once per shape per
     * frame, and a page's shapes share only a handful of distinct widths.
     */
    private fun dashEffect(on: Float): DashPathEffect {
        val key = on.coerceAtLeast(1f)
        dashCache[key]?.let { return it }
        return DashPathEffect(floatArrayOf(key, key * 0.7f), 0f).also {
            if (dashCache.size < DASH_CACHE_MAX) dashCache[key] = it
        }
    }

    /**
     * Flattened image of the board content for snapshots and thumbnails.
     *
     * On an infinite canvas "the board" is whatever the teacher has drawn, so
     * this exports the CONTENT BOUNDS rather than the viewport — a snapshot
     * should capture the whole lesson, not just the part currently on screen.
     * Falls back to the viewport when the board is empty.
     *
     * The caller owns the returned bitmap and must recycle it.
     */
    fun exportBitmap(
        strokes: List<Stroke>,
        textBoxes: List<TextBoxRender>,
        background: Bitmap? = null,
        maxEdgePx: Int = EXPORT_MAX_EDGE_PX,
        paddingWorld: Float = EXPORT_PADDING,
        regionBounds: FloatArray? = null,
        maxScale: Float = EXPORT_MAX_SCALE,
        containers: List<Container> = emptyList(),
        media: Map<String, Bitmap> = emptyMap(),
    ): Bitmap? {
        // An explicit region wins over computed content bounds. This is what
        // the visual-lookup crop uses: the teacher chose the area, so the
        // export must honour it exactly rather than shrink-wrapping the ink
        // inside it - the surrounding whitespace is context the model needs.
        val contentBounds = regionBounds ?: Selection.boundsOf(strokes, emptyList())
        val hasStrokes = !Selection.isEmpty(contentBounds)

        var left = contentBounds[0]
        var top = contentBounds[1]
        var right = contentBounds[2]
        var bottom = contentBounds[3]

        // An empty table has no ink to bound it, but it is still content the
        // teacher put on the board and expects in the export.
        if (regionBounds == null) {
            containers.forEach { container ->
                val b = container.bounds()
                if (left > right) {
                    left = b[0]; top = b[1]; right = b[2]; bottom = b[3]
                } else {
                    left = minOf(left, b[0]); top = minOf(top, b[1])
                    right = maxOf(right, b[2]); bottom = maxOf(bottom, b[3])
                }
            }
        }

        if (regionBounds == null) {
            textBoxes.forEach { box ->
                val lines = box.text.count { it == '\n' } + 1
                val h = box.fontSizePx * 1.3f * lines
                val w = box.fontSizePx * 0.6f * (box.text.lines().maxOfOrNull { it.length } ?: 0)
                if (!hasStrokes && left > right) {
                    left = box.x; top = box.y; right = box.x + w; bottom = box.y + h
                } else {
                    left = minOf(left, box.x); top = minOf(top, box.y)
                    right = maxOf(right, box.x + w); bottom = maxOf(bottom, box.y + h)
                }
            }
        }

        if (left > right || bottom - top <= 0f || right - left <= 0f) {
            // Nothing drawn: export a blank viewport-sized board rather than
            // failing, so the snapshot flow still produces a file.
            if (viewportWidth <= 0 || viewportHeight <= 0) return null
            left = 0f; top = 0f
            right = viewportWidth.toFloat(); bottom = viewportHeight.toFloat()
        }

        left -= paddingWorld; top -= paddingWorld
        right += paddingWorld; bottom += paddingWorld

        val worldWidth = right - left
        val worldHeight = bottom - top
        val scale = minOf(
            maxEdgePx / worldWidth,
            maxEdgePx / worldHeight,
            maxScale,
        ).coerceAtLeast(0.01f)

        val outWidth = (worldWidth * scale).toInt().coerceIn(1, maxEdgePx)
        val outHeight = (worldHeight * scale).toInt().coerceIn(1, maxEdgePx)

        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        // White first: a whiteboard, and a transparent JPEG becomes black.
        canvas.drawColor(android.graphics.Color.WHITE)

        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(-left, -top)

        background?.let { bg ->
            canvas.drawBitmap(
                bg,
                null,
                RectF(left, top, left + bg.width.toFloat(), top + bg.height.toFloat()),
                null,
            )
        }

        val exportBounds = floatArrayOf(left, top, right, bottom)
        drawContainers(canvas, containers, exportBounds, scale, media)
        drawStrokesClipped(canvas, strokes, containers, scale, exportBounds)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        textBoxes.forEach { box ->
            textPaint.color = box.colorArgb
            textPaint.textSize = box.fontSizePx
            var y = box.y + box.fontSizePx
            box.text.split('\n').forEach { line ->
                canvas.drawText(line, box.x, y, textPaint)
                y += box.fontSizePx * 1.25f
            }
        }
        canvas.restore()

        return output
    }

    /**
     * World-space width that renders at least [MIN_VISIBLE_WIDTH_PX] on screen.
     *
     * Zooming out shrinks every stroke; without a floor, ink disappears
     * entirely when a teacher zooms out to see a whole lesson. But the floor
     * is CAPPED: at very low zoom, `MIN / zoom` grows without bound, and
     * applying it unchecked inflated a long thin line into a solid blob. The
     * cap keeps the guard doing its one job — rescuing sub-pixel strokes —
     * without ever redefining what the stroke looks like.
     */
    private fun visibleWidth(worldWidth: Float, zoom: Float): Float {
        val safeZoom = zoom.coerceAtLeast(0.01f)
        val floor = (MIN_VISIBLE_WIDTH_PX / safeZoom)
            .coerceAtMost(worldWidth * MAX_WIDTH_BOOST)
        return maxOf(worldWidth, floor)
    }

    /**
     * A play triangle in a translucent disc, centred on a video's frame.
     *
     * Sized in SCREEN px and converted to world, so the badge stays the same
     * size on the glass however far the board is zoomed — a badge that shrank
     * with the video would become untappable exactly when the object is small.
     */
    private fun drawPlayBadge(canvas: Canvas, centerX: Float, centerY: Float, zoom: Float) {
        val radius = PLAY_BADGE_RADIUS_PX / zoom.coerceAtLeast(0.01f)

        badgePaint.style = Paint.Style.FILL
        badgePaint.color = PLAY_BADGE_DISC_ARGB
        canvas.drawCircle(centerX, centerY, radius, badgePaint)

        // Triangle inscribed in the disc, nudged right so it reads as
        // centred: a triangle's visual centre sits behind its centroid.
        val side = radius * 0.9f
        badgePaint.color = PLAY_BADGE_MARK_ARGB
        badgePath.reset()
        badgePath.moveTo(centerX - side * 0.42f, centerY - side * 0.62f)
        badgePath.lineTo(centerX - side * 0.42f, centerY + side * 0.62f)
        badgePath.lineTo(centerX + side * 0.68f, centerY)
        badgePath.close()
        canvas.drawPath(badgePath, badgePaint)
    }

    companion object {
        /**
         * Cache overshoot on each side. Wide enough that ordinary panning
         * blits rather than re-rasterizes; narrow enough that the extra
         * bitmap stays affordable on a 2GB board.
         */
        const val CACHE_MARGIN_PX = 256

        /** Play badge radius, in SCREEN px; comfortably tappable at any zoom. */
        const val PLAY_BADGE_RADIUS_PX = 34f
        private const val PLAY_BADGE_DISC_ARGB = 0x99000000.toInt()
        private const val PLAY_BADGE_MARK_ARGB = 0xFFFFFFFF.toInt()

        /**
         * The visibility floor may never make a stroke more than this many
         * times its own width — enough to rescue a hairline, not enough to
         * turn a line into a blob.
         */
        const val MAX_WIDTH_BOOST = 3f

        /** Ink never renders thinner than this on screen, at any zoom. */
        const val MIN_VISIBLE_WIDTH_PX = 1.2f

        const val EXPORT_MAX_EDGE_PX = 1536
        const val EXPORT_MAX_SCALE = 2f
        const val EXPORT_PADDING = 48f

        /** Mindmap node corner rounding, in world units. */
        const val NODE_RADIUS = 12f

        /** Bounded so a page of many stroke widths cannot grow the cache. */
        private const val DASH_CACHE_MAX = 24
    }
}
