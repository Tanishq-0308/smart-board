package com.smartboard.teach.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.BuildConfig
import com.smartboard.teach.data.local.SmartBoardDatabase
import com.smartboard.teach.data.prefs.InputSettings
import com.smartboard.teach.data.prefs.InputSettingsStore
import com.smartboard.teach.di.IoDispatcher
import com.smartboard.teach.domain.repository.NotesAiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val message: String? = null,
    val isClearing: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: InputSettingsStore,
    private val database: SmartBoardDatabase,
    aiService: NotesAiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val settings: StateFlow<InputSettings> = store.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InputSettings(),
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val appVersion: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    val isAiConfigured: Boolean = aiService.isConfigured
    val aiModel: String = aiService.modelName

    fun setStylusOnly(value: Boolean) = viewModelScope.launch { store.setStylusOnly(value) }
    fun setPressure(value: Boolean) = viewModelScope.launch { store.setPressureSensitivity(value) }
    fun setEraserButton(value: Boolean) = viewModelScope.launch { store.setHonourEraserButton(value) }
    fun setPointerDebug(value: Boolean) = viewModelScope.launch { store.setPointerDebug(value) }
    fun set24HourClock(value: Boolean) = viewModelScope.launch { store.setUse24HourClock(value) }
    fun setShapeRecognition(value: Boolean) =
        viewModelScope.launch { store.setShapeRecognition(value) }

    /**
     * Clears board pages, strokes and backgrounds.
     *
     * Deliberately does NOT touch the seeded roster or saved notes: a teacher
     * reclaiming disk space at the end of term wants the boards gone, not
     * their generated notes or their class lists.
     */
    fun clearBoardData() {
        if (_uiState.value.isClearing) return
        _uiState.update { it.copy(isClearing = true) }
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val dao = database.boardDao()
                val latest = dao.latestSessionId()
                if (latest != null) {
                    // Strokes and text boxes cascade from the page delete.
                    dao.getPages(latest).forEach { dao.deletePage(it.id) }
                }
                dao.allBackgrounds().forEach { dao.deleteBackground(it.id) }
            }
            _uiState.update {
                it.copy(isClearing = false, message = "Board data cleared.")
            }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }
}
