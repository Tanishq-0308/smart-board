package com.smartboard.teach.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartboard.teach.core.ui.component.EmptyState
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted
import com.smartboard.teach.core.ui.theme.WarningAmber
import com.smartboard.teach.core.util.formatListDateTime
import com.smartboard.teach.domain.model.NoteDocument
import com.smartboard.teach.domain.model.NoteStatus

@Composable
fun NotesListScreen(
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesListViewModel = hiltViewModel(),
) {
    val dimens = SmartBoardTheme.dimens
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize()) {
        if (notes.isEmpty()) {
            EmptyState(
                title = "No notes yet",
                detail = "Use the camera button on the whiteboard to capture the board " +
                    "and turn it into notes.",
                icon = Icons.Filled.Description,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = dimens.studentCardMinWidth * 1.3f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = dimens.touchTarget + dimens.gutter,
                    top = dimens.gutter,
                    end = dimens.gutter,
                    bottom = dimens.gutter,
                ),
                horizontalArrangement = Arrangement.spacedBy(dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(dimens.gutter),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        isRetrying = uiState.retryingNoteId == note.id,
                        onClick = { onOpenNote(note.id) },
                        onRetry = { viewModel.retry(note) },
                        onDelete = { viewModel.delete(note) },
                    )
                }
            }
        }

        uiState.message?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(dimens.gutter),
                action = {
                    TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
                },
            ) { Text(message) }
        }
    }
}

@Composable
private fun NoteCard(
    note: NoteDocument,
    isRetrying: Boolean,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    val isPending = note.status == NoteStatus.FAILED_PENDING_RETRY

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .border(
                width = 1.dp,
                color = if (isPending) WarningAmber.copy(alpha = 0.5f) else TextOnSurfaceMuted.copy(alpha = 0.25f),
                shape = RoundedCornerShape(dimens.cornerRadius),
            )
            .clickable(enabled = !isPending, onClick = onClick)
            .padding(dimens.gutter),
    ) {
        Text(
            text = note.title,
            fontSize = dimens.bodySize,
            fontWeight = FontWeight.SemiBold,
            color = TextOnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = note.createdAt.formatListDateTime(),
            fontSize = dimens.labelSize,
            color = TextOnSurfaceMuted,
        )
        Spacer(Modifier.height(dimens.gutterSmall))

        if (isPending) {
            // The honest message: the board content IS saved, only the AI
            // summary is missing. A generic error here would make a teacher
            // think they lost the lesson.
            PendingBanner(
                message = note.failureMessage ?: "Summary pending.",
                isRetrying = isRetrying,
                onRetry = onRetry,
            )
        } else {
            Text(
                text = note.summary,
                fontSize = dimens.labelSize,
                color = TextOnSurfaceMuted,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(dimens.gutterSmall))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Delete note",
                    tint = TextOnSurfaceMuted,
                    modifier = Modifier.size(dimens.iconSize),
                )
            }
        }
    }
}

@Composable
private fun PendingBanner(
    message: String,
    isRetrying: Boolean,
    onRetry: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .background(WarningAmber.copy(alpha = 0.10f), RoundedCornerShape(dimens.cornerRadius))
            .padding(dimens.gutterSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CloudOff,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(dimens.iconSize * 0.8f),
            )
            Spacer(Modifier.width(dimens.gutterSmall))
            Text(
                text = "Board saved — summary pending",
                fontSize = dimens.labelSize,
                fontWeight = FontWeight.Medium,
                color = WarningAmber,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            fontSize = dimens.labelSize,
            color = TextOnSurfaceMuted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(dimens.gutterSmall))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isRetrying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(dimens.iconSize * 0.7f),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(dimens.gutterSmall))
                Text("Retrying…", fontSize = dimens.labelSize, color = TextOnSurfaceMuted)
            } else {
                TextButton(onClick = onRetry) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(dimens.iconSize * 0.8f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Retry", color = Accent, fontSize = dimens.labelSize)
                }
            }
        }
    }
}
