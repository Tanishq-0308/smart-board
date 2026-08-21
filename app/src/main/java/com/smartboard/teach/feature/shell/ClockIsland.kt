package com.smartboard.teach.feature.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.ClockOnBoard
import com.smartboard.teach.core.ui.theme.ClockOnBoardMuted
import com.smartboard.teach.core.util.formatHeaderDate
import com.smartboard.teach.core.util.formatHeaderTime
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * Live date and time, top-right.
 *
 * Plain text on the board rather than a dark island, matching the reference
 * panel. The clock is glanceable furniture, not a control: giving it an
 * opaque card made it read as something tappable and put a heavy block in
 * the corner where a lesson title usually goes.
 */
@Composable
fun ClockIsland(
    modifier: Modifier = Modifier,
    use24HourClock: Boolean = false,
) {
    val dimens = SmartBoardTheme.dimens
    // Ticks locally rather than through a ViewModel: AppRoot sits outside the
    // NavHost, so there is no backstack entry to scope one to.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            // Align to the top of the second so the minute flips promptly.
            delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }
    val clock = ClockState(now)

    Column(
        modifier = modifier.padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = clock.dateTime.formatHeaderTime(use24HourClock),
            color = ClockOnBoard,
            fontSize = dimens.clockTimeSize,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            // Monospace digits stop the text reflowing as seconds tick.
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = clock.dateTime.formatHeaderDate(),
            color = ClockOnBoardMuted,
            fontSize = dimens.clockDateSize,
            textAlign = TextAlign.End,
        )
    }
}
