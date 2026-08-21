package com.smartboard.teach.feature.material

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.smartboard.teach.core.ui.component.EmptyState
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted
import com.smartboard.teach.domain.model.MaterialKind
import com.smartboard.teach.domain.model.StudyMaterial

@Composable
fun MaterialListScreen(
    onOpenMaterial: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MaterialListViewModel = hiltViewModel(),
) {
    val dimens = SmartBoardTheme.dimens
    val materials by viewModel.materials.collectAsStateWithLifecycle()

    if (materials.isEmpty()) {
        EmptyState(
            title = "No study material",
            detail = "Material assigned to you will appear here.",
            icon = Icons.AutoMirrored.Filled.MenuBook,
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
        items(materials, key = { it.id }) { material ->
            MaterialCard(material = material, onClick = { onOpenMaterial(material.id) })
        }
    }
}

@Composable
private fun MaterialCard(material: StudyMaterial, onClick: () -> Unit) {
    val dimens = SmartBoardTheme.dimens
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .border(
                1.dp,
                TextOnSurfaceMuted.copy(alpha = 0.25f),
                RoundedCornerShape(dimens.cornerRadius),
            )
            .clickable(onClick = onClick)
            .padding(dimens.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (material.kind) {
                MaterialKind.PDF -> Icons.Filled.PictureAsPdf
                MaterialKind.BOOK -> Icons.AutoMirrored.Filled.MenuBook
                MaterialKind.IMAGE -> Icons.Filled.Image
            },
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(dimens.iconSizeLarge),
        )
        Spacer(Modifier.width(dimens.gutter))
        Column(Modifier.weight(1f)) {
            Text(
                text = material.title,
                fontSize = dimens.bodySize,
                fontWeight = FontWeight.Medium,
                color = TextOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = material.kind.name,
                fontSize = dimens.labelSize,
                color = TextOnSurfaceMuted,
            )
        }
    }
}
