package com.smartboard.teach.feature.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartboard.teach.core.ui.component.chromeInset
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.StatusAbsent
import com.smartboard.teach.core.ui.theme.StatusLate
import com.smartboard.teach.core.ui.theme.StatusPresent
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted
import com.smartboard.teach.core.util.formatFriendly
import java.time.LocalDate

@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val dimens = SmartBoardTheme.dimens
    val schoolClass by viewModel.schoolClass.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // --- header: class, date, bulk actions, live counts ---
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, modifier = Modifier.chromeInset()) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Accent,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Back", color = Accent)
                }

                Spacer(Modifier.width(dimens.gutterSmall))

                Column {
                    Text(
                        text = schoolClass?.displayName ?: "Attendance",
                        fontSize = dimens.titleSize,
                        fontWeight = FontWeight.SemiBold,
                        color = TextOnSurface,
                    )
                    if (state.loadedExisting) {
                        Text(
                            text = "Editing saved attendance",
                            fontSize = dimens.labelSize,
                            color = TextOnSurfaceMuted,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                DateSelector(
                    date = state.date,
                    onChange = viewModel::loadForDate,
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = viewModel::markAllPresent) { Text("Mark all present") }
                TextButton(onClick = viewModel::clearAll) { Text("Clear all") }
                Spacer(Modifier.weight(1f))
                CountChip("Present", state.presentCount, StatusPresent)
                Spacer(Modifier.width(dimens.gutterSmall))
                CountChip("Absent", state.absentCount, StatusAbsent)
                Spacer(Modifier.width(dimens.gutterSmall))
                CountChip("Late", state.lateCount, StatusLate)
                Spacer(Modifier.width(dimens.gutterSmall))
                CountChip("Unmarked", students.size - state.marks.size, TextOnSurfaceMuted)
            }

            // --- the grid: adaptive so ~40 students fit with little scrolling ---
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = dimens.studentCardMinWidth),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = dimens.gutter,
                    end = dimens.gutter,
                    top = dimens.gutterSmall,
                    // room for the floating Save button
                    bottom = dimens.touchTargetLarge * 1.6f,
                ),
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
                verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            ) {
                items(students, key = { it.id }) { student ->
                    StudentAttendanceCard(
                        student = student,
                        status = state.marks[student.id],
                        onSetStatus = { viewModel.setStatus(student.id, it) },
                    )
                }
            }
        }

        // Bottom-right: the reach zone for a standing adult at a board.
        Button(
            onClick = viewModel::save,
            enabled = state.marks.isNotEmpty() && !state.isSaving,
            shape = RoundedCornerShape(dimens.cornerRadius),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(dimens.gutterLarge)
                .height(dimens.touchTargetLarge)
                .width(dimens.studentCardMinWidth * 0.8f),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(dimens.iconSize),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    "Save attendance",
                    fontSize = dimens.bodySize,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        val message = state.savedMessage ?: state.errorMessage
        if (message != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(dimens.gutter),
                action = {
                    TextButton(onClick = viewModel::dismissMessage) { Text("Dismiss") }
                },
            ) { Text(message) }
        }
    }
}

@Composable
private fun DateSelector(date: LocalDate, onChange: (LocalDate) -> Unit) {
    val dimens = SmartBoardTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(date.minusDays(1)) }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day", tint = Accent)
        }
        Text(
            text = date.formatFriendly(),
            fontSize = dimens.bodySize,
            fontWeight = FontWeight.Medium,
            color = TextOnSurface,
        )
        IconButton(
            onClick = { onChange(date.plusDays(1)) },
            // Attendance is not recorded ahead of time.
            enabled = date.isBefore(LocalDate.now()),
        ) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next day", tint = Accent)
        }
    }
}

@Composable
private fun CountChip(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    val dimens = SmartBoardTheme.dimens
    Row(
        Modifier
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = dimens.gutterSmall, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label ",
            fontSize = dimens.labelSize,
            color = TextOnSurfaceMuted,
        )
        Text(
            text = "$count",
            fontSize = dimens.labelSize,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}
