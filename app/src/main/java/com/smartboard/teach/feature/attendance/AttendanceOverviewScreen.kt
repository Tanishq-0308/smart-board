package com.smartboard.teach.feature.attendance

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.component.EmptyState
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.StatusAbsent
import com.smartboard.teach.core.ui.theme.StatusLate
import com.smartboard.teach.core.ui.theme.StatusPresent
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted
import com.smartboard.teach.core.ui.theme.WarningAmber
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Attendance destination: today's register for every class, so a teacher
 * sees at a glance what is done and what is still to take. My Classes is the
 * place for class details; this is the place for the register.
 */
@Composable
fun AttendanceOverviewScreen(
    onTakeAttendance: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AttendanceOverviewViewModel = hiltViewModel(),
) {
    val dimens = SmartBoardTheme.dimens
    val classes by viewModel.classes.collectAsStateWithLifecycle()

    if (classes.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.classes_empty_title),
            detail = stringResource(R.string.classes_empty_detail),
            icon = Icons.Filled.HowToReg,
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = dimens.touchTarget + dimens.gutterLarge,
            top = dimens.gutterLarge,
            end = dimens.gutterLarge,
            bottom = dimens.gutterLarge,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        item {
            Text(
                stringResource(R.string.attendance_overview_title),
                fontSize = dimens.titleSize,
                fontWeight = FontWeight.SemiBold,
                color = TextOnSurface,
            )
            Text(
                viewModel.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                fontSize = dimens.bodySize,
                color = TextOnSurfaceMuted,
                modifier = Modifier.padding(bottom = dimens.gutter),
            )
        }
        items(classes, key = { it.schoolClass.id }) { row ->
            ClassRegisterRow(row) { onTakeAttendance(row.schoolClass.id) }
        }
    }
}

@Composable
private fun ClassRegisterRow(row: ClassAttendance, onOpen: () -> Unit) {
    val dimens = SmartBoardTheme.dimens
    val session = row.today
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .border(1.dp, TextOnSurfaceMuted.copy(alpha = 0.25f), RoundedCornerShape(dimens.cornerRadius))
            .clickable(onClick = onOpen)
            .padding(dimens.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.schoolClass.displayName,
                fontSize = dimens.bodySize,
                fontWeight = FontWeight.SemiBold,
                color = TextOnSurface,
            )
            if (session == null) {
                Text(stringResource(R.string.attendance_not_taken), fontSize = dimens.labelSize, color = WarningAmber)
            } else {
                // Letters as well as colours: colour is never the only signal.
                Row {
                    Text(stringResource(R.string.attendance_count_present, session.presentCount), fontSize = dimens.labelSize, color = StatusPresent)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.attendance_count_absent, session.absentCount), fontSize = dimens.labelSize, color = StatusAbsent)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.attendance_count_late, session.lateCount), fontSize = dimens.labelSize, color = StatusLate)
                }
            }
        }
        TextButton(onClick = onOpen) {
            Text(
                stringResource(if (session == null) R.string.attendance_take else R.string.attendance_edit),
                color = Accent,
                fontSize = dimens.labelSize,
            )
        }
    }
}
