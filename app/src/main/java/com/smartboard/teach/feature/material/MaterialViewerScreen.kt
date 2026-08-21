package com.smartboard.teach.feature.material

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartboard.teach.core.ui.component.chromeInset
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.ErrorRed
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted

@Composable
fun MaterialViewerScreen(
    onBack: () -> Unit,
    onAnnotateOnBoard: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MaterialViewerViewModel = hiltViewModel(),
) {
    val dimens = SmartBoardTheme.dimens
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                Text("Material", color = Accent)
            }

            Spacer(Modifier.weight(1f))

            IconButton(
                onClick = viewModel::previousPage,
                enabled = state.currentPage > 0,
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous page", tint = Accent)
            }
            Text(
                text = if (state.pageCount > 0) {
                    "Page ${state.currentPage + 1} of ${state.pageCount}"
                } else {
                    ""
                },
                fontSize = dimens.bodySize,
                fontWeight = FontWeight.Medium,
                color = TextOnSurface,
            )
            IconButton(
                onClick = viewModel::nextPage,
                enabled = state.currentPage < state.pageCount - 1,
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next page", tint = Accent)
            }

            Spacer(Modifier.width(dimens.gutter))

            // The handoff: this page becomes the board background.
            TextButton(
                onClick = { viewModel.sendCurrentPageToBoard(onAnnotateOnBoard) },
                enabled = state.pageBitmap != null,
            ) {
                Icon(Icons.Filled.Draw, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(6.dp))
                Text("Annotate on board", color = Accent)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFFE9EDF2)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.errorMessage != null -> Text(
                    text = state.errorMessage!!,
                    color = ErrorRed,
                    fontSize = dimens.bodySize,
                    modifier = Modifier.padding(dimens.gutterLarge),
                )

                state.isLoading && state.pageBitmap == null -> CircularProgressIndicator()

                state.pageBitmap != null -> Image(
                    bitmap = state.pageBitmap!!.asImageBitmap(),
                    contentDescription = "Page ${state.currentPage + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dimens.gutter),
                )

                else -> Text(
                    "Nothing to show.",
                    color = TextOnSurfaceMuted,
                    fontSize = dimens.bodySize,
                )
            }
        }
    }
}
