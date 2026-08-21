package com.smartboard.teach.feature.attendance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.domain.model.AttendanceStatus
import com.smartboard.teach.domain.model.SchoolClass
import com.smartboard.teach.domain.model.Student
import com.smartboard.teach.domain.repository.AttendanceRepository
import com.smartboard.teach.domain.repository.AuthRepository
import com.smartboard.teach.domain.repository.RosterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AttendanceUiState(
    val date: LocalDate = LocalDate.now(),
    val marks: Map<String, AttendanceStatus> = emptyMap(),
    val isSaving: Boolean = false,
    val savedMessage: String? = null,
    val errorMessage: String? = null,
    /** True once an existing session for this day has been loaded. */
    val loadedExisting: Boolean = false,
) {
    val presentCount: Int get() = marks.values.count { it == AttendanceStatus.PRESENT }
    val absentCount: Int get() = marks.values.count { it == AttendanceStatus.ABSENT }
    val lateCount: Int get() = marks.values.count { it == AttendanceStatus.LATE }
}

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    rosterRepository: RosterRepository,
    private val attendanceRepository: AttendanceRepository,
    private val authRepository: AuthRepository,
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

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    init {
        loadForDate(LocalDate.now())
    }

    /**
     * Loads any session already recorded for this class on this date.
     *
     * Because (classId, date) is unique, re-opening a day EDITS that session
     * rather than creating a second one — so a teacher correcting a mistake
     * does not end up with two conflicting records for the same lesson.
     */
    fun loadForDate(date: LocalDate) {
        viewModelScope.launch {
            val existing = attendanceRepository.sessionFor(classId, date).first()
            _uiState.update {
                it.copy(
                    date = date,
                    marks = existing?.marks ?: emptyMap(),
                    loadedExisting = existing != null,
                    savedMessage = null,
                    errorMessage = null,
                )
            }
        }
    }

    fun setStatus(studentId: String, status: AttendanceStatus) {
        _uiState.update {
            it.copy(
                marks = it.marks + (studentId to status),
                savedMessage = null,
            )
        }
    }

    /** Teachers mark exceptions, not everyone — this is the common opening move. */
    fun markAllPresent() {
        val all = students.value.associate { it.id to AttendanceStatus.PRESENT }
        _uiState.update { it.copy(marks = all, savedMessage = null) }
    }

    fun clearAll() {
        _uiState.update { it.copy(marks = emptyMap(), savedMessage = null) }
    }

    /**
     * Explicit save, never autosave-on-tap: a mis-tap while walking past a
     * classroom board must not silently alter an attendance record.
     */
    fun save() {
        val current = _uiState.value
        if (current.isSaving || current.marks.isEmpty()) return

        _uiState.update { it.copy(isSaving = true, errorMessage = null, savedMessage = null) }
        viewModelScope.launch {
            val teacher = authRepository.currentTeacher()
            if (teacher == null) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "Please sign in again to save.")
                }
                return@launch
            }

            when (
                val result = attendanceRepository.save(
                    classId = classId,
                    date = current.date,
                    teacherId = teacher.id,
                    marks = current.marks,
                )
            ) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        loadedExisting = true,
                        savedMessage = "Attendance saved for ${current.date}.",
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = result.error.message)
                }
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(savedMessage = null, errorMessage = null) }
    }
}
