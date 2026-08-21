package com.smartboard.teach.feature.classes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.domain.model.SchoolClass
import com.smartboard.teach.domain.model.Student
import com.smartboard.teach.domain.repository.RosterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ClassDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    rosterRepository: RosterRepository,
) : ViewModel() {

    private val classId: String = savedStateHandle["classId"] ?: ""

    val schoolClass: StateFlow<SchoolClass?> = rosterRepository.observeClass(classId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val students: StateFlow<List<Student>> = rosterRepository.studentsInClass(classId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
}
