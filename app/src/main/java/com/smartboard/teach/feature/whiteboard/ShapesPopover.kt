package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import com.smartboard.teach.domain.model.DrawTool

/**
 * Every shape, laid out as on the reference panel: seven per row, grouped by
 * family — lines, triangles, quadrilaterals, round forms, polygons, solids.
 */
private val SHAPE_GRID: List<List<DrawTool>> = listOf(
    listOf(
        DrawTool.LINE, DrawTool.DASHED_LINE, DrawTool.ARROW, DrawTool.DASHED_ARROW,
        DrawTool.TRIANGLE, DrawTool.ISOSCELES_TRIANGLE, DrawTool.RIGHT_TRIANGLE,
    ),
    listOf(
        DrawTool.DIAMOND, DrawTool.PARALLELOGRAM, DrawTool.TRAPEZOID, DrawTool.RECT,
        DrawTool.ROUNDED_RECT, DrawTool.CIRCLE, DrawTool.ELLIPSE,
    ),
    listOf(
        DrawTool.SEMICIRCLE, DrawTool.PENTAGON, DrawTool.HEXAGON, DrawTool.STAR,
        DrawTool.CUBE, DrawTool.PYRAMID, DrawTool.PRISM,
    ),
    listOf(
        DrawTool.TETRAHEDRON, DrawTool.CYLINDER, DrawTool.CONE, DrawTool.SPHERE,
    ),
)

/**
 * The shape picker.
 *
 * Each button draws itself with the SAME geometry the board uses, rather than
 * shipping 25 hand-drawn icons. A button therefore cannot drift out of sync
 * with what it produces — change a pentagon's proportions and its icon follows
 * — and adding a shape means adding one enum entry, not an icon asset.
 */
@Composable
fun ShapesPopover(
    state: BoardState,
    onPicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens
    val isDrawing = state.mode == BoardMode.Draw

    FloatingIsland(modifier = modifier, contentPadding = PaddingValues(dimens.gutterSmall)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SHAPE_GRID.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { tool ->
                        ShapeCell(
                            tool = tool,
                            selected = isDrawing && state.tool == tool,
                        ) {
                            selectShapeTool(state, tool)
                            onPicked()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapeCell(tool: DrawTool, selected: Boolean, onClick: () -> Unit) {
    val dimens = SmartBoardTheme.dimens
    val tint = if (selected) TextOnChrome else TextOnChromeMuted
    Box(
        modifier = Modifier
            .size(dimens.chromeButton)
            .clip(RoundedCornerShape(dimens.cornerRadius * 0.5f))
            .background(if (selected) Accent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(dimens.chromeIcon)) {
            drawShapeGlyph(tool, tint)
        }
    }
}

/**
 * Renders a shape into the icon box using the board's own geometry.
 *
 * Lines and arrows are drawn corner-to-corner so they read as diagonals, the
 * way the reference panel shows them; everything else fills the box.
 */
internal fun DrawScope.drawShapeGlyph(tool: DrawTool, tint: Color) {
    val pad = size.minDimension * 0.14f
    val left = pad
    val top = pad
    val right = size.width - pad
    val bottom = size.height - pad

    val outline = if (tool.isTwoPointShape && !tool.isSolid && isLinear(tool)) {
        // Bottom-left to top-right, matching the reference's diagonal glyphs.
        ShapeGeometry.outlineFor(tool, left, bottom, right, top)
    } else {
        ShapeGeometry.outlineFor(tool, left, top, right, bottom)
    }

    val width = size.minDimension * 0.09f
    val stroke = Stroke(width = width, cap = StrokeCap.Round)
    val dashed = Stroke(
        width = width,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * 2.2f, width * 1.6f)),
    )

    fun polyline(line: ShapeGeometry.Polyline, style: Stroke) {
        val pts = line.points
        if (pts.size < 4) return
        val path = Path().apply {
            moveTo(pts[0], pts[1])
            var i = 2
            while (i + 1 < pts.size) {
                lineTo(pts[i], pts[i + 1])
                i += 2
            }
            if (line.closed) close()
        }
        drawPath(path, tint, style = style)
    }

    val bodyStyle = if (tool.isDashed) dashed else stroke
    outline.visible.forEach { polyline(it, bodyStyle) }
    outline.ovals.forEach {
        drawOval(
            color = tint,
            topLeft = Offset(it.left, it.top),
            size = Size(it.right - it.left, it.bottom - it.top),
            style = stroke,
        )
    }
    outline.arcs.forEach {
        drawArc(
            color = tint,
            startAngle = it.startDegrees,
            sweepAngle = it.sweepDegrees,
            useCenter = false,
            topLeft = Offset(it.left, it.top),
            size = Size(it.right - it.left, it.bottom - it.top),
            style = stroke,
        )
    }
    // Hidden edges, faded so the glyph reads as a solid at icon size.
    outline.hidden.forEach { polyline(it, dashed) }
    outline.hiddenOvals.forEach {
        drawOval(
            color = tint.copy(alpha = 0.5f),
            topLeft = Offset(it.left, it.top),
            size = Size(it.right - it.left, it.bottom - it.top),
            style = dashed,
        )
    }

    if (tool.hasArrowHead) {
        val from = Offset(left, bottom)
        val to = Offset(right, top)
        val angle = kotlin.math.atan2(to.y - from.y, to.x - from.x)
        val head = size.minDimension * 0.3f
        val spread = 0.5f
        listOf(angle - spread, angle + spread).forEach { a ->
            drawLine(
                color = tint,
                start = to,
                end = Offset(
                    to.x - head * kotlin.math.cos(a),
                    to.y - head * kotlin.math.sin(a),
                ),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** True for the four tools that are a bare line rather than an enclosed form. */
private fun isLinear(tool: DrawTool): Boolean =
    tool == DrawTool.LINE || tool == DrawTool.DASHED_LINE ||
        tool == DrawTool.ARROW || tool == DrawTool.DASHED_ARROW

private fun selectShapeTool(state: BoardState, tool: DrawTool) {
    state.clearSelection()
    state.mode = BoardMode.Draw
    state.tool = tool
}
