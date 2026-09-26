package com.smartboard.teach.feature.whiteboard.games

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The four classroom games a teacher can put on the board. */
enum class Game { NAMES, SPINNER, DICE, SCORES }

@Composable
fun GamePanel(game: Game, onClose: () -> Unit, modifier: Modifier = Modifier) {
    when (game) {
        Game.NAMES -> NamePickerPanel(onClose, modifier)
        Game.SPINNER -> SpinnerPanel(onClose, modifier)
        Game.DICE -> DicePanel(onClose, modifier)
        Game.SCORES -> ScoreboardPanel(onClose, modifier)
    }
}

/**
 * Movable frame shared by every game, like the Timer: dragged by its own
 * chrome only, so it never becomes a layer that steals the canvas's input.
 */
@Composable
private fun GameFrame(title: String, onClose: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offsetX += drag.x
                    offsetY += drag.y
                }
            },
    ) {
        FloatingIsland(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            Column(Modifier.width(PANEL_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = TextOnChrome, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.game_close), tint = TextOnChrome, modifier = Modifier.size(18.dp))
                    }
                }
                content()
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, enabled: Boolean = true, filled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        color = if (enabled) Color.White else TextOnChromeMuted,
        fontSize = 15.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled && enabled) Accent else Color.White.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun InputBox(value: String, onValueChange: (String) -> Unit, hint: String, singleLine: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(10.dp),
    ) {
        if (value.isEmpty()) Text(hint, color = TextOnChromeMuted, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = TextOnChrome, fontSize = 14.sp),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
        )
    }
}

// --- Random name picker -----------------------------------------------------

@Composable
private fun NamePickerPanel(onClose: () -> Unit, modifier: Modifier, viewModel: GamesViewModel = hiltViewModel()) {
    val classes by viewModel.classes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var namesText by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var avoidRepeats by remember { mutableStateOf(true) }
    var rolling by remember { mutableStateOf(false) }
    val names = GameLogic.parseNames(namesText)

    GameFrame(stringResource(R.string.game_names), onClose, modifier) {
        Text(
            shown ?: stringResource(R.string.game_names_empty_result),
            color = if (rolling) TextOnChromeMuted else TextOnChrome,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        )
        ActionChip(stringResource(R.string.game_names_pick), enabled = names.isNotEmpty() && !rolling) {
            val (pick, next) = GameLogic.pickName(names, picked, avoidRepeats) ?: return@ActionChip
            scope.launch {
                // A short shuffle builds suspense; the pick was already made.
                rolling = true
                repeat(SHUFFLE_FRAMES) { shown = names.random(); delay(SHUFFLE_FRAME_MS) }
                shown = pick
                picked = next
                rolling = false
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = avoidRepeats,
                onCheckedChange = { avoidRepeats = it },
                colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = TextOnChromeMuted),
            )
            Text(stringResource(R.string.game_names_no_repeat), color = TextOnChrome, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            if (avoidRepeats && names.isNotEmpty()) {
                Text(
                    stringResource(R.string.game_names_progress, picked.count { it in names }, names.distinct().size),
                    color = TextOnChromeMuted,
                    fontSize = 12.sp,
                )
            }
        }
        if (classes.isNotEmpty()) {
            Text(
                stringResource(R.string.game_names_load_class),
                color = TextOnChromeMuted,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                classes.forEach { schoolClass ->
                    ActionChip(schoolClass.displayName, filled = false) {
                        scope.launch {
                            namesText = viewModel.namesIn(schoolClass.id).joinToString("\n")
                            picked = emptySet()
                            shown = null
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        InputBox(namesText, { namesText = it; picked = picked.filter { n -> n in GameLogic.parseNames(it) }.toSet() }, stringResource(R.string.game_names_hint))
    }
}

// --- Spinner -----------------------------------------------------------------

@Composable
private fun SpinnerPanel(onClose: () -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var segmentsText by remember { mutableStateOf("1, 2, 3, 4, 5, 6") }
    val segments = GameLogic.parseNames(segmentsText).take(MAX_SEGMENTS)
    val rotation = remember { Animatable(0f) }
    var result by remember { mutableStateOf<String?>(null) }
    var spinning by remember { mutableStateOf(false) }

    fun spin() {
        if (segments.size < 2 || spinning) return
        val (winner, target) = GameLogic.spinTarget(segments.size, rotation.value)
        scope.launch {
            spinning = true
            result = null
            rotation.animateTo(target, tween(SPIN_MS, easing = FastOutSlowInEasing))
            result = segments.getOrNull(winner)
            spinning = false
        }
    }

    GameFrame(stringResource(R.string.game_spinner), onClose, modifier) {
        val labelPaint = remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textAlign = Paint.Align.CENTER }
        }
        Canvas(Modifier.size(WHEEL_SIZE).clickable(enabled = !spinning) { spin() }) {
            val radius = min(size.width, size.height) / 2f - 6f
            val centre = Offset(size.width / 2f, size.height / 2f)
            val n = segments.size.coerceAtLeast(1)
            val sweep = 360f / n
            rotate(rotation.value, centre) {
                for (i in 0 until n) {
                    // Segment i starts at the top (-90°) and runs clockwise.
                    drawArc(
                        color = WHEEL_COLOURS[i % WHEEL_COLOURS.size],
                        startAngle = -90f + i * sweep,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(centre.x - radius, centre.y - radius),
                        size = Size(radius * 2, radius * 2),
                    )
                    val mid = Math.toRadians((-90f + (i + 0.5f) * sweep).toDouble())
                    labelPaint.textSize = (radius / 6f).coerceAtMost(22.sp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(
                        segments.getOrElse(i) { "" }.take(12),
                        centre.x + (radius * 0.62f * cos(mid)).toFloat(),
                        centre.y + (radius * 0.62f * sin(mid)).toFloat() + labelPaint.textSize / 3f,
                        labelPaint,
                    )
                }
            }
            drawCircle(Color.White, radius = radius * 0.1f, center = centre)
            // Fixed pointer at the top.
            val pointer = Path().apply {
                moveTo(centre.x - 14f, 0f)
                lineTo(centre.x + 14f, 0f)
                lineTo(centre.x, 26f)
                close()
            }
            drawPath(pointer, Color.White)
        }
        Text(
            result ?: " ",
            color = TextOnChrome,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        ActionChip(stringResource(R.string.game_spin), enabled = segments.size >= 2 && !spinning) { spin() }
        Spacer(Modifier.height(10.dp))
        InputBox(segmentsText, { segmentsText = it; result = null }, stringResource(R.string.game_spinner_hint))
    }
}

// --- Dice --------------------------------------------------------------------

@Composable
private fun DicePanel(onClose: () -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var count by remember { mutableIntStateOf(2) }
    var faces by remember { mutableStateOf(List(2) { 1 }) }
    var rolling by remember { mutableStateOf(false) }

    GameFrame(stringResource(R.string.game_dice), onClose, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 12.dp)) {
            faces.forEach { Die(it) }
        }
        Text(
            stringResource(R.string.game_dice_total, faces.sum()),
            color = if (rolling) TextOnChromeMuted else TextOnChrome,
            fontSize = 20.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { if (count > 1) { count--; faces = faces.take(count) } }, enabled = !rolling) {
                Icon(Icons.Filled.Remove, stringResource(R.string.game_dice_fewer), tint = TextOnChrome)
            }
            ActionChip(stringResource(R.string.game_roll), enabled = !rolling) {
                scope.launch {
                    rolling = true
                    repeat(SHUFFLE_FRAMES) { faces = GameLogic.rollDice(count); delay(SHUFFLE_FRAME_MS) }
                    faces = GameLogic.rollDice(count)
                    rolling = false
                }
            }
            IconButton(onClick = { if (count < MAX_DICE) { count++; faces = faces + 1 } }, enabled = !rolling) {
                Icon(Icons.Filled.Add, stringResource(R.string.game_dice_more), tint = TextOnChrome)
            }
        }
    }
}

@Composable
private fun Die(value: Int) {
    Canvas(Modifier.size(72.dp)) {
        drawRoundRect(Color.White, cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f))
        val pip = size.minDimension * 0.09f
        val (l, c, r) = listOf(0.25f, 0.5f, 0.75f)
        val spots = when (value) {
            1 -> listOf(c to c)
            2 -> listOf(l to l, r to r)
            3 -> listOf(l to l, c to c, r to r)
            4 -> listOf(l to l, r to l, l to r, r to r)
            5 -> listOf(l to l, r to l, c to c, l to r, r to r)
            else -> listOf(l to l, r to l, l to c, r to c, l to r, r to r)
        }
        spots.forEach { (x, y) -> drawCircle(Color(0xFF1C2530), pip, Offset(size.width * x, size.height * y)) }
    }
}

// --- Team scoreboard ---------------------------------------------------------

@Composable
private fun ScoreboardPanel(onClose: () -> Unit, modifier: Modifier) {
    val teamA = stringResource(R.string.game_team_default, "A")
    val teamB = stringResource(R.string.game_team_default, "B")
    var teams by remember { mutableStateOf(listOf(Team(teamA), Team(teamB))) }
    val nextName = stringResource(R.string.game_team_default, ('A' + teams.size).toString())

    GameFrame(stringResource(R.string.game_scores), onClose, modifier) {
        teams.forEachIndexed { index, team ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(WHEEL_COLOURS[index % WHEEL_COLOURS.size]))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = team.name,
                    onValueChange = { name -> teams = teams.toMutableList().also { it[index] = team.copy(name = name) } },
                    singleLine = true,
                    textStyle = TextStyle(color = TextOnChrome, fontSize = 16.sp),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { teams = teams.toMutableList().also { it[index] = team.copy(score = team.score - 1) } }) {
                    Icon(Icons.Filled.Remove, stringResource(R.string.game_score_down), tint = TextOnChrome)
                }
                Text(
                    team.score.toString(),
                    color = TextOnChrome,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(56.dp),
                )
                IconButton(onClick = { teams = teams.toMutableList().also { it[index] = team.copy(score = team.score + 1) } }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.game_score_up), tint = TextOnChrome)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip(stringResource(R.string.game_team_add), enabled = teams.size < MAX_TEAMS, filled = false) {
                teams = teams + Team(nextName)
            }
            ActionChip(stringResource(R.string.game_team_remove), enabled = teams.size > 2, filled = false) {
                teams = teams.dropLast(1)
            }
            ActionChip(stringResource(R.string.game_scores_reset), filled = false) {
                teams = teams.map { it.copy(score = 0) }
            }
        }
    }
}

private val PANEL_WIDTH = 340.dp
private val WHEEL_SIZE = 260.dp
private const val MAX_SEGMENTS = 12
private const val MAX_DICE = 3
private const val MAX_TEAMS = 6
private const val SPIN_MS = 3_500
private const val SHUFFLE_FRAMES = 12
private const val SHUFFLE_FRAME_MS = 70L

private val WHEEL_COLOURS = listOf(
    Color(0xFF2F6FED), Color(0xFFE5484D), Color(0xFF2E9E5B), Color(0xFFE8A33D),
    Color(0xFF8E4EC6), Color(0xFF12A5B8), Color(0xFFD6409F), Color(0xFF6E7B8B),
)
