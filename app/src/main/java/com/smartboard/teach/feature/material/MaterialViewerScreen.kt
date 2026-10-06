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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.component.chromeInset
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.ErrorRed
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted

@Composable
fun MaterialViewerScreen(
    onBack: () -> Unit,
    /** The PDF's path and the first and last page (0-based) the teacher picked. */
    onAnnotateOnBoard: (String, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MaterialViewerViewModel = hiltViewModel(),
) {
    val dimens = SmartBoardTheme.dimens
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickPages by remember { mutableStateOf(false) }

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
                Text(stringResource(R.string.material_back_label), color = Accent)
            }

            Spacer(Modifier.weight(1f))

            IconButton(
                onClick = viewModel::previousPage,
                enabled = state.currentPage > 0,
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.material_previous_page), tint = Accent)
            }
            Text(
                text = if (state.pageCount > 0) {
                    stringResource(R.string.material_page_of, state.currentPage + 1, state.pageCount)
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
                Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.material_next_page), tint = Accent)
            }

            Spacer(Modifier.width(dimens.gutter))

            // The handoff: the pages the teacher picks go to the board, top to bottom.
            TextButton(
                onClick = {
                    if (state.pageCount > 1) pickPages = true
                    else viewModel.sendDocumentToBoard(0, 0, onAnnotateOnBoard)
                },
                enabled = state.pageBitmap != null,
            ) {
                Icon(Icons.Filled.Draw, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.material_annotate_on_board), color = Accent)
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
                    contentDescription = stringResource(R.string.material_page, state.currentPage + 1),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dimens.gutter),
                )

                else -> Text(
                    stringResource(R.string.material_nothing_to_show),
                    color = TextOnSurfaceMuted,
                    fontSize = dimens.bodySize,
                )
            }
        }
    }

    if (pickPages) {
        PageRangeDialog(
            pageCount = state.pageCount,
            currentPage = state.currentPage,
            onDismiss = { pickPages = false },
            onConfirm = { first, last ->
                pickPages = false
                viewModel.sendDocumentToBoard(first, last, onAnnotateOnBoard)
            },
        )
    }
}

/** From and To, 1-based as the teacher reads them; opens on the page in view. */
@Composable
private fun PageRangeDialog(pageCount: Int, currentPage: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val dimens = SmartBoardTheme.dimens
    var from by remember { mutableStateOf((currentPage + 1).toString()) }
    var to by remember { mutableStateOf((currentPage + 1).toString()) }
    val first = from.toIntOrNull()
    val last = to.toIntOrNull()
    val valid = first != null && last != null && first in 1..pageCount && last in first..pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.material_pages_title)) },
        text = {
            Column {
                Text(stringResource(R.string.material_pages_intro, pageCount), fontSize = dimens.labelSize, color = TextOnSurfaceMuted)
                Spacer(Modifier.height(dimens.gutter))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PageField(stringResource(R.string.material_pages_from), from) {
                        from = it
                        // To follows From forward, so typing the start page alone is enough.
                        if ((it.toIntOrNull() ?: 0) > (to.toIntOrNull() ?: 0)) to = it
                    }
                    Spacer(Modifier.width(dimens.gutter))
                    PageField(stringResource(R.string.material_pages_to), to) { to = it }
                }
                Spacer(Modifier.height(dimens.gutterSmall))
                Text(
                    if (valid) pluralStringResource(R.plurals.material_pages_count, last!! - first!! + 1, last - first + 1)
                    else stringResource(R.string.material_pages_invalid, pageCount),
                    fontSize = dimens.labelSize,
                    color = if (valid) TextOnSurfaceMuted else ErrorRed,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(first!! - 1, last!! - 1) }, enabled = valid) {
                Text(stringResource(R.string.material_pages_open))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.panel_cancel)) } },
    )
}

@Composable
private fun PageField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(140.dp),
    )
}
