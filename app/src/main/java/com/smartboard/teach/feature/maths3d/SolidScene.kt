package com.smartboard.teach.feature.maths3d

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * A 3D Maths scene saved for the whiteboard: the recipe for its solids plus
 * the angle it is seen from — a few hundred bytes, from which the board can
 * draw it at any angle. This is what makes an inserted solid a live object
 * the teacher can turn, rather than a still picture.
 */
@Serializable
data class SolidScene(
    val pieces: List<ScenePiece>,
    val yaw: Float,
    val pitch: Float,
    val pi: PiValue = PiValue.DECIMAL,
)

/**
 * One solid of a scene, flattened from [Piece]. A polygon's name is kept as
 * its string resource NAME, not its id: ids are renumbered between builds,
 * and a lesson saved by one version must still read right in the next.
 */
@Serializable
data class ScenePiece(
    val kind: String,
    val mode: Mode,
    val height: Double,
    val cx: Double = 0.0,
    val cy: Double = 0.0,
    val r: Double = 0.0,
    val w: Double = 0.0,
    val h: Double = 0.0,
    val pts: List<Double> = emptyList(),
    val name: String? = null,
    val regular: Boolean = false,
    val free: Boolean = false,
)

fun Piece.toScene(res: Resources): ScenePiece = when (val s = shape) {
    is Shape2D.Circle -> ScenePiece("circle", mode, height, cx = s.cx, cy = s.cy, r = s.r)
    is Shape2D.Rect -> ScenePiece("rect", mode, height, cx = s.cx, cy = s.cy, w = s.w, h = s.h)
    is Shape2D.Polygon -> ScenePiece(
        "polygon", mode, height,
        pts = s.pts.flatMap { listOf(it.x, it.y) },
        name = res.getResourceEntryName(s.name),
        regular = s.regular,
        free = s.free,
    )
    is Shape2D.Profile -> ScenePiece("profile", mode, height, pts = s.pts.flatMap { listOf(it.x, it.y) })
}

fun ScenePiece.toPiece(res: Resources, packageName: String): Piece? {
    val points = pts.chunked(2).map { P(it[0], it[1]) }
    val shape = when (kind) {
        "circle" -> Shape2D.Circle(cx, cy, r)
        "rect" -> Shape2D.Rect(cx, cy, w, h)
        "polygon" -> {
            val id = name?.let { res.getIdentifier(it, "string", packageName) }?.takeIf { it != 0 }
                ?: com.smartboard.teach.R.string.m3d_shape_free
            Shape2D.Polygon(points, id, regular, free)
        }
        "profile" -> Shape2D.Profile(points)
        else -> return null
    }
    return Piece(shape, mode, height)
}

/** Where scenes live, and the picture of each the board draws. */
object SolidSceneFiles {
    private val json = Json { ignoreUnknownKeys = true }

    /** The rendered view beside a scene file: what the board shows. */
    fun posterPathFor(scenePath: String) = scenePath.substringBeforeLast('.') + "_view.png"

    fun read(path: String): SolidScene? =
        runCatching { json.decodeFromString(SolidScene.serializer(), File(path).readText()) }.getOrNull()

    /** Writes [scene] and its [poster] to [path], or to a new file; returns the scene's path. */
    fun write(context: Context, scene: SolidScene, poster: Bitmap, path: String? = null): String {
        val file = path?.let(::File) ?: File(File(context.filesDir, "solids").apply { mkdirs() }, "${UUID.randomUUID()}.json")
        file.writeText(json.encodeToString(SolidScene.serializer(), scene))
        File(posterPathFor(file.absolutePath)).outputStream().use { poster.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.absolutePath
    }
}

/**
 * Draws [scene] the way 3D Maths shows it — the view's gradient and floor
 * grid, the shaded solids, their dimension labels — with the worked answers
 * for its selected (last) solid in a caption strip beneath, as the old still
 * snapshot had. [width] sets the size; the height follows a fixed aspect, so
 * a re-render at a new angle fits the same board frame exactly.
 */
fun renderScene(scene: SolidScene, res: Resources, packageName: String, width: Int): Bitmap? {
    val pieces = scene.pieces.mapNotNull { it.toPiece(res, packageName) }
    if (pieces.isEmpty()) return null
    val parts = pieces.mapIndexed { i, p -> placedSolid(p.shape, p.mode, p.height, overlays = i == pieces.lastIndex) }
    val solid = mergeSolids(parts)
    val camera = OrbitCamera().apply {
        frame(solid)
        yaw = scene.yaw
        pitch = scene.pitch
    }

    val viewH = (width * VIEW_ASPECT).toInt()
    val textSize = (width / 34f).coerceAtLeast(12f)
    val captionH = (textSize * 3.4f).toInt()
    val out = Bitmap.createBitmap(width, viewH + captionH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val density = width / REFERENCE_WIDTH_DP
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.shader = LinearGradient(0f, 0f, 0f, viewH.toFloat(), ViewTop.toArgb(), ViewBottom.toArgb(), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width.toFloat(), viewH.toFloat(), paint)
    paint.shader = null

    canvas.save()
    canvas.clipRect(0f, 0f, width.toFloat(), viewH.toFloat())
    val view = ViewTransform(camera, Size(width.toFloat(), viewH.toFloat()))
    paint.strokeWidth = density
    for (i in -10..10) {
        paint.color = (if (i == 0) GridMajor else GridMinor).toArgb()
        line(canvas, paint, view, V(i.toFloat(), 0f, -10f), V(i.toFloat(), 0f, 10f))
        line(canvas, paint, view, V(-10f, 0f, i.toFloat()), V(10f, 0f, i.toFloat()))
    }
    SoftwareRenderer().draw(canvas, solid, view, wireframe = false, glass = false, density = density)

    paint.strokeWidth = 1.5f * density
    for (l in solid.lines) {
        paint.color = (if (l.teal) DimTeal else androidx.compose.ui.graphics.Color.White).toArgb()
        for (i in 1 until l.pts.size) line(canvas, paint, view, l.pts[i - 1], l.pts[i])
    }
    for (label in solid.labels) {
        val at = view.project(label.pos) ?: continue
        val color = (if (label.teal) DimTeal else SolidAmber).toArgb()
        val text = label.text.resolve(res)
        paint.textSize = (if (label.small) 11f else 13f) * density
        paint.typeface = Typeface.DEFAULT_BOLD
        val tw = paint.measureText(text)
        val padX = 6 * density
        val padY = 2 * density
        val box = RectF(at.x - tw / 2 - padX, at.y - paint.textSize / 2 - padY * 2, at.x + tw / 2 + padX, at.y + paint.textSize / 2 + padY)
        paint.style = Paint.Style.FILL
        paint.color = LabelFill.toArgb()
        canvas.drawRoundRect(box, 6 * density, 6 * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = color
        canvas.drawRoundRect(box, 6 * density, 6 * density, paint)
        paint.style = Paint.Style.FILL
        canvas.drawText(text, at.x - tw / 2, at.y + paint.textSize * 0.35f, paint)
    }
    canvas.restore()

    // The caption: the selected solid's name and headline answers.
    paint.color = ChromeDarkArgb
    canvas.drawRect(0f, viewH.toFloat(), width.toFloat(), (viewH + captionH).toFloat(), paint)
    val last = pieces.last()
    val sheet = mathsFor(last.shape, last.mode, last.height, scene.pi)
    paint.textSize = textSize
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.color = SolidAmber.toArgb()
    val x = textSize * 0.8f
    canvas.drawText(sheet.name.resolve(res), x, viewH + textSize * 1.3f, paint)
    paint.typeface = Typeface.DEFAULT
    paint.textSize = textSize * 0.8f
    paint.color = DimTeal.toArgb()
    val summary = sheet.summary.joinToString("   ") { (label, answer) ->
        res.getString(com.smartboard.teach.R.string.m3d_caption_item, res.getString(label), answer)
    }
    canvas.drawText(summary, x, viewH + textSize * 2.6f, paint)
    return out
}

private fun line(canvas: Canvas, paint: Paint, view: ViewTransform, a: V, b: V) {
    val pa = view.project(a) ?: return
    val pb = view.project(b) ?: return
    canvas.drawLine(pa.x, pa.y, pb.x, pb.y, paint)
}

/** The 3D view's height as a fraction of its width (the caption adds to it). */
private const val VIEW_ASPECT = 0.62f

/** Label and line sizes are tuned for a view about this wide, in dp. */
private const val REFERENCE_WIDTH_DP = 520f

private val ChromeDarkArgb = com.smartboard.teach.core.ui.theme.ChromeDark.toArgb()
