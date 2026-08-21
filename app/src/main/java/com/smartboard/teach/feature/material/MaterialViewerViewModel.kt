package com.smartboard.teach.feature.material

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.data.file.PdfPageRenderer
import com.smartboard.teach.di.IoDispatcher
import com.smartboard.teach.domain.model.BackgroundKind
import com.smartboard.teach.domain.model.BoardBackground
import com.smartboard.teach.domain.model.StudyMaterial
import com.smartboard.teach.domain.repository.BoardRepository
import com.smartboard.teach.domain.repository.MaterialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class MaterialViewerUiState(
    val material: StudyMaterial? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val pageBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class MaterialViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val materialRepository: MaterialRepository,
    private val pdfPageRenderer: PdfPageRenderer,
    private val boardRepository: BoardRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val materialId: String = savedStateHandle["materialId"] ?: ""

    private val _state = MutableStateFlow(MaterialViewerUiState())
    val state: StateFlow<MaterialViewerUiState> = _state.asStateFlow()

    private var localFile: File? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            // ensureLocalFile copies from assets in Phase 1 and downloads from
            // the LMS in Phase 2 — same call, same loading and error states.
            when (val result = materialRepository.ensureLocalFile(materialId)) {
                is AppResult.Success -> {
                    localFile = result.data
                    when (val count = pdfPageRenderer.pageCount(result.data)) {
                        is AppResult.Success -> {
                            _state.update { it.copy(pageCount = count.data) }
                            renderPage(0)
                        }

                        is AppResult.Failure -> _state.update {
                            it.copy(isLoading = false, errorMessage = count.error.message)
                        }
                    }
                }

                is AppResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.message)
                }
            }
        }
    }

    fun renderPage(index: Int) {
        val file = localFile ?: return
        val count = _state.value.pageCount
        if (count > 0 && index !in 0 until count) return

        _state.update { it.copy(isLoading = true, currentPage = index) }
        viewModelScope.launch {
            when (val result = pdfPageRenderer.renderPageToFile(file, index)) {
                is AppResult.Success -> {
                    val bitmap = withContext(ioDispatcher) {
                        BitmapFactory.decodeFile(result.data.absolutePath)
                    }
                    _state.value.pageBitmap?.recycle()
                    _state.update {
                        it.copy(pageBitmap = bitmap, isLoading = false, errorMessage = null)
                    }
                }

                is AppResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.message)
                }
            }
        }
    }

    fun nextPage() = renderPage(_state.value.currentPage + 1)
    fun previousPage() = renderPage(_state.value.currentPage - 1)

    /**
     * Sends the current page to the whiteboard as a background to annotate.
     *
     * This is the Phase 2 seam working already: the board consumes a
     * BoardBackground and neither knows nor cares that the PDF came from the
     * LMS rather than a local import.
     */
    fun sendCurrentPageToBoard(onReady: (String) -> Unit) {
        val file = localFile ?: return
        val pageIndex = _state.value.currentPage

        viewModelScope.launch {
            when (val rendered = pdfPageRenderer.renderPageToFile(file, pageIndex)) {
                is AppResult.Success -> {
                    val background = BoardBackground(
                        id = UUID.randomUUID().toString(),
                        kind = BackgroundKind.PDF_PAGE,
                        sourcePath = file.absolutePath,
                        pdfPageIndex = pageIndex,
                        renderedPath = rendered.data.absolutePath,
                    )
                    boardRepository.saveBackground(background)
                    onReady(background.id)
                }

                is AppResult.Failure -> _state.update {
                    it.copy(errorMessage = rendered.error.message)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _state.value.pageBitmap?.recycle()
    }
}
