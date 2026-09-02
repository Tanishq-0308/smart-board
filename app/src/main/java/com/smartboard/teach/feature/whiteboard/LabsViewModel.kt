package com.smartboard.teach.feature.whiteboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.data.labs.LabCatalogue
import com.smartboard.teach.data.labs.LabEntry
import com.smartboard.teach.data.labs.LabSubject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LabsUiState(
    val loading: Boolean = true,
    /** The site root each lab's `embed` address is joined onto. */
    val base: String = "",
    val labs: List<LabEntry> = emptyList(),
    val subjects: List<LabSubject> = emptyList(),
    /** True when the shelf came from the copy on the board rather than the site. */
    val offline: Boolean = false,
    val error: String? = null,
)

/**
 * Holds the shelf.
 *
 * Deliberately thin: the board's part in this is to find out what labs exist
 * and where they live. Everything a lab does after that happens inside the
 * WebView, and none of it belongs here.
 */
@HiltViewModel
class LabsViewModel @Inject constructor(
    private val catalogue: LabCatalogue,
) : ViewModel() {

    private val _state = MutableStateFlow(LabsUiState())
    val state: StateFlow<LabsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = LabsUiState(loading = true)
        viewModelScope.launch {
            _state.value = when (val result = catalogue.load()) {
                is AppResult.Success -> LabsUiState(
                    loading = false,
                    base = result.data.base,
                    labs = result.data.manifest.ready,
                    subjects = result.data.manifest.subjects,
                    offline = !result.data.fromNetwork,
                )

                is AppResult.Failure -> LabsUiState(
                    loading = false,
                    error = result.error.message,
                )
            }
        }
    }
}
