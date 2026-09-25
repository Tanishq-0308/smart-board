package com.smartboard.teach.feature.maths3d

import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

internal val SolidAmber = Color(0xFFFFB020)
internal val DimTeal = Color(0xFF3DD6B5)
private val EdgeBrown = Color(0xFF3A2400)
private val GridMajor = Color(0xFF4A6680)
private val GridMinor = Color(0xFF2B3D50)
private val ViewTop = Color(0xFF22364A)
private val ViewBottom = Color(0xFF101A24)
private val LabelFill = Color(0xD90F1720)

private const val FOV_DEG = 45f

private val SUN = V(8f, 14f, 10f).normalized()
private val FILL = V(-10f, 4f, -8f).normalized()

/** Orbit camera around [target]. Yaw/pitch in radians. */
class OrbitCamera {
    var yaw by mutableFloatStateOf(0.6f)
    var pitch by mutableFloatStateOf(0.45f)
    var distance by mutableFloatStateOf(14f)
    var target by androidx.compose.runtime.mutableStateOf(V(0f, 2f, 0f))

    val position: V
        get() = target + V(cos(pitch) * sin(yaw), sin(pitch), cos(pitch) * cos(yaw)) * distance

    /** Fit the solid in view from the prototype's three-quarter angle. */
    fun frame(solid: Solid) {
        val t = solid.tris
        if (t.isEmpty()) return
        var lo = V(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        var hi = V(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (i in t.indices step 3) {
            lo = V(min(lo.x, t[i]), min(lo.y, t[i + 1]), min(lo.z, t[i + 2]))
            hi = V(max(hi.x, t[i]), max(hi.y, t[i + 1]), max(hi.z, t[i + 2]))
        }
        val radius = (hi - lo).length() / 2
        target = lo.lerp(hi, 0.5f)
        distance = max(radius / sin(FOV_DEG * PI.toFloat() / 360) * 1.25f, 6f)
        val dir = V(0.62f, 0.5f, 0.9f).normalized()
        yaw = atan2(dir.x, dir.z)
        pitch = asin(dir.y)
    }
}

/**
 * Software 3D view of a [Solid]: flat Lambert shading, triangles sorted back
 * to front (painter's algorithm), overlays drawn on top.
 *
 * Native Compose rather than WebGL: boards ship old or missing WebViews (see
 * NoteDetailScreen), and these solids are a few hundred to ~1,500 triangles.
 *
 * ponytail: painter's sort can misorder intersecting or strongly concave
 * faces (a deep vase from some angles). Move to GLES if that shows up.
 */
@Composable
fun SolidViewport(
    solid: Solid?,
    camera: OrbitCamera,
    autoRotate: Boolean,
    wireframe: Boolean,
    glass: Boolean,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val spin by rememberUpdatedState(autoRotate)
    // three.js autoRotateSpeed 1.5 = one orbit every 40 s.
    LaunchedEffect(camera) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && spin) camera.yaw += (now - last) / 1e9f * (2 * PI.toFloat() / 40f)
                last = now
            }
        }
    }
    val renderer = remember { SoftwareRenderer() }

    Canvas(
        // Grid lines project far past the edges; keep them inside the pane.
        modifier.clipToBounds().pointerInput(camera) {
            detectTransformGestures { _, pan, zoom, _ ->
                camera.yaw -= pan.x * 0.008f
                // Below the floor too, so the base of a solid can be shown. Stops
                // short of straight up/down, where the view's "up" is undefined.
                camera.pitch = (camera.pitch + pan.y * 0.008f).coerceIn(-1.5f, 1.5f)
                camera.distance = (camera.distance / zoom).coerceIn(3f, 80f)
            }
        },
    ) {
        drawRect(Brush.verticalGradient(listOf(ViewTop, ViewBottom)))
        val view = ViewTransform(camera, size)
        drawGrid(view)
        if (solid == null) return@Canvas
        drawIntoCanvas { renderer.draw(it.nativeCanvas, solid, view, wireframe, glass, density) }
        if (!showLabels) return@Canvas
        for (line in solid.lines) {
            val pts = line.pts.mapNotNull { view.project(it) }
            for (i in 1 until pts.size) drawLine(if (line.teal) DimTeal else Color.White, pts[i - 1], pts[i], 1.5f * density)
        }
        for (label in solid.labels) {
            val at = view.project(label.pos) ?: continue
            val color = if (label.teal) DimTeal else SolidAmber
            val layout = measurer.measure(
                label.text,
                TextStyle(color = color, fontSize = if (label.small) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold),
            )
            val pad = Offset(6 * density, 1 * density)
            val box = Size(layout.size.width + pad.x * 2, layout.size.height + pad.y * 2)
            val topLeft = at - Offset(box.width / 2, box.height / 2)
            drawRoundRect(LabelFill, topLeft, box, CornerRadius(6 * density))
            drawRoundRect(color, topLeft, box, CornerRadius(6 * density), style = androidx.compose.ui.graphics.drawscope.Stroke(density))
            drawText(layout, topLeft = topLeft + pad)
        }
    }
}

/** World → screen for one frame. */
internal class ViewTransform(camera: OrbitCamera, size: Size) {
    val eye = camera.position
    private val fwd = (camera.target - eye).normalized()
    private val right = (fwd cross V(0f, 1f, 0f)).normalized()
    private val up = right cross fwd
    private val cx = size.width / 2
    private val cy = size.height / 2
    private val focal = cy / tan(FOV_DEG * PI.toFloat() / 360)

    /** Depth along the view direction; larger is farther. */
    fun depth(p: V) = (p - eye) dot fwd

    fun project(p: V): Offset? {
        val d = p - eye
        val z = d dot fwd
        if (z < 0.05f) return null
        return Offset(cx + (d dot right) / z * focal, cy - (d dot up) / z * focal)
    }
}

private fun DrawScope.drawGrid(view: ViewTransform) {
    // GridHelper(20, 20): 1 cm cells from -10 to 10 on the floor.
    for (i in -10..10) {
        val color = if (i == 0) GridMajor else GridMinor
        val a1 = view.project(V(i.toFloat(), 0f, -10f)); val b1 = view.project(V(i.toFloat(), 0f, 10f))
        if (a1 != null && b1 != null) drawLine(color, a1, b1, density)
        val a2 = view.project(V(-10f, 0f, i.toFloat())); val b2 = view.project(V(10f, 0f, i.toFloat()))
        if (a2 != null && b2 != null) drawLine(color, a2, b2, density)
    }
}

/** Buffers reused across frames so a spinning solid does not churn the GC. */
private class SoftwareRenderer {
    private var order = IntArray(0)
    private var depths = FloatArray(0)
    private var shades = IntArray(0)
    private var xy = FloatArray(0)
    private var colors = IntArray(0)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun draw(canvas: NativeCanvas, solid: Solid, view: ViewTransform, wireframe: Boolean, glass: Boolean, density: Float) {
        val t = solid.tris
        val n = solid.triangleCount
        if (order.size < n) {
            order = IntArray(n); depths = FloatArray(n); shades = IntArray(n)
            xy = FloatArray(n * 6); colors = IntArray(n * 3)
        }
        val base = SolidAmber
        val alpha = if (glass) 0.45f else 1f
        var visible = 0
        for (i in 0 until n) {
            val o = i * 9
            val a = V(t[o], t[o + 1], t[o + 2]); val b = V(t[o + 3], t[o + 4], t[o + 5]); val c = V(t[o + 6], t[o + 7], t[o + 8])
            val pa = view.project(a) ?: continue
            val pb = view.project(b) ?: continue
            val pc = view.project(c) ?: continue
            var nrm = ((b - a) cross (c - a)).normalized()
            val mid = (a + b + c) * (1f / 3)
            // Double-sided: light the face that is turned toward the camera.
            if (nrm dot (view.eye - mid) < 0) nrm *= -1f
            // Hemisphere sky/ground + key sun + cool fill, as in the prototype's lights.
            // Ground bounce kept high enough that a base seen from below
            // still reads as amber, not a black hole.
            val hemi = 0.40f + 0.10f * nrm.y
            val light = hemi + 0.62f * max(0f, nrm dot SUN) + 0.16f * max(0f, nrm dot FILL)
            shades[visible] = Color(
                (base.red * light).coerceAtMost(1f), (base.green * light).coerceAtMost(1f),
                (base.blue * light).coerceAtMost(1f), alpha,
            ).toArgb()
            depths[visible] = view.depth(mid)
            order[visible] = visible
            val x = visible * 6
            xy[x] = pa.x; xy[x + 1] = pa.y; xy[x + 2] = pb.x; xy[x + 3] = pb.y; xy[x + 4] = pc.x; xy[x + 5] = pc.y
            visible++
        }

        if (wireframe) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density
            paint.color = base.copy(alpha = 0.9f).toArgb()
            for (i in 0 until visible) {
                val x = i * 6
                path.reset()
                path.moveTo(xy[x], xy[x + 1]); path.lineTo(xy[x + 2], xy[x + 3]); path.lineTo(xy[x + 4], xy[x + 5]); path.close()
                canvas.drawPath(path, paint)
            }
            return
        }

        // Back to front.
        val sorted = order.copyOf(visible).sortedByDescending { depths[it] }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // One batched call. drawVertices is only hardware-accelerated from API 29.
            val verts = FloatArray(visible * 6)
            for ((k, i) in sorted.withIndex()) {
                System.arraycopy(xy, i * 6, verts, k * 6, 6)
                colors[k * 3] = shades[i]; colors[k * 3 + 1] = shades[i]; colors[k * 3 + 2] = shades[i]
            }
            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.WHITE
            canvas.drawVertices(
                NativeCanvas.VertexMode.TRIANGLES, verts.size, verts, 0,
                null, 0, colors, 0, null, 0, 0, paint,
            )
        } else {
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = 0.5f // closes hairline seams between neighbours
            for (i in sorted) {
                val x = i * 6
                path.reset()
                path.moveTo(xy[x], xy[x + 1]); path.lineTo(xy[x + 2], xy[x + 3]); path.lineTo(xy[x + 4], xy[x + 5]); path.close()
                paint.color = shades[i]
                canvas.drawPath(path, paint)
            }
        }

        // Crease edges, hidden when both faces meeting there are turned away.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = EdgeBrown.toArgb()
        for (e in solid.edges) {
            val toEye = view.eye - e.a
            if (!glass && (e.n1 dot toEye) <= 0 && (e.n2 dot toEye) <= 0) continue
            val a = view.project(e.a) ?: continue
            val b = view.project(e.b) ?: continue
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        }
    }
}
