package com.smartboard.teach.feature.classes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.domain.model.AuthState
import com.smartboard.teach.domain.model.SchoolClass
import com.smartboard.teach.domain.repository.AuthRepository
import com.smartboard.teach.domain.repository.RosterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClassListUiState(
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ClassListViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val rosterRepository: RosterRepository,
) : ViewModel() {

    /**
     * Classes for whoever is signed in. Driven off authState rather than a
     * one-shot read so signing out empties the list immediately instead of
     * leaving another teacher's roster on a shared classroom board.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val classes: StateFlow<List<SchoolClass>> = authRepository.authState
        .flatMapLatest { auth ->
            when (auth) {
                is AuthState.Authenticated -> rosterRepository.classesForTeacher(auth.teacher.id)
                else -> flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _uiState = MutableStateFlow(ClassListUiState())
    val uiState: StateFlow<ClassListUiState> = _uiState.asStateFlow()

    /**
     * Phase 1: a no-op that still drives real loading and error states, so
     * Phase 2's ERP fetch lights up here with no UI change.
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            val result = rosterRepository.refresh()
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    errorMessage = (result as? com.smartboard.teach.core.util.AppResult.Failure)
                        ?.error?.message,
                )
            }
        }
    }
}
