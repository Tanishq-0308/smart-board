package com.smartboard.teach.feature.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.data.file.NotesFileStore
import com.smartboard.teach.domain.model.NoteDocument
import com.smartboard.teach.domain.repository.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteDetailUiState(
    val note: NoteDocument? = null,
    val markdown: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val notesRepository: NotesRepository,
    private val fileStore: NotesFileStore,
) : ViewModel() {

    private val noteId: String = savedStateHandle["noteId"] ?: ""

    private val _state = MutableStateFlow(NoteDetailUiState())
    val state: StateFlow<NoteDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val note = notesRepository.getNote(noteId)
            val markdown = note?.markdownPath?.let { fileStore.readMarkdown(it) }
            _state.update {
                it.copy(note = note, markdown = markdown, isLoading = false)
            }
        }
    }
}
