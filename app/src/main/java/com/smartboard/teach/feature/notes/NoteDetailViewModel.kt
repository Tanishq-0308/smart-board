package com.smartboard.teach.feature.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.data.file.NotesFileStore
import com.smartboard.teach.domain.lessonpack.PackDefaults
import com.smartboard.teach.domain.model.AssignmentQuestion
import com.smartboard.teach.domain.model.NoteDocument
import com.smartboard.teach.domain.repository.AuthRepository
import com.smartboard.teach.domain.repository.NotesRepository
import com.smartboard.teach.domain.repository.RosterRepository
import com.smartboard.teach.domain.usecase.LessonPackUseCase
import com.smartboard.teach.domain.usecase.PackProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class NoteDetailUiState(
    val note: NoteDocument? = null,
    val markdown: String? = null,
    val isLoading: Boolean = true,
    /** A pack step running from this screen (resume, regenerate, publish). */
    val busy: PackBusy? = null,
    val message: String? = null,
    /** The class picker, for a pack made without one (as a guest). */
    val setup: PackSetup? = null,
)

sealed interface PackBusy {
    data class Working(val progress: PackProgress?) : PackBusy
    data object Publishing : PackBusy
}

/** A note, and for a lesson pack its review: edit the assignment, then publish it. */
@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val notesRepository: NotesRepository,
    private val fileStore: NotesFileStore,
    private val lessonPack: LessonPackUseCase,
    private val authRepository: AuthRepository,
    private val rosterRepository: RosterRepository,
) : ViewModel() {

    private val noteId: String = savedStateHandle["noteId"] ?: ""

    private val _state = MutableStateFlow(NoteDetailUiState())
    val state: StateFlow<NoteDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        var note = notesRepository.getNote(noteId)
        // A due date a week out unless the teacher picks one.
        val pack = note?.pack
        if (pack != null && pack.assignment != null && pack.dueDate == null && !pack.isShared) {
            (lessonPack.saveReview(noteId, pack.assignment, LocalDate.now().plusDays(7).toString()) as? AppResult.Success)
                ?.let { note = it.data }
        }
        val markdown = note?.markdownPath?.let { fileStore.readMarkdown(it) }
        _state.update { it.copy(note = note, markdown = markdown, isLoading = false) }
    }

    fun editQuestion(edited: AssignmentQuestion) = review { questions -> questions.map { if (it.id == edited.id) edited else it } }

    fun deleteQuestion(id: String) = review { questions -> questions.filterNot { it.id == id } }

    fun setDueDate(date: LocalDate) {
        val pack = _state.value.note?.pack ?: return
        val draft = pack.assignment ?: return
        viewModelScope.launch { apply(lessonPack.saveReview(noteId, draft, date.toString())) }
    }

    private fun review(change: (List<AssignmentQuestion>) -> List<AssignmentQuestion>) {
        val pack = _state.value.note?.pack ?: return
        val draft = pack.assignment ?: return
        viewModelScope.launch { apply(lessonPack.saveReview(noteId, draft.copy(questions = change(draft.questions)), pack.dueDate)) }
    }

    /** Carries a failed pack on from where it stopped. */
    fun resume() = run { lessonPack.resume(noteId) { p -> _state.update { it.copy(busy = PackBusy.Working(p)) } } }

    fun regenerate() = run { lessonPack.regenerate(noteId) { p -> _state.update { it.copy(busy = PackBusy.Working(p)) } } }

    fun publish() {
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = PackBusy.Publishing, message = null) }
        viewModelScope.launch { apply(lessonPack.publish(noteId)) }
    }

    /** Opens the class picker (a guest's pack, or to change the class before publishing). */
    fun chooseClass() {
        viewModelScope.launch {
            val teacher = authRepository.currentTeacher() ?: return@launch
            val classes = rosterRepository.classesForTeacher(teacher.id).first()
            val slots = rosterRepository.timetable()
            val pack = _state.value.note?.pack
            _state.update {
                it.copy(setup = PackSetup(
                    classes = classes,
                    subjects = classes.associate { c -> c.id to PackDefaults.subjectsOf(c, slots) },
                    classId = pack?.classId,
                    subjectId = pack?.subjectId,
                    size = pack?.size ?: com.smartboard.teach.domain.model.AssignmentSize.STANDARD,
                ))
            }
        }
    }

    fun updateSetup(setup: PackSetup) = _state.update { it.copy(setup = setup) }

    fun cancelSetup() = _state.update { it.copy(setup = null) }

    fun confirmSetup() {
        val setup = _state.value.setup ?: return
        val classId = setup.classId ?: return
        val c = setup.classes.first { it.id == classId }
        val subjects = setup.subjects[classId].orEmpty()
        val subject = subjects.firstOrNull { it.id != null && it.id == setup.subjectId } ?: subjects.firstOrNull()
        _state.update { it.copy(setup = null) }
        run {
            val chosen = lessonPack.chooseClass(noteId, classId, c.displayName, subject?.id, subject?.name.orEmpty(), setup.size)
            if (chosen is AppResult.Failure) chosen
            else lessonPack.resume(noteId) { p -> _state.update { it.copy(busy = PackBusy.Working(p)) } }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun run(block: suspend () -> AppResult<NoteDocument>) {
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = PackBusy.Working(null), message = null) }
        viewModelScope.launch { apply(block()) }
    }

    private suspend fun apply(result: AppResult<NoteDocument>) {
        val failure = (result as? AppResult.Failure)?.error?.message
        load()
        _state.update { it.copy(busy = null, message = failure) }
    }
}
