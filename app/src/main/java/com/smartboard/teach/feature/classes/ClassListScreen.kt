package com.smartboard.teach.feature.classes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material3.Icon
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
import com.smartboard.teach.core.ui.component.EmptyState
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted
import com.smartboard.teach.domain.model.SchoolClass

@Composable
fun ClassListScreen(
    onOpenClass: (String) -> Unit,
    onTakeAttendance: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClassListViewModel = hiltViewModel(),
) {
    val dimens = SmartBoardTheme.dimens
    val classes by viewModel.classes.collectAsStateWithLifecycle()

    if (classes.isEmpty()) {
        EmptyState(
            title = "No classes assigned",
            detail = "Classes assigned to you will appear here.",
            icon = Icons.Filled.Groups,
            modifier = modifier,
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = dimens.studentCardMinWidth * 1.2f),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = dimens.touchTarget + dimens.gutter,
            top = dimens.gutter,
            end = dimens.gutter,
            bottom = dimens.gutter,
        ),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(dimens.gutter),
    ) {
        items(classes, key = { it.id }) { schoolClass ->
            ClassCard(
                schoolClass = schoolClass,
                onOpen = { onOpenClass(schoolClass.id) },
                onTakeAttendance = { onTakeAttendance(schoolClass.id) },
            )
        }
    }
}

@Composable
private fun ClassCard(
    schoolClass: SchoolClass,
    onOpen: () -> Unit,
    onTakeAttendance: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .border(
                1.dp,
                TextOnSurfaceMuted.copy(alpha = 0.25f),
                RoundedCornerShape(dimens.cornerRadius),
            )
            .clickable(onClick = onOpen)
            .padding(dimens.gutter),
    ) {
        Text(
            text = schoolClass.displayName,
            fontSize = dimens.titleSize,
            fontWeight = FontWeight.SemiBold,
            color = TextOnSurface,
        )
        schoolClass.subject?.let {
            Spacer(Modifier.height(2.dp))
            Text(text = it, fontSize = dimens.bodySize, color = TextOnSurfaceMuted)
        }

        Spacer(Modifier.height(dimens.gutterSmall))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Groups,
                contentDescription = null,
                tint = TextOnSurfaceMuted,
                modifier = Modifier.size(dimens.iconSize * 0.8f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${schoolClass.studentCount} students",
                fontSize = dimens.labelSize,
                color = TextOnSurfaceMuted,
            )
        }

        Spacer(Modifier.height(dimens.gutterSmall))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onTakeAttendance) {
                Icon(
                    Icons.Filled.HowToReg,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(dimens.iconSize * 0.8f),
                )
                Spacer(Modifier.width(6.dp))
                Text("Attendance", color = Accent, fontSize = dimens.labelSize)
            }
        }
    }
}
