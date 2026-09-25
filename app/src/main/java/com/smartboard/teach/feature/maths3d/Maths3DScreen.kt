package com.smartboard.teach.feature.maths3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.component.chromeInset
import com.smartboard.teach.core.ui.theme.ChromeBorder
import com.smartboard.teach.core.ui.theme.ChromeDark
import com.smartboard.teach.core.ui.theme.ChromeDarkElevated
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.pow

private val Chalkboard = Color(0xFF1F3A2E)
private val Chalk = Color(0xFFF4F1E8)
private val ChalkYellow = Color(0xFFFFD27A)
private val EaseOutCubic = Easing { 1 - (1 - it).pow(3) }

/**
 * 3D Maths: draw a 2D shape on the chalk grid, watch it become a solid, read
 * the worked formulas. Port of the HTML prototype (smart-board 3D modelling).
 *
 * @param onInsert called with a PNG path when the teacher sends a snapshot of
 *   the solid to the whiteboard
 */
@Composable
fun Maths3DScreen(
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: Maths3DViewModel = viewModel(),
) {
    val dimens = SmartBoardTheme.dimens
    val shape = viewModel.shape
    val mode = viewModel.mode
    val h = viewModel.height

    val camera = remember { OrbitCamera() }
    var resetKey by remember { mutableIntStateOf(0) }
    var autoRotate by remember { mutableStateOf(true) }
    var wireframe by remember { mutableStateOf(false) }
    var glass by remember { mutableStateOf(false) }
    var labels by remember { mutableStateOf(true) }

    val grow = remember { Animatable(1f) }
    LaunchedEffect(viewModel.growKey) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(1600, easing = EaseOutCubic))
    }
    val others = viewModel.others
    // The rest of the scene: fully grown, no dimension clutter.
    val otherSolids = remember(others) {
        others.map { placedSolid(it.shape, it.mode, it.height, overlays = false) }
    }
    LaunchedEffect(shape, mode, resetKey, others) {
        val all = otherSolids + listOfNotNull(
            if (shape != null && mode != null) placedSolid(shape, mode, h) else null,
        )
        if (all.isNotEmpty()) camera.frame(mergeSolids(all))
    }

    val solid = remember(shape, mode, h, grow.value, otherSolids) {
        val selected = if (shape != null && mode != null) placedSolid(shape, mode, h, grow.value.toDouble()) else null
        val all = otherSolids + listOfNotNull(selected)
        if (all.isEmpty()) null else mergeSolids(all)
    }
    val sheet = remember(shape, mode, h, viewModel.pi) {
        if (shape != null && mode != null) mathsFor(shape, mode, h, viewModel.pi) else null
    }

    val layer = rememberGraphicsLayer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inserting by remember { mutableStateOf(false) }

    val boardPane: @Composable (Modifier) -> Unit = { m ->
        Pane("1. Draw a shape", m, actions = {
            Pill("Multi", selected = viewModel.multi) { viewModel.toggleMulti() }
            Pill("↶ Undo", enabled = viewModel.canUndo) { viewModel.undo() }
            Pill("Clear", enabled = shape != null) { viewModel.clear() }
        }) {
            ChalkBoard(shape, mode, others, viewModel::onStroke, Modifier.weight(1f).fillMaxWidth())
            Text(
                viewModel.message, color = TextOnChromeMuted, fontSize = dimens.labelSize,
                modifier = Modifier.padding(dimens.gutterSmall),
            )
        }
    }

    val viewPane: @Composable (Modifier) -> Unit = { m ->
        Pane("2. Turn it in 3D", m, actions = {
            Pill("Auto rotate", selected = autoRotate) { autoRotate = !autoRotate }
            Pill("Wireframe", selected = wireframe) { wireframe = !wireframe }
            Pill("Transparent", selected = glass) { glass = !glass }
            Pill("Labels", selected = labels) { labels = !labels }
            Pill("Reset view") { resetKey++ }
        }) {
            SolidViewport(
                solid, camera, autoRotate, wireframe, glass, labels,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            )
            Controls(
                shape = shape, mode = mode, h = h,
                inserting = inserting,
                onMode = viewModel::selectMode,
                onHeight = viewModel::changeHeight,
                onGrow = viewModel::grow,
                onInsert = {
                    if (sheet == null || inserting) return@Controls
                    inserting = true
                    scope.launch {
                        try {
                            val image = layer.toImageBitmap().asAndroidBitmap()
                            val file = withContext(Dispatchers.IO) { saveSnapshot(context, image, sheet) }
                            onInsert(file.absolutePath)
                        } finally {
                            inserting = false
                        }
                    }
                },
            )
        }
    }

    val mathsPane: @Composable (Modifier) -> Unit = { m ->
        Pane("3. Maths", m) {
            MathsSheet(sheet, viewModel.pi, viewModel::togglePi, Modifier.weight(1f).fillMaxWidth())
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(ChromeDark)
            .padding(dimens.gutterSmall)
            // Everything, not just the top bar, sits clear of the menu button.
            .chromeInset(),
        verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        PresetBar(viewModel::preset, Modifier)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val gap = dimens.gutterSmall
            if (maxWidth >= 1000.dp) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    boardPane(Modifier.weight(1f).fillMaxHeight())
                    viewPane(Modifier.weight(1.25f).fillMaxHeight())
                    mathsPane(Modifier.width(280.dp * dimens.scale).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(Modifier.weight(1.4f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        boardPane(Modifier.weight(1f).fillMaxHeight())
                        viewPane(Modifier.weight(1f).fillMaxHeight())
                    }
                    mathsPane(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

// --- chrome ---------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetBar(onPreset: (Preset) -> Unit, modifier: Modifier) {
    val dimens = SmartBoardTheme.dimens
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text("3D Maths", color = SolidAmber, fontSize = dimens.titleSize, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(dimens.gutter))
        Text("Quick start:", color = TextOnChromeMuted, fontSize = dimens.labelSize)
        PRESETS.forEach { p -> Pill(p.label) { onPreset(p) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pane(
    title: String,
    modifier: Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    FloatingIsland(modifier, contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxSize()) {
            FlowRow(
                Modifier.fillMaxWidth().padding(dimens.gutterSmall),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = TextOnChrome, fontSize = dimens.bodySize, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                actions()
            }
            content()
        }
    }
}

/** Compact toggle/button. Selected pills turn teal, as in the prototype. */
@Composable
private fun Pill(
    text: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    val shape = RoundedCornerShape(8.dp)
    val bg = when {
        accent -> SolidAmber
        selected -> DimTeal
        else -> ChromeDarkElevated
    }
    Text(
        text,
        color = if (accent || selected) Color(0xFF10181F) else TextOnChrome,
        fontSize = dimens.labelSize,
        fontWeight = if (accent || selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (accent || selected) bg else ChromeBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    shape: Shape2D?,
    mode: Mode?,
    h: Double,
    inserting: Boolean,
    onMode: (Mode) -> Unit,
    onHeight: (Double) -> Unit,
    onGrow: () -> Unit,
    onInsert: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Column(Modifier.fillMaxWidth().padding(dimens.gutterSmall), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            shape?.let { s ->
                modesFor(s).forEach { opt ->
                    Pill(opt.label, selected = opt.mode == mode, enabled = opt.enabled) { onMode(opt.mode) }
                }
            }
            Spacer(Modifier.weight(1f))
            Pill("▶ Grow", accent = true, enabled = shape != null, onClick = onGrow)
            Pill(if (inserting) "Inserting…" else "Insert on board", enabled = shape != null && !inserting, onClick = onInsert)
        }
        val needsHeight = shape != null && mode != Mode.SPHERE && mode != Mode.REVOLVE
        Row(Modifier.fillMaxWidth().alpha(if (needsHeight) 1f else 0f), verticalAlignment = Alignment.CenterVertically) {
            Text("Height (h):", color = TextOnChromeMuted, fontSize = dimens.labelSize)
            Slider(
                value = h.toFloat(),
                onValueChange = { onHeight(snap(it.toDouble())) },
                valueRange = 0.5f..10f,
                steps = 18,
                enabled = needsHeight,
                colors = SliderDefaults.colors(thumbColor = SolidAmber, activeTrackColor = SolidAmber),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(fmt(h), color = TextOnChrome, fontSize = dimens.labelSize, modifier = Modifier.width(36.dp))
        }
    }
}

// --- maths panel ----------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MathsSheet(sheet: Sheet?, pi: PiValue, onTogglePi: () -> Unit, modifier: Modifier) {
    val dimens = SmartBoardTheme.dimens
    val formulaFont = FontFamily.Serif
    if (sheet == null) {
        Text(
            "Draw a shape or pick a preset above. Its name, formulas and worked answers appear here.",
            color = TextOnChromeMuted, fontSize = dimens.bodySize,
            modifier = modifier.padding(dimens.gutter),
        )
        return
    }
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(dimens.gutterSmall),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(sheet.name, color = SolidAmber, fontSize = dimens.headlineSize, fontWeight = FontWeight.SemiBold)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sheet.given.forEach { Pill(it) {} }
                if (sheet.usesPi) Pill("π = ${pi.text} ⇄", onClick = onTogglePi)
            }
        }
        items(sheet.cards) { card ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChromeDarkElevated)
                    .border(1.dp, ChromeBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(card.title.uppercase(), color = TextOnChromeMuted, fontSize = 12.sp, letterSpacing = 0.6.sp)
                Text(card.formula, color = TextOnChrome, fontSize = 18.sp, fontFamily = formulaFont)
                if (card.steps.isNotEmpty()) Text(card.steps, color = TextOnChromeMuted, fontSize = 15.sp, fontFamily = formulaFont)
                Text(card.answer, color = DimTeal, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        sheet.euler?.let { e ->
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChromeDarkElevated)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("EULER'S FORMULA  F + V − E = 2", color = TextOnChromeMuted, fontSize = 12.sp, letterSpacing = 0.6.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(e.f to "Faces", e.v to "Vertices", e.e to "Edges").forEach { (n, label) ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(ChromeDark).padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("$n", color = SolidAmber, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text(label, color = TextOnChrome, fontSize = 13.sp)
                            }
                        }
                    }
                    Text("${e.f} + ${e.v} − ${e.e} = ${e.f + e.v - e.e} ✓", color = TextOnChromeMuted, fontSize = 15.sp, fontFamily = formulaFont)
                }
            }
        }
        sheet.note?.let { item { Text(it, color = TextOnChromeMuted, fontSize = 13.sp) } }
    }
}

// --- chalk board ----------------------------------------------------------------

/** Board pixels per cm: 14 boxes across the short side, 20–40 dp each. */
private fun unitPx(w: Float, h: Float, density: Float) = (minOf(w, h) / 14f).coerceIn(20 * density, 40 * density)

@Composable
private fun ChalkBoard(
    shape: Shape2D?,
    mode: Mode?,
    others: List<Piece>,
    onStroke: (List<P>) -> Unit,
    modifier: Modifier,
) {
    val stroke = remember { mutableStateListOf<P>() }
    val measurer = rememberTextMeasurer(cacheSize = 32)
    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .background(Chalkboard)
                .pointerInput(Unit) {
                    fun toU(o: Offset): P {
                        val u = unitPx(size.width.toFloat(), size.height.toFloat(), density)
                        return P(((o.x - size.width / 2f) / u).toDouble(), ((o.y - size.height / 2f) / u).toDouble())
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        stroke.clear()
                        stroke += toU(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.historical.forEach { stroke += toU(it.position) }
                            stroke += toU(change.position)
                            change.consume()
                            if (!change.pressed) break
                        }
                        val points = stroke.toList()
                        stroke.clear()
                        onStroke(points)
                    }
                },
        ) {
            val u = unitPx(size.width, size.height, density)
            fun px(p: P) = Offset(size.width / 2 + p.x.toFloat() * u, size.height / 2 + p.y.toFloat() * u)
            drawBoardGrid(u)
            // Revolve axis
            drawLine(
                DimTeal.copy(alpha = 0.55f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height),
                1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8 * density, 6 * density)),
            )
            chalkText(measurer, "axis ↻", Offset(size.width / 2 + 6 * density, 6 * density), DimTeal.copy(alpha = 0.8f), centre = false)
            chalkText(measurer, "1 box = 1 cm", Offset(size.width - 90 * density, 6 * density), Chalk.copy(alpha = 0.5f), centre = false)

            // Unselected shapes faint and unlabelled, under the selected one.
            others.forEach { drawShape(it.shape, it.mode, measurer, ::px, selected = false) }
            if (shape != null) drawShape(shape, mode, measurer, ::px)

            if (stroke.size > 1) {
                val path = Path().apply {
                    moveTo(px(stroke[0]).x, px(stroke[0]).y)
                    for (i in 1 until stroke.size) px(stroke[i]).let { lineTo(it.x, it.y) }
                }
                drawPath(path, Chalk.copy(alpha = 0.9f), style = Stroke(3 * density, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
        if (shape == null && stroke.isEmpty()) {
            Text(
                "Draw a closed shape (circle, square, triangle…) → prism / cylinder\n" +
                    "Draw an open line right of the axis → it turns into a vase or bowl",
                color = Chalk.copy(alpha = 0.75f), fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        }
    }
}

private fun DrawScope.drawBoardGrid(u: Float) {
    val cols = (size.width / u / 2).toInt() + 1
    val rows = (size.height / u / 2).toInt() + 1
    for (i in -cols..cols) {
        val x = size.width / 2 + i * u
        drawLine(Color.White.copy(alpha = if (i % 5 == 0) 0.14f else 0.06f), Offset(x, 0f), Offset(x, size.height))
    }
    for (j in -rows..rows) {
        val y = size.height / 2 + j * u
        drawLine(Color.White.copy(alpha = if (j % 5 == 0) 0.14f else 0.06f), Offset(0f, y), Offset(size.width, y))
    }
}

private fun DrawScope.chalkText(m: TextMeasurer, text: String, at: Offset, color: Color = ChalkYellow, centre: Boolean = true) {
    val layout = m.measure(
        text,
        TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, shadow = Shadow(Chalkboard, blurRadius = 6f)),
    )
    val topLeft = if (centre) at - Offset(layout.size.width / 2f, layout.size.height / 2f) else at
    drawText(layout, topLeft = topLeft)
}

private fun DrawScope.drawShape(
    shape: Shape2D,
    mode: Mode?,
    m: TextMeasurer,
    px: (P) -> Offset,
    selected: Boolean = true,
) {
    fun pathOf(pts: List<P>, closed: Boolean) = Path().apply {
        pts.forEachIndexed { i, p -> px(p).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        if (closed) close()
    }
    val lineWidth = 3 * density
    val pts = outline(shape)
    val closed = shape !is Shape2D.Profile

    // Mirror ghost across the axis when revolving.
    if (!selected) {
        val path = pathOf(pts, closed)
        if (closed) drawPath(path, SolidAmber.copy(alpha = 0.07f))
        drawPath(path, Chalk.copy(alpha = 0.45f), style = Stroke(2 * density))
        return
    }
    if (mode == Mode.REVOLVE) {
        drawPath(
            pathOf(pts.map { P(-it.x, it.y) }, closed), DimTeal.copy(alpha = 0.6f),
            style = Stroke(2 * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5 * density, 6 * density))),
        )
    }
    val path = pathOf(pts, closed)
    if (closed) drawPath(path, SolidAmber.copy(alpha = 0.14f))
    drawPath(path, Chalk, style = Stroke(lineWidth, join = androidx.compose.ui.graphics.StrokeJoin.Round))

    when (shape) {
        is Shape2D.Profile -> Unit
        is Shape2D.Circle -> {
            val c = px(P(shape.cx, shape.cy)); val e = px(P(shape.cx + shape.r, shape.cy))
            drawLine(ChalkYellow, c, e, 2 * density)
            drawCircle(ChalkYellow, 3.5f * density, c)
            chalkText(m, "r = ${fmt(shape.r)}", Offset((c.x + e.x) / 2, c.y - 12 * density))
        }
        is Shape2D.Rect -> {
            val tl = px(pts[0]); val br = px(pts[2])
            chalkText(m, "l = ${fmt(shape.w)}", Offset((tl.x + br.x) / 2, br.y + 14 * density))
            chalkText(m, "b = ${fmt(shape.h)}", Offset(br.x + 8 * density, (tl.y + br.y) / 2 - 8 * density), centre = false)
        }
        is Shape2D.Polygon -> {
            // Label each side; skip busy free shapes.
            if (shape.free && pts.size > 8) return
            val c = centroid(pts)
            pts.forEachIndexed { i, p ->
                val q = pts[(i + 1) % pts.size]
                val mx = (p.x + q.x) / 2; val my = (p.y + q.y) / 2
                val len = hypot(mx - c.x, my - c.y).takeIf { it > 0 } ?: 1.0
                chalkText(m, fmt(dist(p, q)), px(P(mx + (mx - c.x) / len * 0.55, my + (my - c.y) / len * 0.55)))
            }
        }
    }
}

// --- snapshot -------------------------------------------------------------------

/**
 * The 3D view plus a caption strip (solid name and headline answers), saved
 * as a PNG for the whiteboard to import. Lives in cache: the board's importer
 * copies it into its own media store.
 */
private fun saveSnapshot(context: Context, view: Bitmap, sheet: Sheet): File {
    // A hardware bitmap cannot be drawn into a software canvas; copy first.
    val src = view.copy(Bitmap.Config.ARGB_8888, false)
    val w = src.width
    val textSize = (w / 34f).coerceAtLeast(18f)
    val captionH = (textSize * 3.4f).toInt()
    val out = Bitmap.createBitmap(w, src.height + captionH, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(ChromeDark.toArgb())
    canvas.drawBitmap(src, 0f, 0f, null)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        typeface = Typeface.DEFAULT_BOLD
        color = SolidAmber.toArgb()
    }
    val x = textSize * 0.8f
    canvas.drawText(sheet.name, x, src.height + textSize * 1.3f, paint)
    paint.typeface = Typeface.DEFAULT
    paint.textSize = textSize * 0.8f
    paint.color = DimTeal.toArgb()
    canvas.drawText(sheet.summary, x, src.height + textSize * 2.6f, paint)
    src.recycle()

    val dir = File(context.cacheDir, "maths3d").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.png")
    file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    out.recycle()
    return file
}
