package com.smartboard.teach.feature.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.StatusAbsent
import com.smartboard.teach.core.ui.theme.StatusLate
import com.smartboard.teach.core.ui.theme.StatusPresent
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted
import com.smartboard.teach.domain.model.AttendanceStatus
import com.smartboard.teach.domain.model.Student

/**
 * One student, with three large P / A / L buttons.
 *
 * Deliberately not a dropdown, a swipe, or a long-press: a teacher marks 40
 * students while standing at a board, often without looking directly at the
 * control they are hitting. One tap, one decision, no hidden affordances.
 *
 * The selected state is shown by colour AND letter, so it reads from the back
 * of the room and stays unambiguous for a colour-blind viewer.
 */
@Composable
fun StudentAttendanceCard(
    student: Student,
    status: AttendanceStatus?,
    onSetStatus: (AttendanceStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .border(
                width = if (status != null) 2.dp else 1.dp,
                color = status.color()?.copy(alpha = 0.6f)
                    ?: TextOnSurfaceMuted.copy(alpha = 0.25f),
                shape = RoundedCornerShape(dimens.cornerRadius),
            )
            .padding(dimens.gutterSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = student.rollNumber,
                fontSize = dimens.labelSize,
                fontWeight = FontWeight.SemiBold,
                color = TextOnSurfaceMuted,
                modifier = Modifier.width(dimens.iconSizeLarge),
            )
            Text(
                text = student.fullName,
                // >=20sp so a name is legible from several metres away.
                fontSize = dimens.titleSize,
                color = TextOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(dimens.gutterSmall))

        Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall)) {
            AttendanceStatus.entries.forEach { option ->
                StatusButton(
                    status = option,
                    selected = status == option,
                    onClick = { onSetStatus(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusButton(
    status: AttendanceStatus,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens
    val color = status.color()!!

    Box(
        modifier = modifier
            // At least 72dp: a standing adult hitting this while moving.
            .height(dimens.touchTargetLarge)
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .background(if (selected) color else color.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = if (selected) color else color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(dimens.cornerRadius),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = status.letter,
            fontSize = dimens.titleSize,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else color,
        )
    }
}

private fun AttendanceStatus?.color(): Color? = when (this) {
    AttendanceStatus.PRESENT -> StatusPresent
    AttendanceStatus.ABSENT -> StatusAbsent
    AttendanceStatus.LATE -> StatusLate
    null -> null
}
