package com.smartboard.teach.feature.whiteboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import com.smartboard.teach.domain.model.ContainerKind

/**
 * Selection overlay: marquee, bounding box and handles.
 *
 * Drawn in SCREEN space on purpose. If handles were drawn in world space they
 * would shrink as the teacher zooms out, right when the content is smallest
 * and hardest to grab. Constant physical size keeps them reachable at every
 * zoom level.
 */
internal fun DrawScope.drawSelectionChrome(state: BoardState) {
    val camera = state.camera

    // Live marquee.
    state.marqueeRect?.let { rect ->
        val left = camera.worldToScreenX(rect[0])
        val top = camera.worldToScreenY(rect[1])
        val right = camera.worldToScreenX(rect[2])
        val bottom = camera.worldToScreenY(rect[3])

        drawRect(
            color = MARQUEE_FILL,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
        )
        drawRect(
            color = SELECTION_ACCENT,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = DrawStroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
            ),
        )
    }

    if (!state.hasSelection) return

    // While rotating, draw the box captured at press. Rebuilding it from the
    // rotated content would re-fit an AXIS-ALIGNED rect every frame, so the
    // box stayed upright and swelled instead of turning with the ink.
    val rotating = state.dragState as? DragState.RotatingSelection
    val bounds = rotating?.originalBounds ?: state.selectionBounds()
    if (Selection.isEmpty(bounds)) return

    val left = camera.worldToScreenX(bounds[0])
    val top = camera.worldToScreenY(bounds[1])
    val right = camera.worldToScreenX(bounds[2])
    val bottom = camera.worldToScreenY(bounds[3])
    val midX = (left + right) / 2f
    val midY = (top + bottom) / 2f

    // Everything below is drawn in a frame rotated about the selection
    // centre, so box, handles and stalk turn together as one rigid object.
    rotate(
        degrees = Math.toDegrees(state.selectionRotation.toDouble()).toFloat(),
        pivot = Offset(midX, midY),
    ) {

    // Bounding box.
    drawRect(
        color = SELECTION_ACCENT,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = DrawStroke(
            width = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
        ),
    )

    // A mindmap gets the box but no handles. Its size is decided by the tree
    // — reflow recomputes every node rect — so a resize would be undone by
    // the next added node, and a rotate would leave the node chrome, which is
    // axis-aligned Compose, pointing at the wrong places. The box still shows
    // what a drag will move.
    if (state.containerById(state.selectedContainerId ?: "")?.kind == ContainerKind.MINDMAP) {
        return@rotate
    }

    // Rotate handle, on a stalk above the box.
    val rotateY = top - HANDLE_TOUCH_RADIUS_PX * Selection.ROTATE_HANDLE_GAP
    drawLine(
        color = SELECTION_ACCENT,
        start = Offset(midX, top),
        end = Offset(midX, rotateY),
        strokeWidth = 2f,
    )
    drawCircle(
        color = Color.White,
        radius = HANDLE_DRAW_RADIUS,
        center = Offset(midX, rotateY),
    )
    drawCircle(
        color = SELECTION_ACCENT,
        radius = HANDLE_DRAW_RADIUS,
        center = Offset(midX, rotateY),
        style = DrawStroke(width = 2.5f),
    )

    // Corner handles are square, edge handles smaller — the shape difference
    // tells a teacher which will scale both axes before they commit to a drag.
    listOf(
        Offset(left, top), Offset(right, top),
        Offset(left, bottom), Offset(right, bottom),
    ).forEach { drawHandle(it, HANDLE_DRAW_RADIUS) }

    listOf(
        Offset(midX, top), Offset(midX, bottom),
        Offset(left, midY), Offset(right, midY),
    ).forEach { drawHandle(it, HANDLE_DRAW_RADIUS * 0.72f) }

    }
}

private fun DrawScope.drawHandle(centre: Offset, radius: Float) {
    drawRect(
        color = Color.White,
        topLeft = Offset(centre.x - radius, centre.y - radius),
        size = Size(radius * 2, radius * 2),
    )
    drawRect(
        color = SELECTION_ACCENT,
        topLeft = Offset(centre.x - radius, centre.y - radius),
        size = Size(radius * 2, radius * 2),
        style = DrawStroke(width = 2.5f),
    )
}

private val SELECTION_ACCENT = Color(0xFF2F6FED)
private val MARQUEE_FILL = Color(0x142F6FED)

/** Visual radius; the touch radius is deliberately larger. */
private const val HANDLE_DRAW_RADIUS = 9f
