package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.ChromeBorder
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted

/** The largest table the picker offers. */
const val TABLE_PICKER_MAX = 10

/**
 * One square in the grid, and the gap between them.
 *
 * Ten columns at this size keep the whole picker inside the board's left
 * gutter: the tray's popovers are anchored to the toolbar's left edge, so a
 * wider grid runs off the side of the screen rather than growing rightward.
 */
private val SWATCH = 16.dp
private val GAP = 2.dp

/**
 * The familiar drag-a-grid table sizer: point at 4x3, get a 4x3 table.
 *
 * Replaces dropping a fixed 2x2 and making the teacher grow it by hand. The
 * grid both PREVIEWS and picks — squares up to the pointer light up and the
 * caption reads the size back — so the table that lands is the one that was
 * shown, rather than a guess a teacher then has to correct mid-lesson.
 *
 * Works by tap or by drag: a finger dragged across the grid updates the
 * highlight and commits where it lifts, which is how this control behaves in
 * every office suite a teacher has already used.
 */
@Composable
fun TableSizePicker(
    onPick: (rows: Int, cols: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 0 means "nothing pointed at yet", so the caption can invite a choice
    // rather than claiming a 1x1 table is selected.
    var rows by remember { mutableIntStateOf(0) }
    var cols by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val stepPx = with(density) { (SWATCH + GAP).toPx() }

    /** Which cell a local pointer position falls on, clamped to the grid. */
    fun cellAt(position: Offset): Pair<Int, Int> {
        val col = (position.x / stepPx).toInt() + 1
        val row = (position.y / stepPx).toInt() + 1
        return row.coerceIn(1, TABLE_PICKER_MAX) to col.coerceIn(1, TABLE_PICKER_MAX)
    }

    FloatingIsland(modifier = modifier, contentPadding = PaddingValues(10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                verticalArrangement = Arrangement.spacedBy(GAP),
                modifier = Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val (r, c) = cellAt(offset)
                            rows = r
                            cols = c
                        },
                        onDrag = { change, _ ->
                            val (r, c) = cellAt(change.position)
                            rows = r
                            cols = c
                        },
                        // Committing on lift means one gesture picks the size:
                        // press, slide to 5x4, release, and the table is there.
                        onDragEnd = { if (rows > 0 && cols > 0) onPick(rows, cols) },
                    )
                },
            ) {
                for (row in 1..TABLE_PICKER_MAX) {
                    Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                        for (col in 1..TABLE_PICKER_MAX) {
                            val lit = row <= rows && col <= cols
                            Box(
                                modifier = Modifier
                                    .size(SWATCH)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (lit) Accent else ChromeBorder)
                                    .pointerInput(row, col) {
                                        detectTapGestures {
                                            rows = row
                                            cols = col
                                            onPick(row, col)
                                        }
                                    },
                            )
                        }
                    }
                }
            }

            Text(
                text = if (rows > 0) "$cols × $rows" else stringResource(R.string.board_table_choose_size),
                color = if (rows > 0) TextOnChrome else TextOnChromeMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
