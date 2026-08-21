package com.smartboard.teach.feature.material

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.domain.model.AuthState
import com.smartboard.teach.domain.model.StudyMaterial
import com.smartboard.teach.domain.repository.AuthRepository
import com.smartboard.teach.domain.repository.MaterialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MaterialListViewModel @Inject constructor(
    authRepository: AuthRepository,
    materialRepository: MaterialRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val materials: StateFlow<List<StudyMaterial>> = authRepository.authState
        .flatMapLatest { auth ->
            when (auth) {
                is AuthState.Authenticated -> materialRepository.materialsForTeacher(auth.teacher.id)
                else -> flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
