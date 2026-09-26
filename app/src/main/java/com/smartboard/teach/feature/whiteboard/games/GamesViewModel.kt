package com.smartboard.teach.feature.whiteboard.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.domain.model.AuthState
import com.smartboard.teach.domain.model.SchoolClass
import com.smartboard.teach.domain.repository.AuthRepository
import com.smartboard.teach.domain.repository.RosterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Class lists for the name picker. A guest sees none and types names instead;
 * signing out empties the list at once, so another teacher's roster never
 * lingers on a shared board. Goes through RosterRepository, so the switch to
 * the backend roster needs no change here.
 */
@HiltViewModel
class GamesViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val rosterRepository: RosterRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val classes: StateFlow<List<SchoolClass>> = authRepository.authState
        .flatMapLatest { auth ->
            if (auth is AuthState.Authenticated) {
                rosterRepository.classesForTeacher(auth.teacher.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun namesIn(classId: String): List<String> =
        rosterRepository.studentsInClass(classId).first().map { it.fullName }
}
