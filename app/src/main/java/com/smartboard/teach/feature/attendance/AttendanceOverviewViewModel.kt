package com.smartboard.teach.feature.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.domain.model.AttendanceSession
import com.smartboard.teach.domain.model.AuthState
import com.smartboard.teach.domain.model.SchoolClass
import com.smartboard.teach.domain.repository.AttendanceRepository
import com.smartboard.teach.domain.repository.AuthRepository
import com.smartboard.teach.domain.repository.RosterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** One class on today's attendance overview; [today] is null until it is taken. */
data class ClassAttendance(val schoolClass: SchoolClass, val today: AttendanceSession?)

/**
 * Today's attendance across the teacher's classes: which are done and which
 * are still to take. Reads the same repositories as the rest of the app, so
 * the backend switch needs no change here.
 */
@HiltViewModel
class AttendanceOverviewViewModel @Inject constructor(
    authRepository: AuthRepository,
    rosterRepository: RosterRepository,
    attendanceRepository: AttendanceRepository,
) : ViewModel() {

    val date: LocalDate = LocalDate.now()

    @OptIn(ExperimentalCoroutinesApi::class)
    val classes: StateFlow<List<ClassAttendance>> = authRepository.authState
        .flatMapLatest { auth ->
            if (auth is AuthState.Authenticated) {
                rosterRepository.classesForTeacher(auth.teacher.id)
            } else {
                flowOf(emptyList())
            }
        }
        .flatMapLatest { classes ->
            if (classes.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    classes.map { schoolClass ->
                        attendanceRepository.sessionFor(schoolClass.id, date)
                    },
                ) { sessions -> classes.zip(sessions) { c, s -> ClassAttendance(c, s) } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
