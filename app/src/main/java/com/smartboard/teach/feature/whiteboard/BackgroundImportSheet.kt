package com.smartboard.teach.feature.whiteboard

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.ErrorRed
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted

/**
 * Background import flow.
 *
 * Two stages: pick a source, then (for PDFs) pick which page. The page picker
 * exists because a teacher importing a 40-page chapter almost never wants
 * page 1 — they want the page they are about to teach.
 */
@Composable
fun BackgroundImportSheet(
    uiState: BackgroundImportState,
    onPickImage: () -> Unit,
    onPickPdf: () -> Unit,
    onChoosePage: (Int) -> Unit,
    onRemoveBackground: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = if (uiState.hasBackground) {
            {
                TextButton(onClick = onRemoveBackground) {
                    Text("Remove background", color = ErrorRed)
                }
            }
        } else {
            null
        },
        title = {
            Text(
                text = if (uiState.pdfPageCount != null) "Choose a page" else "Board background",
                fontSize = dimens.titleSize,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Box(Modifier.widthIn(min = 420.dp)) {
                when {
                    uiState.isBusy -> BusyBlock(uiState.busyMessage)

                    uiState.errorMessage != null -> Column {
                        Text(uiState.errorMessage, color = ErrorRed, fontSize = dimens.bodySize)
                        Spacer(Modifier.height(dimens.gutter))
                        SourceRow(onPickImage, onPickPdf)
                    }

                    uiState.pdfPageCount != null -> PagePicker(
                        pageCount = uiState.pdfPageCount,
                        onChoosePage = onChoosePage,
                    )

                    else -> Column {
                        Text(
                            "Put an image or a PDF page behind the ink and annotate over it.",
                            color = TextOnSurfaceMuted,
                            fontSize = dimens.bodySize,
                        )
                        Spacer(Modifier.height(dimens.gutter))
                        SourceRow(onPickImage, onPickPdf)
                    }
                }
            }
        },
    )
}

@Composable
private fun BusyBlock(message: String) {
    val dimens = SmartBoardTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(dimens.iconSize), strokeWidth = 2.dp)
        Spacer(Modifier.width(dimens.gutter))
        Text(message, fontSize = dimens.bodySize, color = TextOnSurfaceMuted)
    }
}

@Composable
private fun SourceRow(onPickImage: () -> Unit, onPickPdf: () -> Unit) {
    val dimens = SmartBoardTheme.dimens
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) {
        SourceCard(Icons.Filled.Image, "Image", onPickImage)
        SourceCard(Icons.Filled.PictureAsPdf, "PDF", onPickPdf)
    }
}

@Composable
private fun SourceCard(icon: ImageVector, label: String, onClick: () -> Unit) {
    val dimens = SmartBoardTheme.dimens
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .border(
                1.dp,
                TextOnSurfaceMuted.copy(alpha = 0.35f),
                RoundedCornerShape(dimens.cornerRadius),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.gutterLarge, vertical = dimens.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(dimens.iconSizeLarge))
        Spacer(Modifier.height(dimens.gutterSmall))
        Text(label, fontSize = dimens.bodySize, color = TextOnSurface)
    }
}

@Composable
private fun PagePicker(pageCount: Int, onChoosePage: (Int) -> Unit) {
    val dimens = SmartBoardTheme.dimens
    Column {
        Text(
            "$pageCount pages",
            color = TextOnSurfaceMuted,
            fontSize = dimens.labelSize,
        )
        Spacer(Modifier.height(dimens.gutterSmall))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = dimens.touchTargetLarge),
            modifier = Modifier.height(280.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        ) {
            items((0 until pageCount).toList()) { index ->
                Box(
                    modifier = Modifier
                        .height(dimens.touchTarget)
                        .clip(RoundedCornerShape(dimens.cornerRadius))
                        .background(Color(0xFFEFF2F6))
                        .clickable { onChoosePage(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", fontSize = dimens.bodySize, color = TextOnSurface)
                }
            }
        }
    }
}

data class BackgroundImportState(
    val isBusy: Boolean = false,
    val busyMessage: String = "",
    val pdfPageCount: Int? = null,
    val errorMessage: String? = null,
    val hasBackground: Boolean = false,
)
