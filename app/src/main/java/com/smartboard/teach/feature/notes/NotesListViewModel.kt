package com.smartboard.teach.feature.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.domain.model.NoteDocument
import com.smartboard.teach.domain.repository.NotesAiService
import com.smartboard.teach.domain.repository.NotesRepository
import com.smartboard.teach.domain.usecase.GenerateNotesFromSnapshotUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class NotesListUiState(
    val retryingNoteId: String? = null,
    val message: String? = null,
)

@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val generateNotes: GenerateNotesFromSnapshotUseCase,
    private val aiService: NotesAiService,
) : ViewModel() {

    val notes: StateFlow<List<NoteDocument>> = notesRepository.observeNotes().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _uiState = MutableStateFlow(NotesListUiState())
    val uiState: StateFlow<NotesListUiState> = _uiState.asStateFlow()

    val isAiConfigured: Boolean get() = aiService.isConfigured

    /**
     * Re-runs summarization from the snapshot already on disk. This is what
     * makes the offline path recoverable rather than a dead end.
     */
    fun retry(note: NoteDocument) {
        if (_uiState.value.retryingNoteId != null) return
        _uiState.update { it.copy(retryingNoteId = note.id, message = null) }

        viewModelScope.launch {
            val bitmap = decodeSnapshot(note.snapshotPath)
            if (bitmap == null) {
                _uiState.update {
                    it.copy(
                        retryingNoteId = null,
                        message = "The saved snapshot could not be opened.",
                    )
                }
                return@launch
            }

            val result = generateNotes.retry(note.id, bitmap)
            bitmap.recycle()

            _uiState.update {
                it.copy(
                    retryingNoteId = null,
                    message = when (result) {
                        is AppResult.Success -> "Notes generated."
                        is AppResult.Failure -> result.error.message
                    },
                )
            }
        }
    }

    fun delete(note: NoteDocument) {
        viewModelScope.launch { notesRepository.delete(note.id) }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun decodeSnapshot(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }
}
