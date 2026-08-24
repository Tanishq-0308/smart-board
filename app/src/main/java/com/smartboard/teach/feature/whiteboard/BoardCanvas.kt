package com.smartboard.teach.feature.whiteboard

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.smartboard.teach.domain.model.ContainerKind
import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.TextBox
import com.smartboard.teach.feature.whiteboard.container.ContainerHitTest
import com.smartboard.teach.feature.whiteboard.instruments.COMPASS_LEG_CM
import com.smartboard.teach.feature.whiteboard.instruments.compassNeedleTip
import com.smartboard.teach.feature.whiteboard.instruments.hasSweep
import com.smartboard.teach.feature.whiteboard.instruments.compassRadius
import com.smartboard.teach.feature.whiteboard.instruments.InstrumentDrag
import com.smartboard.teach.feature.whiteboard.instruments.InstrumentGeometry
import com.smartboard.teach.feature.whiteboard.instruments.instrumentHitAt
import com.smartboard.teach.feature.whiteboard.instruments.hasRulingEdge
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.hypot

/**
 * The infinite drawing surface.
 *
 * Content lives in world coordinates; the camera decides what is visible.
 * Draw order each frame:
 *   1. background bitmap (world-anchored)
 *   2. committed strokes — blitted from the viewport cache when it is valid,
 *      drawn directly while panning or zooming
 *   3. the live stroke
 *   4. selection chrome (marquee, bounding box, handles)
 *
 * Two-finger gestures pan and zoom at ANY time, regardless of tool, so a
 * teacher never has to put the pen down to move the board.
 */
@Composable
fun BoardCanvas(
    state: BoardState,
    renderer: BoardRenderer,
    onStrokeFinished: (Stroke) -> Unit,
    onStrokesErased: (List<Stroke>) -> Unit,
    onSelectionMoved: (before: List<Stroke>, beforeBoxes: List<TextBox>) -> Unit,
    onCameraSettled: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundBitmap: Bitmap? = null,
    onSized: (Int, Int) -> Unit = { _, _ -> },
    onPointerDebug: ((PointerDebugInfo) -> Unit)? = null,
    /** Tapping a video's play badge; the path is the copied video file. */
    onPlayVideo: (String) -> Unit = {},
) {
    val palmRejection = remember { PalmRejection() }
    palmRejection.setStylusOnlyMode(state.stylusOnlyMode)

    val erasedThisDrag = remember { mutableListOf<Stroke>() }
    // Pre-transform copies, so a completed move/resize is one undoable action.
    val transformSnapshot = remember { TransformSnapshot() }
    val gridPainter = remember { GridPainter() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(state.canvasStyle.colorArgb))
            .onSizeChanged { size: IntSize ->
                renderer.ensureSurface(size.width, size.height)
                renderer.invalidateCache()
                state.markCommittedDirty()
                onSized(size.width, size.height)
            }
            // ONE handler for everything. Two-finger navigation and one-finger
            // drawing cannot live in separate pointerInput modifiers: the
            // first to see an event consumes it, and a second-finger arrival
            // has to be able to cancel a stroke the first finger already
            // started. Interleaving them here keeps that transition correct.
            .pointerInput(state.mode, state.tool, state.stylusOnlyMode, state.honourEraserButton) {
                awaitPointerEventScope {
                    var lastCentroid: Offset? = null
                    var lastSpan = 0f
                    var gestureWasMultiTouch = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.filter { it.pressed }

                        // --- two or more fingers: navigation, never ink ---
                        if (pressed.size >= 2) {
                            if (!gestureWasMultiTouch) {
                                // A second finger landed mid-stroke: throw the
                                // partial stroke away rather than leaving a
                                // stray mark where the teacher meant to pan.
                                state.cancelStroke()
                                state.dragState = DragState.None
                                palmRejection.reset()
                                gestureWasMultiTouch = true
                            }

                            val centroid = Offset(
                                pressed.map { it.position.x }.average().toFloat(),
                                pressed.map { it.position.y }.average().toFloat(),
                            )
                            val span = averageDistance(pressed, centroid)

                            lastCentroid?.let { previous ->
                                val dx = centroid.x - previous.x
                                val dy = centroid.y - previous.y
                                if (dx != 0f || dy != 0f) state.camera.pan(dx, dy)

                                if (lastSpan > MIN_PINCH_SPAN && span > MIN_PINCH_SPAN) {
                                    val factor = span / lastSpan
                                    if (abs(factor - 1f) > PINCH_DEADZONE) {
                                        state.camera.zoomBy(factor, centroid.x, centroid.y)
                                    }
                                }
                                state.markCommittedDirty()
                            }

                            lastCentroid = centroid
                            lastSpan = span
                            pressed.forEach { it.consume() }
                            continue
                        }

                        // --- all fingers lifted: settle the camera ---
                        if (pressed.isEmpty() && gestureWasMultiTouch) {
                            gestureWasMultiTouch = false
                            lastCentroid = null
                            lastSpan = 0f
                            onCameraSettled()
                            continue
                        }

                        // One finger still down after a pinch: do not start
                        // drawing with the leftover finger.
                        if (gestureWasMultiTouch) {
                            pressed.forEach { it.consume() }
                            continue
                        }

                        for (change in event.changes) {
                            if (change.isConsumed) continue

                            val pointerId = change.id.value
                            val isStylus = change.type == PointerType.Stylus ||
                                change.type == PointerType.Eraser
                            val nowMs = change.uptimeMillis

                            onPointerDebug?.invoke(
                                PointerDebugInfo(
                                    pointerType = change.type.toString(),
                                    pressure = change.pressure,
                                    pointerId = pointerId,
                                    contactCount = event.changes.size,
                                    accepted = false,
                                ),
                            )

                            val effectiveTool =
                                if (state.honourEraserButton && change.type == PointerType.Eraser) {
                                    DrawTool.ERASER
                                } else {
                                    state.tool
                                }

                            when (event.type) {
                                PointerEventType.Press ->
                                    if (palmRejection.shouldAcceptDown(pointerId, isStylus, nowMs)) {
                                        change.consume()
                                        handlePress(
                                            state, renderer, change, effectiveTool,
                                            erasedThisDrag, transformSnapshot,
                                        )
                                    }

                                PointerEventType.Move ->
                                    if (palmRejection.shouldAcceptMove(pointerId, isStylus, nowMs)) {
                                        change.consume()
                                        handleMove(
                                            state, renderer, change, effectiveTool, erasedThisDrag,
                                        )
                                    }

                                PointerEventType.Release ->
                                    if (palmRejection.onPointerUp(pointerId, isStylus, nowMs)) {
                                        change.consume()
                                        handleRelease(
                                            state, renderer, change, effectiveTool,
                                            erasedThisDrag, transformSnapshot,
                                            onStrokeFinished, onStrokesErased,
                                            onSelectionMoved, onCameraSettled,
                                            onPlayVideo,
                                        )
                                    }

                                else -> Unit
                            }
                        }
                    }
                }
            },
    ) {
        // --- draw phase ---
        // Reading these here (draw phase, not composition) keeps a pointer
        // sample costing a redraw rather than a recomposition.
        @Suppress("UNUSED_EXPRESSION") state.liveStrokeVersion
        @Suppress("UNUSED_EXPRESSION") state.committedVersion
        @Suppress("UNUSED_EXPRESSION") state.selectionVersion
        @Suppress("UNUSED_EXPRESSION") state.camera.version
        @Suppress("UNUSED_EXPRESSION") renderer.version
        @Suppress("UNUSED_EXPRESSION") state.mediaVersion

        val camera = state.camera
        val worldBounds = camera.visibleWorldBounds(size.width, size.height)

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            // Clip to the canvas bounds. Without this the cache blit — which
            // is deliberately larger than the viewport so small pans need no
            // re-rasterize — spills its margin over the sidebar and toolbar.
            native.save()
            native.clipRect(0f, 0f, size.width, size.height)

            // 1. The page's grid, in world space so squares travel with the
            //    ink. Drawn OUTSIDE the renderer cache: it is a screenful of
            //    lines, far cheaper to regenerate than to invalidate and
            //    re-rasterize every committed stroke whenever the board moves.
            native.save()
            native.scale(camera.zoom, camera.zoom)
            native.translate(-camera.offsetX, -camera.offsetY)
            gridPainter.draw(native, state.canvasStyle, worldBounds, camera.zoom)
            native.restore()

            // 2. Background, anchored in world space so it pans with the ink,
            //    and carrying its own position/scale/rotation so a teacher can
            //    place an imported image rather than being stuck at the origin.
            backgroundBitmap?.let { bg ->
                val placement = state.background
                val bx = placement?.x ?: 0f
                val by = placement?.y ?: 0f
                val bscale = placement?.scale ?: 1f
                val brot = placement?.rotation ?: 0f
                val w = bg.width.toFloat() * bscale
                val h = bg.height.toFloat() * bscale

                native.save()
                native.scale(camera.zoom, camera.zoom)
                native.translate(-camera.offsetX, -camera.offsetY)
                if (brot != 0f) {
                    native.rotate(
                        Math.toDegrees(brot.toDouble()).toFloat(),
                        bx + w / 2f,
                        by + h / 2f,
                    )
                }
                native.drawBitmap(bg, null, RectF(bx, by, bx + w, by + h), null)
                native.restore()
            }

            // 2. Committed strokes.
            val cache = renderer.cacheBitmap
            if (cache != null && renderer.isCacheUsable(camera)) {
                val offset = renderer.cacheBlitOffset(camera)
                native.drawBitmap(cache, offset[0], offset[1], null)
            } else {
                // Cache stale (mid-pan or mid-zoom): draw directly. Only
                // visible strokes are touched, so this stays bounded.
                native.save()
                native.scale(camera.zoom, camera.zoom)
                native.translate(-camera.offsetX, -camera.offsetY)
                renderer.drawStrokesDirect(
                    native, state.strokes, camera, worldBounds,
                    state.containers, state.mediaBitmaps,
                )
                native.restore()
            }

            // 3. The live stroke — clipped to its cell while it is being drawn,
            //    so ink does not spill past a cell edge and then snap back in
            //    when the stroke is committed and the cache re-rasterizes.
            state.liveStroke?.let { live ->
                native.save()
                native.scale(camera.zoom, camera.zoom)
                native.translate(-camera.offsetX, -camera.offsetY)
                val liveCell = live.containerId
                    ?.let { state.containerById(it) }
                    ?.cellAt(live.cellIndex)
                if (liveCell != null) {
                    native.clipRect(
                        liveCell.left, liveCell.top, liveCell.right, liveCell.bottom,
                    )
                }
                renderer.drawStroke(native, live, camera.zoom)
                native.restore()
            }

            native.restore()
        }

        // 4. Selection chrome, drawn in screen space so handles and dashes
        //    stay a constant physical size at any zoom — and clipped, since a
        //    selection panned off the edge must not paint over the sidebar.
        clipRect(0f, 0f, size.width, size.height) {
            drawSelectionChrome(state)
        }
    }
}

/** Retains pre-transform copies so a whole gesture is one undo step. */
private class TransformSnapshot {
    var strokes: List<Stroke> = emptyList()
    var textBoxes: List<TextBox> = emptyList()

    fun capture(state: BoardState) {
        strokes = state.selectedStrokes()
        textBoxes = state.selectedTextBoxes()
    }

    fun clear() {
        strokes = emptyList()
        textBoxes = emptyList()
    }
}

/**
 * Is the point inside the placed background?
 *
 * Rotation is undone about the centre before the rect test, so a turned image
 * is still hit where it visibly is rather than where its axis-aligned box was.
 */
/** Maps a world point back into the selection's unrotated frame. */
private fun unrotatePoint(
    x: Float,
    y: Float,
    cx: Float,
    cy: Float,
    rotation: Float,
): Pair<Float, Float> {
    if (rotation == 0f) return x to y
    val c = kotlin.math.cos(-rotation)
    val s = kotlin.math.sin(-rotation)
    val dx = x - cx
    val dy = y - cy
    return (cx + dx * c - dy * s) to (cy + dx * s + dy * c)
}

private fun backgroundHit(state: BoardState, worldX: Float, worldY: Float): Boolean {
    val bg = state.background ?: return false
    if (state.backgroundWidthPx <= 0f) return false

    val w = state.backgroundWidthPx * bg.scale
    val h = state.backgroundHeightPx * bg.scale
    val cx = bg.x + w / 2f
    val cy = bg.y + h / 2f

    var px = worldX
    var py = worldY
    if (bg.rotation != 0f) {
        val c = kotlin.math.cos(-bg.rotation)
        val s = kotlin.math.sin(-bg.rotation)
        val dx = worldX - cx
        val dy = worldY - cy
        px = cx + dx * c - dy * s
        py = cy + dx * s + dy * c
    }
    return px >= bg.x && px <= bg.x + w && py >= bg.y && py <= bg.y + h
}

private fun averageDistance(changes: List<PointerInputChange>, centroid: Offset): Float {
    if (changes.isEmpty()) return 0f
    var total = 0f
    changes.forEach { total += hypot(it.position.x - centroid.x, it.position.y - centroid.y) }
    return total / changes.size
}

// --- Press ------------------------------------------------------------------

private fun handlePress(
    state: BoardState,
    renderer: BoardRenderer,
    change: PointerInputChange,
    tool: DrawTool,
    erasedThisDrag: MutableList<Stroke>,
    snapshot: TransformSnapshot,
) {
    val camera = state.camera
    val worldX = camera.screenToWorldX(change.position.x)
    val worldY = camera.screenToWorldY(change.position.y)

    // Touching the BOARD commits any open text edit. The canvas has to do this
    // itself: it consumes the press before the text field could ever see it
    // lose focus, so without this the editor stays open forever. A press on a
    // text box is excluded — that press is what opens the editor, and clearing
    // here would close it in the same frame.
    if (Selection.textBoxAt(state.textBoxes, worldX, worldY) == null) {
        state.editingTextBoxId = null
    }

    // Is this press ON an instrument? If so it moves or turns it rather than
    // drawing, selecting or panning. Handled HERE rather than in a competing
    // pointer layer: the canvas is the single owner of pointer input, and a
    // second full-screen handler above it would win every hit-test and make
    // the board undrawable.
    //
    // Ahead of the mode switch, because an instrument is board furniture: a
    // teacher reaching for the ruler's pivot expects to turn it whatever tool
    // happens to be selected. Behind the switch it was grabbable only while
    // drawing, so with Select active the press fell through to the marquee and
    // the instrument simply would not rotate.
    if (state.instruments.isNotEmpty()) {
        val hit = instrumentHitAt(state, change.position)
        if (hit != null) {
            state.instrumentDragMode = hit.mode
            state.draggedInstrumentId = hit.id
            state.dragState = DragState.None
            return
        }
    }

    when (state.mode) {
        BoardMode.Pan -> {
            state.dragState = DragState.Panning
            return
        }

        BoardMode.Select -> {
            val handleRadius = camera.screenToWorldDistance(HANDLE_TOUCH_RADIUS_PX)

            // A handle wins over everything else, so a resize is never
            // mistaken for a new marquee.
            if (state.hasSelection) {
                // The rotate stalk is drawn in screen px, so convert its gap
                // rather than deriving it from the world-space handleRadius.
                // The chrome is drawn rotated about the selection centre, so
                // un-rotate the touch point by the same angle before testing
                // against the axis-aligned rect. Without this the handles are
                // drawn in one place and respond in another.
                val hb = state.selectionBounds()
                val (hx, hy) = unrotatePoint(
                    worldX, worldY,
                    (hb[0] + hb[2]) / 2f, (hb[1] + hb[3]) / 2f,
                    state.selectionRotation,
                )
                val handle = Selection.handleAt(
                    hb, hx, hy, handleRadius,
                    rotateGapWorld = camera.screenToWorldDistance(
                        HANDLE_TOUCH_RADIUS_PX * Selection.ROTATE_HANDLE_GAP,
                    ),
                )
                if (handle != null) {
                    snapshot.capture(state)
                    val originalStrokes = state.selectedStrokes()
                    val originalBoxes = state.selectedTextBoxes()
                    state.dragState = if (handle == Selection.Handle.ROTATE) {
                        val b = state.selectionBounds()
                        val pivotX = (b[0] + b[2]) / 2f
                        val pivotY = (b[1] + b[3]) / 2f
                        DragState.RotatingSelection(
                            pivotX, pivotY,
                            atan2(worldY - pivotY, worldX - pivotX),
                            originalStrokes, originalBoxes,
                            if (state.backgroundSelected) state.background else null,
                            // Frozen so the drawn box turns instead of
                            // re-fitting (and growing) around rotated ink.
                            b.copyOf(),
                        )
                    } else {
                        DragState.ResizingSelection(
                            handle, state.selectionBounds().copyOf(),
                            originalStrokes, originalBoxes,
                            if (state.backgroundSelected) state.background else null,
                        )
                    }
                    return
                }

                // Dragging inside the selection moves it.
                if (Selection.pointInRect(worldX, worldY, state.selectionBounds())) {
                    snapshot.capture(state)
                    state.dragState = DragState.MovingSelection(worldX, worldY)
                    return
                }
            }

            // Tap on an object selects it; tap on empty space starts a marquee.
            val tolerance = camera.screenToWorldDistance(TAP_TOLERANCE_PX)
            val hitBox = Selection.textBoxAt(state.textBoxes, worldX, worldY)
            // Ink inside a table cell or mindmap node is not independently
            // selectable: dragging one word out of a cell it is meant to live
            // in is never what the teacher wants. The container is the object;
            // its ink moves with it.
            val hitStroke = if (hitBox == null) {
                Selection.strokeAt(state.strokes, worldX, worldY, tolerance)
                    ?.takeIf { it.containerId == null }
            } else {
                null
            }

            when {
                hitBox != null -> {
                    state.selectOnly(emptyList(), listOf(hitBox.id))
                    snapshot.capture(state)
                    state.dragState = DragState.MovingSelection(worldX, worldY)
                }

                hitStroke != null -> {
                    state.selectOnly(listOf(hitStroke.id), emptyList())
                    snapshot.capture(state)
                    state.dragState = DragState.MovingSelection(worldX, worldY)
                }

                // Containers come after ink but before the background: ink
                // written ON a picture stays grabbable, while the picture
                // still beats a full-page backdrop behind it.
                ContainerHitTest.containerAt(state.containers, worldX, worldY) != null -> {
                    val hit = ContainerHitTest.containerAt(state.containers, worldX, worldY)!!
                    state.clearSelection()
                    state.selectedContainerId = hit.id
                    // Which node was tapped, so the mindmap chrome knows what
                    // "add a child" and "delete" refer to. -1 when the tap
                    // landed between nodes, which selects the map as a whole.
                    state.selectedCellIndex = hit.cellIndexAt(worldX, worldY)
                    state.bumpSelection()
                    snapshot.capture(state)
                    state.dragState = DragState.MovingSelection(worldX, worldY)
                }

                // Background is checked LAST: ink drawn on top of an imported
                // worksheet must stay grabbable, otherwise the image would
                // swallow every tap once it covers the page.
                backgroundHit(state, worldX, worldY) -> {
                    state.clearSelection()
                    state.backgroundSelected = true
                    state.bumpSelection()
                    snapshot.capture(state)
                    state.dragState = DragState.MovingSelection(worldX, worldY)
                }

                else -> {
                    state.clearSelection()
                    state.dragState = DragState.Marquee(worldX, worldY)
                    state.marqueeRect = floatArrayOf(worldX, worldY, worldX, worldY)
                }
            }
            return
        }

        else -> Unit
    }

    // Draw / erase.
    if (tool == DrawTool.ERASER) {
        erasedThisDrag.clear()
        eraseAt(state, renderer, worldX, worldY, erasedThisDrag)
        state.dragState = DragState.Drawing
        return
    }

    // Which cell is under the pen? Resolved ONCE, here, and held for the whole
    // stroke — handleMove is the hot path and must not repeat this.
    val cell = ContainerHitTest.cellAt(state.containers, worldX, worldY)

    // A TAP inside a mindmap node focuses it, and a tap on a video's play
    // badge opens it — but whether this press is a tap or the start of
    // handwriting is not knowable yet. It is decided at release by whether the
    // pen moved; acting here would leave a stray dot on every tap, and would
    // fire the player when the teacher meant to annotate the frame.
    state.tappedCell = cell?.takeIf {
        val kind = state.containerById(it.containerId)?.kind
        kind == ContainerKind.MINDMAP || kind == ContainerKind.VIDEO
    }

    // Does this stroke rule against the instrument? Decided ONCE at press, so
    // a hand that drifts off the edge mid-stroke still draws a straight line
    // rather than veering away — which is how a real ruler behaves.
    state.snappingToInstrument = shouldStartSnapped(state, worldX, worldY)
    val start = snapPoint(state, worldX, worldY)

    val builder = state.beginStroke(
        tool = tool,
        initialPressure = change.pressure,
        containerId = cell?.containerId,
        cellIndex = cell?.cellIndex ?: -1,
    )
    if (tool.isTwoPointShape) {
        builder.setShapeEndpoints(start[0], start[1], start[0], start[1])
    } else {
        builder.addPoint(start[0], start[1], change.pressure)
    }
    state.onLivePointAdded()
    state.dragState = DragState.Drawing
}

// --- Move -------------------------------------------------------------------

private fun handleMove(
    state: BoardState,
    renderer: BoardRenderer,
    change: PointerInputChange,
    tool: DrawTool,
    erasedThisDrag: MutableList<Stroke>,
) {
    val camera = state.camera
    val worldX = camera.screenToWorldX(change.position.x)
    val worldY = camera.screenToWorldY(change.position.y)

    // An in-progress instrument drag owns the gesture.
    if (state.instrumentDragMode != InstrumentDrag.NONE) {
        applyInstrumentDrag(state, change)
        return
    }

    when (val drag = state.dragState) {
        DragState.Panning -> {
            val previous = change.previousPosition
            camera.pan(change.position.x - previous.x, change.position.y - previous.y)
            state.markCommittedDirty()
            return
        }

        is DragState.Marquee -> {
            state.marqueeRect =
                Selection.normalizeRect(drag.startX, drag.startY, worldX, worldY)
            state.bumpSelection()
            return
        }

        is DragState.MovingSelection -> {
            val dx = worldX - drag.lastX
            val dy = worldY - drag.lastY
            translateSelection(state, dx, dy)
            state.dragState = DragState.MovingSelection(worldX, worldY)
            renderer.invalidateCache()
            state.markCommittedDirty()
            return
        }

        is DragState.ResizingSelection -> {
            val r = Selection.scaleForHandleDrag(drag.handle, drag.originalBounds, worldX, worldY)
            // A ROTATED selection scales uniformly.
            //
            // originalBounds is axis-aligned but the selection is not, so a
            // per-axis scale would be applied in the wrong frame and shear the
            // content. Uniform scale commutes with rotation, so it is correct
            // in any frame. The cost is that a rotated selection cannot be
            // stretched on one axis — a fair trade for never shearing a
            // teacher's diagram.
            val (sx, sy) = if (state.selectionRotation != 0f) {
                val uniform = maxOf(r[0], r[1])
                uniform to uniform
            } else {
                r[0] to r[1]
            }
            // Always rebuild from the ORIGINAL objects captured at press.
            // Applying an incremental step each frame compounds the stroke
            // width (geometry converges, width does not) and a slow drag left
            // a hairline rendered as a fat blob.
            applyScaleFromOriginal(state, drag, r[2], r[3], sx, sy)
            renderer.invalidateCache()
            state.markCommittedDirty()
            return
        }

        is DragState.RotatingSelection -> {
            val angle = atan2(worldY - drag.pivotY, worldX - drag.pivotX)
            // Total rotation from the press angle, applied to the ORIGINALS —
            // never an incremental delta on the current state, which would
            // accumulate floating-point drift over a long drag.
            state.selectionRotation = angle - drag.startAngle
            applyRotationFromOriginal(state, drag, angle - drag.startAngle)
            renderer.invalidateCache()
            state.markCommittedDirty()
            return
        }

        DragState.Drawing -> Unit
        DragState.None -> return
    }

    if (tool == DrawTool.ERASER) {
        eraseAt(state, renderer, worldX, worldY, erasedThisDrag)
        return
    }

    val builder = state.liveBuilder ?: return

    if (tool.isTwoPointShape) {
        val live = state.liveStroke ?: return
        val end = snapPoint(state, worldX, worldY)
        builder.setShapeEndpoints(live.x(0), live.y(0), end[0], end[1])
        state.onLivePointAdded()
        return
    }

    var added = false

    // Historical samples prevent fast strokes looking polygonal. Compose's
    // HistoricalChange carries position but NOT pressure, so pressure is
    // ramped from the previous frame's value to this one across the batch —
    // pinning them all to the current value puts a visible width step at
    // every frame boundary.
    val history = change.historical
    if (history.isNotEmpty()) {
        val from = state.lastPressure
        val to = change.pressure
        val n = history.size
        history.forEachIndexed { index, historical ->
            val t = (index + 1).toFloat() / (n + 1)
            val interpolated = from + (to - from) * t
            val hist = snapPoint(
                state,
                camera.screenToWorldX(historical.position.x),
                camera.screenToWorldY(historical.position.y),
            )
            if (builder.addPoint(
                    hist[0],
                    hist[1],
                    interpolated,
                    applySmoothing = !state.snappingToInstrument,
                )
            ) {
                added = true
            }
        }
    }

    val moved = snapPoint(state, worldX, worldY)
    // Snapped samples bypass smoothing: the EMA exists to steady a wobbling
    // hand, but a projected point is already exactly on the ruling edge, and
    // averaging it back toward the finger drags the ink off the edge — which
    // showed up as a dense zigzag rather than a ruled line.
    if (builder.addPoint(
            moved[0],
            moved[1],
            change.pressure,
            applySmoothing = !state.snappingToInstrument,
        )
    ) {
        added = true
    }
    state.lastPressure = change.pressure

    if (added) state.onLivePointAdded()
}

// --- Release ----------------------------------------------------------------

private fun handleRelease(
    state: BoardState,
    renderer: BoardRenderer,
    change: PointerInputChange,
    tool: DrawTool,
    erasedThisDrag: MutableList<Stroke>,
    snapshot: TransformSnapshot,
    onStrokeFinished: (Stroke) -> Unit,
    onStrokesErased: (List<Stroke>) -> Unit,
    onSelectionMoved: (List<Stroke>, List<TextBox>) -> Unit,
    onCameraSettled: () -> Unit,
    onPlayVideo: (String) -> Unit,
) {
    when (val drag = state.dragState) {
        DragState.Panning -> {
            state.dragState = DragState.None
            onCameraSettled()
            return
        }

        is DragState.Marquee -> {
            val rect = state.marqueeRect
            if (rect != null) {
                // Contained ink joins the selection only when its WHOLE
                // container is enclosed. A marquee across half a table would
                // otherwise select some of its ink, and dragging that would
                // pull the words out of their cells and leave the grid behind.
                val strokes = Selection.strokesInMarquee(state.strokes, rect).filter { stroke ->
                    val id = stroke.containerId ?: return@filter true
                    val container = state.containerById(id) ?: return@filter true
                    ContainerHitTest.isFullyInside(container, rect)
                }
                state.selectOnly(
                    strokes.map { it.id },
                    Selection.textBoxesInMarquee(state.textBoxes, rect).map { it.id },
                )
            }
            state.marqueeRect = null
            state.dragState = DragState.None
            return
        }

        is DragState.MovingSelection,
        is DragState.ResizingSelection,
        is DragState.RotatingSelection,
        -> {
            if (snapshot.strokes.isNotEmpty() || snapshot.textBoxes.isNotEmpty()) {
                onSelectionMoved(snapshot.strokes, snapshot.textBoxes)
            }
            snapshot.clear()
            state.dragState = DragState.None
            renderer.invalidateCache()
            onCameraSettled()
            return
        }

        else -> Unit
    }

    // A finished sweep becomes ordinary ink, so it undoes, saves and exports
    // like anything else drawn on the board — the compass is the tool, not the
    // owner of what it drew.
    if (state.instrumentDragMode == InstrumentDrag.SWEEP) {
        commitCompassArc(state, onStrokeFinished)
    }

    state.dragState = DragState.None
    state.snappingToInstrument = false
    state.instrumentDragMode = InstrumentDrag.NONE
    state.draggedInstrumentId = null

    if (tool == DrawTool.ERASER) {
        // The eraser never sets it, but a tool switch mid-gesture could leave
        // one behind, and a stale tap would focus the wrong node later.
        state.tappedCell = null
        if (erasedThisDrag.isNotEmpty()) {
            onStrokesErased(erasedThisDrag.toList())
            erasedThisDrag.clear()
        }
        return
    }

    val finished = state.endStroke()
    val tapped = state.tappedCell
    state.tappedCell = null

    // A press that never travelled is a TAP, and its one-dot stroke is thrown
    // away rather than left as a mark. Measured by distance rather than "did a
    // move event arrive", because a stylus always reports jitter while resting
    // on the glass. Anything that did travel is handwriting, and falls through
    // to be kept — including ink drawn across a video's poster frame.
    val wasTap = finished == null || strokeIsTap(finished)
    if (tapped != null) {
        val container = state.containerById(tapped.containerId)

        // A tap on the play badge opens the video; a tap anywhere else on it
        // just selects, so the frame can still be moved and annotated.
        if (wasTap && container?.kind == ContainerKind.VIDEO) {
            val cellRect = container.cellAt(tapped.cellIndex)
            val path = container.mediaPath
            val upX = state.camera.screenToWorldX(change.position.x)
            val upY = state.camera.screenToWorldY(change.position.y)
            if (cellRect != null && path != null &&
                onPlayBadge(state, cellRect.centerX, cellRect.centerY, upX, upY)
            ) {
                onPlayVideo(path)
                return
            }
        }

        // Either way, a press inside a container FOCUSES it — so a mindmap's
        // +/x buttons follow the pen and the teacher never switches tools to
        // grow the tree.
        state.clearSelection()
        state.selectedContainerId = tapped.containerId
        state.selectedCellIndex = tapped.cellIndex
        state.bumpSelection()
        if (wasTap) return
    }

    finished?.let(onStrokeFinished)
}

/**
 * True when a world point falls on a video's play badge.
 *
 * The badge is drawn at a constant SCREEN size, so its world radius grows as
 * the board is zoomed out — the test has to match, or the tappable area drifts
 * away from the circle the teacher can see.
 */
internal fun onPlayBadge(
    state: BoardState,
    centerX: Float,
    centerY: Float,
    worldX: Float,
    worldY: Float,
): Boolean {
    val radius = BoardRenderer.PLAY_BADGE_RADIUS_PX / state.camera.zoom.coerceAtLeast(0.01f)
    return hypot(worldX - centerX, worldY - centerY) <= radius
}

/** True when a finished stroke never travelled far enough to be a mark. */
internal fun strokeIsTap(stroke: Stroke): Boolean {
    if (stroke.pointCount == 0) return true
    val x0 = stroke.x(0)
    val y0 = stroke.y(0)
    for (i in 1 until stroke.pointCount) {
        if (hypot(stroke.x(i) - x0, stroke.y(i) - y0) > TAP_SLOP_PX) return false
    }
    return true
}

/**
 * How far a press may travel and still count as a tap, in WORLD units.
 *
 * Generous enough to absorb stylus jitter and a finger rolling on the glass,
 * small enough that a deliberate dot or a short tick still writes.
 */
internal const val TAP_SLOP_PX = 6f

// --- Helpers ----------------------------------------------------------------

private fun translateSelection(state: BoardState, dx: Float, dy: Float) {
    state.selectedContainerId?.let { id ->
        val index = state.containers.indexOfFirst { it.id == id }
        if (index >= 0) {
            val c = state.containers[index]
            state.containers[index] = c.copy(
                x = c.x + dx,
                y = c.y + dy,
                cells = c.cells.map {
                    it.copy(
                        left = it.left + dx, top = it.top + dy,
                        right = it.right + dx, bottom = it.bottom + dy,
                    )
                },
            )
            // Contained ink is in world coordinates, so it must be translated
            // by the same delta or it stays behind while the frame moves.
            state.strokes.forEachIndexed { i, stroke ->
                if (stroke.containerId == id) {
                    state.strokes[i] = Selection.translateStroke(stroke, dx, dy)
                }
            }
        }
        state.bumpSelection()
        return
    }
    if (state.backgroundSelected) {
        state.background = state.background?.let { it.copy(x = it.x + dx, y = it.y + dy) }
        state.bumpSelection()
        return
    }
    state.selectedStrokeIds.forEach { id ->
        val index = state.strokes.indexOfFirst { it.id == id }
        if (index >= 0) {
            state.strokes[index] = Selection.translateStroke(state.strokes[index], dx, dy)
        }
    }
    state.selectedTextBoxIds.forEach { id ->
        val index = state.textBoxes.indexOfFirst { it.id == id }
        if (index >= 0) {
            state.textBoxes[index] = Selection.translateTextBox(state.textBoxes[index], dx, dy)
        }
    }
    state.bumpSelection()
}

/**
 * Rebuilds the selection from the objects captured at press.
 *
 * The absolute scale is applied ONCE to the original geometry each frame.
 * Stepping incrementally from the current state compounds stroke width — the
 * geometry converges but the width multiplier does not — which turned a
 * hairline into a blob over a slow drag.
 */
private fun applyScaleFromOriginal(
    state: BoardState,
    drag: DragState.ResizingSelection,
    pivotX: Float,
    pivotY: Float,
    scaleX: Float,
    scaleY: Float,
) {
    drag.originalBackground?.let { original ->
        // Uniform: a non-uniform drag would stretch a worksheet out of shape.
        // The larger axis wins so the image tracks the corner being dragged.
        val uniform = maxOf(scaleX, scaleY).coerceAtLeast(MIN_BACKGROUND_SCALE)
        state.background = original.copy(
            x = pivotX + (original.x - pivotX) * uniform,
            y = pivotY + (original.y - pivotY) * uniform,
            scale = original.scale * uniform,
        )
        state.bumpSelection()
        return
    }
    drag.originalStrokes.forEach { original ->
        val index = state.strokes.indexOfFirst { it.id == original.id }
        if (index >= 0) {
            state.strokes[index] =
                Selection.scaleStroke(original, pivotX, pivotY, scaleX, scaleY)
        }
    }
    drag.originalBoxes.forEach { original ->
        val index = state.textBoxes.indexOfFirst { it.id == original.id }
        if (index >= 0) {
            state.textBoxes[index] =
                Selection.scaleTextBox(original, pivotX, pivotY, scaleX, scaleY)
        }
    }
    state.bumpSelection()
}

/** Total rotation applied to the originals, for the same reason. */
private fun applyRotationFromOriginal(
    state: BoardState,
    drag: DragState.RotatingSelection,
    radians: Float,
) {
    drag.originalBackground?.let { original ->
        state.background = original.copy(rotation = original.rotation + radians)
        state.bumpSelection()
        return
    }
    drag.originalStrokes.forEach { original ->
        val index = state.strokes.indexOfFirst { it.id == original.id }
        if (index >= 0) {
            state.strokes[index] =
                Selection.rotateStroke(original, drag.pivotX, drag.pivotY, radians)
        }
    }
    drag.originalBoxes.forEach { original ->
        val index = state.textBoxes.indexOfFirst { it.id == original.id }
        if (index >= 0) {
            state.textBoxes[index] =
                Selection.rotateTextBox(original, drag.pivotX, drag.pivotY, radians)
        }
    }
    state.bumpSelection()
}

/**
 * Removes every stroke under the eraser, accumulating them into [erasedThisDrag].
 *
 * That accumulation is what makes an erase undoable AND saveable: the release
 * handler reports the list through `onStrokesErased`, which is the only place
 * that records [BoardCommand.EraseStrokes] and the only place that persists.
 * Without it the strokes were removed from the page, never written to history
 * and never written to disk — so they came back on reload and then vanished
 * again on the next unrelated save.
 */
private fun eraseAt(
    state: BoardState,
    renderer: BoardRenderer,
    worldX: Float,
    worldY: Float,
    erasedThisDrag: MutableList<Stroke>,
) {
    val radius = state.camera.screenToWorldDistance(state.eraserScreenRadius)
    val hits = state.strokes.filter { StrokeHitTest.intersects(it, worldX, worldY, radius) }
    if (hits.isEmpty()) return

    erasedThisDrag += hits
    state.strokes.removeAll(hits)
    renderer.invalidateCache()
    state.markCommittedDirty()
}

/** Live pointer telemetry for the Settings debug overlay. */
data class PointerDebugInfo(
    val pointerType: String,
    val pressure: Float,
    val pointerId: Long,
    val contactCount: Int,
    val accepted: Boolean,
)

/** Screen-space radius within which a touch counts as grabbing a handle. */
const val HANDLE_TOUCH_RADIUS_PX = 28f

/** Screen-space slop for tapping a stroke to select it. */
const val TAP_TOLERANCE_PX = 16f

private const val MIN_PINCH_SPAN = 8f
private const val PINCH_DEADZONE = 0.005f

/** Floor on background scale, so an image can never be shrunk to nothing. */
private const val MIN_BACKGROUND_SCALE = 0.05f

/**
 * True when a stroke starting here should be ruled against the instrument.
 *
 * Only strokes that BEGIN near the ruling edge are captured, so a teacher can
 * still label a diagram beside the instrument without putting it away first.
 */
private fun shouldStartSnapped(state: BoardState, worldX: Float, worldY: Float): Boolean {
    // With several instruments down, rule against the NEAREST edge in range —
    // resting a set square on a ruler is exactly why two are on the board, and
    // picking the wrong one would rule the line at the wrong angle.
    var bestId: String? = null
    var bestDistance = Float.MAX_VALUE
    state.instruments.forEach { instrument ->
        if (!instrument.kind.hasRulingEdge) return@forEach
        val edge = InstrumentGeometry.edgeOf(instrument, state.camera.zoom)
        val distance = InstrumentGeometry.distanceToEdge(edge, worldX, worldY)
        if (distance < bestDistance &&
            InstrumentGeometry.shouldSnap(distance, state.camera.zoom)
        ) {
            bestDistance = distance
            bestId = instrument.id
        }
    }
    state.snapInstrumentId = bestId
    return bestId != null
}

/** Projects a sample onto the ruling edge while this stroke is snapped. */
private fun snapPoint(state: BoardState, worldX: Float, worldY: Float): FloatArray {
    if (!state.snappingToInstrument) return floatArrayOf(worldX, worldY)
    val instrument = state.instrumentById(state.snapInstrumentId)
        ?: return floatArrayOf(worldX, worldY)
    val edge = InstrumentGeometry.edgeOf(instrument, state.camera.zoom)
    return InstrumentGeometry.projectOntoEdge(edge, worldX, worldY)
}

/**
 * Turns the compass's swept arc into a stroke and clears the sweep.
 *
 * Sampled at a fixed angular step rather than a fixed point count, so a small
 * arc does not carry the cost of a full circle and a full circle does not come
 * out as a visible polygon.
 */
private fun commitCompassArc(state: BoardState, onStrokeFinished: (Stroke) -> Unit) {
    val instrument = state.instrumentById(state.draggedInstrumentId) ?: return
    if (!instrument.hasSweep) return

    val camera = state.camera
    val needleScreen = compassNeedleTip(instrument, camera)
    val cx = camera.screenToWorldX(needleScreen.x)
    val cy = camera.screenToWorldY(needleScreen.y)
    val radius = instrument.compassRadius

    val from = instrument.sweepStart
    val total = instrument.sweepEnd - instrument.sweepStart
    val steps = (abs(total) / ARC_STEP_RAD).toInt().coerceIn(2, MAX_ARC_POINTS)

    // Flagged so shape recognition leaves it alone: the arc is already exact
    // geometry at the radius the teacher set, and snapping it to the
    // recogniser's idea of a circle would quietly change that radius.
    state.suppressShapeSnap = true
    val builder = state.beginStroke(DrawTool.PEN, initialPressure = 1f)
    for (i in 0..steps) {
        val angle = from + total * (i.toFloat() / steps)
        builder.addPoint(
            cx + cos(angle) * radius,
            cy + sin(angle) * radius,
            1f,
            // Smoothing is for a wobbling hand; these points are already
            // exactly on the circle and averaging them would flatten it.
            applySmoothing = false,
        )
    }
    state.endStroke()?.let(onStrokeFinished)
    state.suppressShapeSnap = false

    // The arc is ink now, so the tool stops showing its own copy of it.
    state.replaceInstrument(instrument.copy(sweepStart = 0f, sweepEnd = 0f))
}

/** Roughly three degrees per sample: smooth at any radius a board can show. */
private const val ARC_STEP_RAD = 0.05f
private const val MAX_ARC_POINTS = 512

/** A point rotated about another, in world units. */
private fun rotateAbout(px: Float, py: Float, cx: Float, cy: Float, angle: Float): FloatArray {
    val c = cos(angle)
    val s = sin(angle)
    val dx = px - cx
    val dy = py - cy
    return floatArrayOf(cx + dx * c - dy * s, cy + dx * s + dy * c)
}

/**
 * An angle step folded into (-pi, pi].
 *
 * atan2 wraps at the half turn, so a pencil crossing that seam reports a step
 * of nearly a full turn in the wrong direction. Without this the arc would
 * jump backwards every time a teacher swept past it.
 */
private fun normaliseAngle(angle: Float): Float {
    var a = angle
    while (a > PI) a -= (PI * 2).toFloat()
    while (a <= -PI) a += (PI * 2).toFloat()
    return a
}

private const val PI = Math.PI.toFloat()

/** How far the legs may open: closed enough to be useless, wide enough to splay. */
private const val MIN_SPREAD_RAD = 0.12f
private const val MAX_SPREAD_RAD = 2.4f

/** Moves, turns or resizes the instrument for this pointer sample. */
private fun applyInstrumentDrag(state: BoardState, change: PointerInputChange) {
    val current = state.instrumentById(state.draggedInstrumentId) ?: return
    val camera = state.camera
    val ax = camera.worldToScreenX(current.x)
    val ay = camera.worldToScreenY(current.y)

    val updated = when (state.instrumentDragMode) {
        InstrumentDrag.MOVE -> {
            val previous = change.previousPosition
            current.copy(
                x = current.x + (change.position.x - previous.x) / camera.zoom,
                y = current.y + (change.position.y - previous.y) / camera.zoom,
            )
        }

        // Tracks the hand rather than accumulating deltas, so a fast turn
        // cannot drift away from the finger.
        InstrumentDrag.ROTATE -> current.copy(
            rotation = atan2(change.position.y - ay, change.position.x - ax),
        )

        // Opening the legs. Measured from the hinge, so the spread follows the
        // hand the way the real hinge does.
        InstrumentDrag.SPREAD -> {
            val legPx = COMPASS_LEG_CM * InstrumentGeometry.pxPerCm * camera.zoom
            val reach = hypot(change.position.x - ax, change.position.y - ay)
            // The hand is on ONE leg, half the spread off the centre line.
            val half = asin((reach / legPx).coerceIn(0f, 1f))
            current.copy(
                spreadRad = (half * 2f).coerceIn(MIN_SPREAD_RAD, MAX_SPREAD_RAD),
                // A resized compass starts its arc afresh: the old sweep was
                // drawn at a different radius and would not join up.
                sweepStart = 0f,
                sweepEnd = 0f,
            )
        }

        // Sweeping the pencil round the needle. The compass ROTATES about the
        // needle rather than the hinge, which is what keeps the needle — and
        // so the circle's centre — still while the arc is drawn.
        InstrumentDrag.SWEEP -> {
            val needle = compassNeedleTip(current, camera)
            val angle = atan2(change.position.y - needle.y, change.position.x - needle.x)
            val previous = atan2(
                change.previousPosition.y - needle.y,
                change.previousPosition.x - needle.x,
            )
            // Unwrapped, so a sweep past the 180-degree seam keeps counting up
            // instead of jumping to a huge negative arc.
            val step = normaliseAngle(angle - previous)

            val startedSweep = !current.hasSweep
            val sweepEnd = if (startedSweep) previous + step else current.sweepEnd + step

            // Turning the tool by the same step keeps the pencil under the
            // hand while the needle stays put.
            val hinge = rotateAbout(
                px = current.x,
                py = current.y,
                cx = camera.screenToWorldX(needle.x),
                cy = camera.screenToWorldY(needle.y),
                angle = step,
            )
            current.copy(
                x = hinge[0],
                y = hinge[1],
                rotation = current.rotation + step,
                sweepStart = if (startedSweep) previous else current.sweepStart,
                sweepEnd = sweepEnd,
            )
        }

        InstrumentDrag.NONE -> current
    }
    state.replaceInstrument(updated)
}
