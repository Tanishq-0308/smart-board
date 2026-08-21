package com.smartboard.teach.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.data.prefs.InputSettingsStore
import com.smartboard.teach.domain.model.AuthState
import com.smartboard.teach.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shell-level auth state that the header, sidebar and nav gating react to.
 *
 * Starts at [AuthState.Loading] rather than Guest so that AuthGate does not
 * bounce a signed-in teacher to the login screen for a frame during cold
 * start, before DataStore has been read.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    inputSettingsStore: InputSettingsStore,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthState.Loading,
    )

    val use24HourClock: StateFlow<Boolean> = inputSettingsStore.settings
        .map { it.use24HourClock }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
