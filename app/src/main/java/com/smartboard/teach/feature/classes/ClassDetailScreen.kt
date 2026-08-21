package com.smartboard.teach.feature.classes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartboard.teach.core.ui.component.chromeInset
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted
import com.smartboard.teach.domain.model.Student

@Composable
fun ClassDetailScreen(
    onBack: () -> Unit,
    onTakeAttendance: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClassDetailViewModel = hiltViewModel(),
) {
    val dimens = SmartBoardTheme.dimens
    val schoolClass by viewModel.schoolClass.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, modifier = Modifier.chromeInset()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(6.dp))
                Text("Classes", color = Accent)
            }
            Spacer(Modifier.width(dimens.gutter))
            Column {
                Text(
                    text = schoolClass?.displayName ?: "",
                    fontSize = dimens.titleSize,
                    fontWeight = FontWeight.SemiBold,
                    color = TextOnSurface,
                )
                Text(
                    text = "${students.size} students",
                    fontSize = dimens.labelSize,
                    color = TextOnSurfaceMuted,
                )
            }
            Spacer(Modifier.weight(1f))
            schoolClass?.let { cls ->
                TextButton(onClick = { onTakeAttendance(cls.id) }) {
                    Icon(Icons.Filled.HowToReg, contentDescription = null, tint = Accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Take attendance", color = Accent)
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = dimens.studentCardMinWidth),
            contentPadding = PaddingValues(dimens.gutter),
            horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        ) {
            items(students, key = { it.id }) { student ->
                StudentRow(student)
            }
        }
    }
}

@Composable
private fun StudentRow(student: Student) {
    val dimens = SmartBoardTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .border(
                1.dp,
                TextOnSurfaceMuted.copy(alpha = 0.2f),
                RoundedCornerShape(dimens.cornerRadius),
            )
            .padding(dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(dimens.iconSizeLarge)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = student.initials,
                fontSize = dimens.labelSize,
                fontWeight = FontWeight.SemiBold,
                color = Accent,
            )
        }
        Spacer(Modifier.width(dimens.gutterSmall))
        Column(Modifier.weight(1f)) {
            Text(
                text = student.fullName,
                fontSize = dimens.bodySize,
                color = TextOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Roll ${student.rollNumber}",
                fontSize = dimens.labelSize,
                color = TextOnSurfaceMuted,
            )
        }
    }
}
