package com.smartboard.teach.feature.whiteboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.data.file.BoardExportStore
import com.smartboard.teach.data.file.PdfPageRenderer
import com.smartboard.teach.data.file.SafImporter
import com.smartboard.teach.data.file.posterPathFor
import com.smartboard.teach.data.prefs.InputSettings
import com.smartboard.teach.data.prefs.InputSettingsStore
import com.smartboard.teach.domain.model.BackgroundKind
import com.smartboard.teach.domain.model.BoardBackground
import com.smartboard.teach.domain.model.BoardCanvasStyle
import com.smartboard.teach.domain.model.BoardPage
import com.smartboard.teach.domain.model.CameraState
import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.Lesson
import com.smartboard.teach.domain.model.ContainerKind
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.TextBox
import com.smartboard.teach.domain.repository.BoardRepository
import com.smartboard.teach.domain.repository.NotesAiService
import com.smartboard.teach.data.file.LookupCropStore
import com.smartboard.teach.domain.usecase.ExplainBoardRegionUseCase
import com.smartboard.teach.domain.usecase.GenerateNotesFromSnapshotUseCase
import com.smartboard.teach.feature.notes.SnapshotPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class WhiteboardUiState(
    val sessionId: String? = null,
    val pages: List<BoardPage> = emptyList(),
    val currentPageId: String? = null,
    val isLoading: Boolean = true,
) {
    val currentIndex: Int get() = pages.indexOfFirst { it.id == currentPageId }
    val pageLabel: String
        get() = if (currentIndex >= 0) "Page ${currentIndex + 1} of ${pages.size}" else ""
}

/**
 * Owns board persistence and page navigation.
 *
 * Writes are debounced rather than immediate: a stroke completing every few
 * hundred milliseconds during active teaching would otherwise mean constant
 * disk traffic. Nothing is ever written on a pointer-move.
 */
@HiltViewModel
class WhiteboardViewModel @Inject constructor(
    private val boardRepository: BoardRepository,
    private val generateNotes: GenerateNotesFromSnapshotUseCase,
    private val explainRegion: ExplainBoardRegionUseCase,
    private val lookupCropStore: LookupCropStore,
    private val safImporter: SafImporter,
    private val exportStore: BoardExportStore,
    private val pdfPageRenderer: PdfPageRenderer,
    private val aiService: NotesAiService,
    inputSettingsStore: InputSettingsStore,
) : ViewModel() {

    /** Input toggles from Settings, applied live to the canvas. */
    val inputSettings: StateFlow<InputSettings> = inputSettingsStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InputSettings(),
    )

    private val _state = MutableStateFlow(WhiteboardUiState())
    val state: StateFlow<WhiteboardUiState> = _state.asStateFlow()

    private val _snapshotPhase = MutableStateFlow<SnapshotPhase?>(null)
    val snapshotPhase: StateFlow<SnapshotPhase?> = _snapshotPhase.asStateFlow()

    private val _lookupState = MutableStateFlow<LookupState?>(null)
    val lookupState: StateFlow<LookupState?> = _lookupState.asStateFlow()

    /** In-flight lookup, so a new request or a dismiss cancels the old one. */
    private var lookupJob: Job? = null

    private val _backgroundState = MutableStateFlow<BackgroundImportState?>(null)
    val backgroundState: StateFlow<BackgroundImportState?> = _backgroundState.asStateFlow()

    /** PDF awaiting a page choice. */
    private var pendingPdf: File? = null

    /** Decoded background for the canvas to draw. */
    private val _backgroundBitmap = MutableStateFlow<Bitmap?>(null)
    val backgroundBitmap: StateFlow<Bitmap?> = _backgroundBitmap.asStateFlow()

    val isAiConfigured: Boolean get() = aiService.isConfigured

    private var saveJob: Job? = null

    /** Set by the canvas once it knows its size; pages are created at board size. */
    private var boardWidthPx = 0
    private var boardHeightPx = 0

    /** Latest content, captured on each change so a flush has something to write. */
    private var pendingStrokes: List<Stroke> = emptyList()
    private var pendingTextBoxes: List<TextBox> = emptyList()
    private var pendingContainers: List<Container> = emptyList()
    private var pendingBackgroundId: String? = null
    private var pendingCamera: CameraState = CameraState()
    private var pendingCanvasStyle: BoardCanvasStyle = BoardCanvasStyle()

    fun onCanvasSized(widthPx: Int, heightPx: Int, onPageReady: (PageContentSnapshot) -> Unit) {
        if (widthPx <= 0 || heightPx <= 0) return
        val firstSizing = boardWidthPx == 0
        boardWidthPx = widthPx
        boardHeightPx = heightPx
        if (firstSizing) restoreOrCreateSession(onPageReady)
    }

    /**
     * Resumes the most recent lesson if there is one, otherwise starts a fresh
     * session. A teacher returning to the board after it slept should find
     * their work, not a blank page.
     */
    private fun restoreOrCreateSession(onPageReady: (PageContentSnapshot) -> Unit) {
        viewModelScope.launch {
            val sessionId = boardRepository.latestSessionId() ?: UUID.randomUUID().toString()
            var pages = boardRepository.getPages(sessionId)

            if (pages.isEmpty()) {
                val page = boardRepository.createPage(sessionId, 0, boardWidthPx, boardHeightPx)
                pages = listOf(page)
            }

            val first = pages.first()
            _state.update {
                it.copy(
                    sessionId = sessionId,
                    pages = pages,
                    currentPageId = first.id,
                    isLoading = false,
                )
            }
            // So a resumed lesson shows its name rather than reading as unsaved.
            _currentLesson.value = boardRepository.getLesson(sessionId)
            loadPage(first.id, onPageReady)
        }
    }

    fun loadPage(pageId: String, onLoaded: (PageContentSnapshot) -> Unit) {
        viewModelScope.launch {
            val content = boardRepository.loadPage(pageId) ?: return@launch
            _state.update { it.copy(currentPageId = pageId) }
            pendingStrokes = content.strokes
            pendingTextBoxes = content.textBoxes
            pendingContainers = content.containers
            pendingBackgroundId = content.background?.id
            pendingCanvasStyle = content.page.canvasStyle
            onLoaded(
                PageContentSnapshot(
                    strokes = content.strokes,
                    textBoxes = content.textBoxes,
                    background = content.background,
                    camera = content.page.camera,
                    containers = content.containers,
                    canvasStyle = content.page.canvasStyle,
                ),
            )
        }
    }

    /**
     * Schedules a save. Called after each completed stroke or erase; repeated
     * calls within the debounce window collapse into one write.
     */
    fun scheduleSave(
        strokes: List<Stroke>,
        textBoxes: List<TextBox>,
        backgroundId: String?,
        camera: Camera? = null,
        containers: List<Container> = emptyList(),
    ) {
        pendingStrokes = strokes
        pendingTextBoxes = textBoxes
        pendingContainers = containers
        pendingBackgroundId = backgroundId
        camera?.let { pendingCamera = CameraState(it.offsetX, it.offsetY, it.zoom) }

        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            writeNow()
        }
    }

    /**
     * Forces an immediate write. Called from onStop so nothing is lost when
     * the board is switched off or the app is backgrounded mid-lesson.
     */
    fun flush() {
        saveJob?.cancel()
        secondarySaveJob?.cancel()
        viewModelScope.launch {
            writeNow()
            // The second pane holds real page content; losing it on a lid
            // close would lose half the lesson.
            writeSecondaryNow()
        }
    }

    private suspend fun writeNow() {
        val current = _state.value
        val pageId = current.currentPageId ?: return
        val sessionId = current.sessionId ?: return
        val index = current.currentIndex.coerceAtLeast(0)

        boardRepository.savePage(
            page = BoardPage(
                id = pageId,
                sessionId = sessionId,
                pageIndex = index,
                widthPx = boardWidthPx,
                heightPx = boardHeightPx,
                backgroundId = pendingBackgroundId,
                canvasStyle = pendingCanvasStyle,
                camera = pendingCamera,
            ),
            strokes = pendingStrokes,
            textBoxes = pendingTextBoxes,
            containers = pendingContainers,
        )
    }

    fun addPage(onPageReady: (PageContentSnapshot) -> Unit) {
        val current = _state.value
        val sessionId = current.sessionId ?: return
        viewModelScope.launch {
            // Persist the page being left before moving off it.
            writeNow()
            val page = boardRepository.createPage(
                sessionId = sessionId,
                pageIndex = current.pages.size,
                widthPx = boardWidthPx,
                heightPx = boardHeightPx,
            )
            _state.update { it.copy(pages = it.pages + page, currentPageId = page.id) }
            pendingStrokes = emptyList()
            pendingTextBoxes = emptyList()
            pendingContainers = emptyList()
            pendingBackgroundId = null
            pendingCamera = CameraState()
            pendingCanvasStyle = BoardCanvasStyle()
            onPageReady(PageContentSnapshot(emptyList(), emptyList(), null, CameraState()))
        }
    }

    fun switchToPage(pageId: String, onPageReady: (PageContentSnapshot) -> Unit) {
        if (pageId == _state.value.currentPageId) return
        viewModelScope.launch {
            writeNow()
            loadPage(pageId, onPageReady)
        }
    }

    fun deleteCurrentPage(onPageReady: (PageContentSnapshot) -> Unit) {
        val current = _state.value
        val pageId = current.currentPageId ?: return
        // Never leave the board with zero pages.
        if (current.pages.size <= 1) return

        viewModelScope.launch {
            saveJob?.cancel()
            boardRepository.deletePage(pageId)
            val remaining = current.pages.filterNot { it.id == pageId }
            val next = remaining.firstOrNull() ?: return@launch
            _state.update { it.copy(pages = remaining, currentPageId = next.id) }
            loadPage(next.id, onPageReady)
        }
    }

    fun onBackgroundChanged(background: BoardBackground?) {
        pendingBackgroundId = background?.id
        if (background == null) {
            _backgroundBitmap.value?.recycle()
            _backgroundBitmap.value = null
        }
    }

    // --- Snapshot -> AI -> notes ------------------------------------------

    /**
     * @param composeBitmap produces the flattened board image. The canvas
     *        owns the layers, so it supplies this rather than the ViewModel
     *        reaching into the UI.
     */
    fun captureAndSummarize(composeBitmap: () -> Bitmap?) {
        if (_snapshotPhase.value != null) return
        _snapshotPhase.value = SnapshotPhase.Capturing

        viewModelScope.launch {
            val bitmap = composeBitmap()
            if (bitmap == null) {
                _snapshotPhase.value = SnapshotPhase.Failed("The board could not be captured.")
                return@launch
            }

            _snapshotPhase.value = SnapshotPhase.Summarizing
            val result = generateNotes(bitmap, _state.value.currentPageId)
            bitmap.recycle()

            _snapshotPhase.value = when (result) {
                is AppResult.Success -> SnapshotPhase.Done(result.data.title)
                is AppResult.Failure -> SnapshotPhase.Failed(result.error.message)
            }
        }
    }

    fun dismissSnapshotDialog() {
        _snapshotPhase.value = null
    }

    // --- Visual lookup (explain a lassoed region) --------------------------

    /**
     * Explains the region the teacher has selected.
     *
     * @param cropRegion renders the selected area. Supplied by the canvas for
     *        the same reason as the snapshot path — the renderer owns the
     *        layers, so the ViewModel never reaches into the UI.
     *
     * The crop is written for sharing REGARDLESS of how the AI call goes.
     * That is what keeps "Search with Lens" available when the model fails,
     * is unconfigured, or the board is offline — the cases where a teacher
     * most needs the fallback.
     */
    fun lookupSelection(cropRegion: () -> Bitmap?) {
        lookupJob?.cancel()
        _lookupState.value = LookupState.Working()

        lookupJob = viewModelScope.launch {
            val bitmap = cropRegion()
            if (bitmap == null) {
                _lookupState.value = LookupState.Failed("That region could not be captured.")
                return@launch
            }

            // Guards the unconfigured path below in the same way as the
            // result publish: dismissing during the crop write must stick.
            

            try {
                // Write the shareable crop first: it is the fallback path and
                // must exist before anything is allowed to fail.
                val shareUri = runCatching { lookupCropStore.writeShareableCrop(bitmap) }.getOrNull()

                if (!explainRegion.isConfigured) {
                    ensureActive()
                    _lookupState.value = LookupState.NotConfigured(shareUri)
                    return@launch
                }

                _lookupState.value = LookupState.Working(shareUri)

                val next = when (val result = explainRegion(bitmap)) {
                    is AppResult.Success -> LookupState.Ready(result.data, shareUri)
                    // The share URI rides along on failure too: offline is
                    // precisely when handing the crop to Lens is the only
                    // thing left that works.
                    is AppResult.Failure -> LookupState.Failed(result.error.message, shareUri)
                }
                // Only publish if this job is still the current one. Without
                // the guard, a lookup cancelled by dismiss or by a second
                // selection resurrects the panel when its request returns.
                ensureActive()
                _lookupState.value = next
            } finally {
                // The use case does not own the bitmap, so it is recycled here
                // on every path including cancellation.
                bitmap.recycle()
            }
        }
    }

    /**
     * Persists a moved/resized/rotated background.
     *
     * Separate from [scheduleSave], which only records WHICH background a page
     * uses. The placement lives on the background row itself, so it needs its
     * own write or a dragged image snaps back on reload.
     */
    fun saveBackgroundPlacement(background: BoardBackground) {
        viewModelScope.launch { boardRepository.saveBackground(background) }
    }

    fun dismissLookup() {
        lookupJob?.cancel()
        lookupJob = null
        _lookupState.value = null
    }

    // --- Background import -------------------------------------------------

    fun openBackgroundSheet() {
        _backgroundState.value = BackgroundImportState(
            hasBackground = _backgroundBitmap.value != null,
        )
    }

    fun dismissBackgroundSheet() {
        _backgroundState.value = null
        pendingPdf = null
    }

    fun onImagePicked(uri: Uri) {
        _backgroundState.value = BackgroundImportState(isBusy = true, busyMessage = "Importing image…")
        viewModelScope.launch {
            when (val result = safImporter.importImage(uri)) {
                is AppResult.Success -> applyBackgroundFile(
                    file = result.data,
                    kind = BackgroundKind.IMAGE,
                    sourcePath = result.data.absolutePath,
                    pdfPageIndex = null,
                )

                is AppResult.Failure ->
                    _backgroundState.value = BackgroundImportState(errorMessage = result.error.message)
            }
        }
    }

    /**
     * Imports a picture and hands back a placed container plus its bitmap.
     *
     * Separate from [onImagePicked], which sets the page BACKGROUND. This is
     * the insert path: the picture becomes a movable object the teacher can
     * place, resize and write on top of, and several can live on one page.
     */
    fun insertImage(
        uri: Uri,
        onReady: (Container, android.graphics.Bitmap) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = safImporter.importImage(uri)) {
                is AppResult.Success -> {
                    val file = result.data
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }
                    if (bitmap == null) {
                        _backgroundState.value =
                            BackgroundImportState(errorMessage = "That image could not be read.")
                        return@launch
                    }
                    onReady(
                        Container(
                            id = UUID.randomUUID().toString(),
                            kind = ContainerKind.IMAGE,
                            // Placed by the caller, which knows the viewport.
                            x = 0f,
                            y = 0f,
                            mediaPath = file.absolutePath,
                        ),
                        bitmap,
                    )
                }

                is AppResult.Failure ->
                    _backgroundState.value = BackgroundImportState(errorMessage = result.error.message)
            }
        }
    }

    /**
     * Imports a video and hands back a placed container plus its poster frame.
     *
     * The container holds the VIDEO path in [Container.mediaPath] while the
     * bitmap handed back is the POSTER — the board draws a still and only
     * opens the video when the teacher plays it. Poster path is derived from
     * the video path rather than stored, so no schema change is needed.
     */
    fun insertVideo(
        uri: Uri,
        onReady: (Container, android.graphics.Bitmap) -> Unit,
    ) {
        _backgroundState.value = BackgroundImportState(isBusy = true, busyMessage = "Importing video…")
        viewModelScope.launch {
            when (val result = safImporter.importVideo(uri)) {
                is AppResult.Success -> {
                    val poster = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(result.data.poster.absolutePath)
                    }
                    if (poster == null) {
                        _backgroundState.value =
                            BackgroundImportState(errorMessage = "That video could not be read.")
                        return@launch
                    }
                    _backgroundState.value = null
                    onReady(
                        Container(
                            id = UUID.randomUUID().toString(),
                            kind = ContainerKind.VIDEO,
                            x = 0f,
                            y = 0f,
                            mediaPath = result.data.video.absolutePath,
                        ),
                        poster,
                    )
                }

                is AppResult.Failure ->
                    _backgroundState.value = BackgroundImportState(errorMessage = result.error.message)
            }
        }
    }

    /**
     * Captures the frame at [positionMs] and hands it back as an IMAGE.
     *
     * IMAGE, not VIDEO: what lands on the board is a still the teacher then
     * annotates with the pen, shapes and geometry tools — so it should behave
     * exactly like an inserted picture, and must not carry a play badge that
     * would reopen the player.
     */
    fun captureVideoFrame(
        videoPath: String,
        positionMs: Int,
        onReady: (Container, android.graphics.Bitmap) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = safImporter.captureFrame(videoPath, positionMs)) {
                is AppResult.Success -> {
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(result.data.absolutePath)
                    }
                    if (bitmap == null) {
                        _backgroundState.value =
                            BackgroundImportState(errorMessage = "That frame could not be read.")
                        return@launch
                    }
                    onReady(
                        Container(
                            id = UUID.randomUUID().toString(),
                            kind = ContainerKind.IMAGE,
                            x = 0f,
                            y = 0f,
                            mediaPath = result.data.absolutePath,
                        ),
                        bitmap,
                    )
                }

                is AppResult.Failure ->
                    _backgroundState.value = BackgroundImportState(errorMessage = result.error.message)
            }
        }
    }

    private val _exportPhase = MutableStateFlow<ExportPhase?>(null)
    val exportPhase: StateFlow<ExportPhase?> = _exportPhase.asStateFlow()

    fun beginExport() {
        _exportPhase.value = ExportPhase.Choosing
    }

    fun dismissExport() {
        _exportPhase.value = null
    }

    /**
     * Saves a rendered selection to shared storage as PNG or PDF.
     *
     * [render] is called on the MAIN thread by the caller's lambda before any
     * IO starts: BoardRenderer's paint and path fields are shared mutable
     * state, and rendering off the UI thread produces intermittently corrupt
     * output that is very hard to trace back.
     */
    fun exportSelection(asPdf: Boolean, render: () -> Bitmap?) {
        val bitmap = render()
        if (bitmap == null) {
            _exportPhase.value = ExportPhase.Failed("There was nothing to save.")
            return
        }
        _exportPhase.value = ExportPhase.Working
        viewModelScope.launch {
            val name = "board_${System.currentTimeMillis()}"
            val result = if (asPdf) {
                exportStore.savePdf(bitmap, name)
            } else {
                exportStore.savePng(bitmap, name)
            }
            bitmap.recycle()
            _exportPhase.value = when (result) {
                is AppResult.Success -> ExportPhase.Done(result.data.displayPath)
                is AppResult.Failure -> ExportPhase.Failed(
                    result.error.message ?: "The file could not be written.",
                )
            }
        }
    }

    /**
     * Downloads an image found on the web and hands it back to be placed.
     *
     * Becomes a plain IMAGE container, so a picture pulled off a search page
     * behaves exactly like one imported from storage — movable, resizable,
     * annotatable, and saved with the lesson.
     */
    fun insertWebImage(
        url: String,
        onReady: (Container, android.graphics.Bitmap) -> Unit,
    ) {
        _backgroundState.value = BackgroundImportState(isBusy = true, busyMessage = "Fetching image…")
        viewModelScope.launch {
            when (val result = safImporter.downloadImage(url)) {
                is AppResult.Success -> {
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(result.data.absolutePath)
                    }
                    if (bitmap == null) {
                        _backgroundState.value =
                            BackgroundImportState(errorMessage = "That image could not be read.")
                        return@launch
                    }
                    _backgroundState.value = null
                    onReady(
                        Container(
                            id = UUID.randomUUID().toString(),
                            kind = ContainerKind.IMAGE,
                            x = 0f,
                            y = 0f,
                            mediaPath = result.data.absolutePath,
                        ),
                        bitmap,
                    )
                }

                is AppResult.Failure ->
                    _backgroundState.value = BackgroundImportState(errorMessage = result.error.message)
            }
        }
    }

    /**
     * Records the page's new paper and writes it straight away.
     *
     * Not debounced like ink: changing the paper is a deliberate, occasional
     * act, and a teacher who sets a grid then closes the lid should not lose it
     * to a pending save.
     */
    fun setCanvasStyle(style: BoardCanvasStyle) {
        pendingCanvasStyle = style
        viewModelScope.launch { writeNow() }
    }

    // --- Split view (second pane) ---

    /**
     * The page shown in the second pane, or null when the board is not split.
     *
     * A SEPARATE page of the same lesson, so both panes are real board pages:
     * ink drawn in either saves to its own page and is there when the lesson
     * is reopened, split or not.
     */
    private val _secondaryPageId = MutableStateFlow<String?>(null)
    val secondaryPageId: StateFlow<String?> = _secondaryPageId.asStateFlow()

    /** Pending content for the second pane, kept apart from the primary's. */
    private var secondaryStrokes: List<Stroke> = emptyList()
    private var secondaryTextBoxes: List<TextBox> = emptyList()
    private var secondaryContainers: List<Container> = emptyList()
    private var secondaryBackgroundId: String? = null
    private var secondaryCamera: CameraState = CameraState()
    private var secondaryCanvasStyle: BoardCanvasStyle = BoardCanvasStyle()
    private var secondarySaveJob: Job? = null

    /**
     * Opens the second pane on [pageId], or on a sensible neighbour.
     *
     * Defaults to the NEXT page so splitting immediately shows two different
     * things; falls back to the previous one on the last page, and creates a
     * page when the lesson has only one — a split showing the same page twice
     * would look broken.
     */
    fun openSecondaryPane(pageId: String? = null, onLoaded: (PageContentSnapshot) -> Unit) {
        viewModelScope.launch {
            val current = _state.value
            val explicit = pageId
            if (explicit != null) {
                loadSecondary(explicit, onLoaded)
                return@launch
            }

            val index = current.currentIndex
            val neighbour = current.pages.getOrNull(index + 1) ?: current.pages.getOrNull(index - 1)
            if (neighbour != null) {
                loadSecondary(neighbour.id, onLoaded)
                return@launch
            }

            val sessionId = current.sessionId ?: return@launch
            val page = boardRepository.createPage(
                sessionId = sessionId,
                pageIndex = current.pages.size,
                widthPx = boardWidthPx,
                heightPx = boardHeightPx,
            )
            _state.update { it.copy(pages = it.pages + page) }
            loadSecondary(page.id, onLoaded)
        }
    }

    /** Closes the second pane, flushing whatever it holds. */
    fun closeSecondaryPane() {
        viewModelScope.launch {
            writeSecondaryNow()
            _secondaryPageId.value = null
        }
    }

    fun loadSecondaryPage(pageId: String, onLoaded: (PageContentSnapshot) -> Unit) {
        viewModelScope.launch {
            writeSecondaryNow()
            loadSecondary(pageId, onLoaded)
        }
    }

    private suspend fun loadSecondary(pageId: String, onLoaded: (PageContentSnapshot) -> Unit) {
        val content = boardRepository.loadPage(pageId) ?: return
        _secondaryPageId.value = pageId
        secondaryStrokes = content.strokes
        secondaryTextBoxes = content.textBoxes
        secondaryContainers = content.containers
        secondaryBackgroundId = content.background?.id
        secondaryCanvasStyle = content.page.canvasStyle
        onLoaded(
            PageContentSnapshot(
                strokes = content.strokes,
                textBoxes = content.textBoxes,
                background = content.background,
                camera = content.page.camera,
                containers = content.containers,
                canvasStyle = content.page.canvasStyle,
            ),
        )
    }

    /** Debounced save for the second pane, mirroring [scheduleSave]. */
    fun scheduleSecondarySave(
        strokes: List<Stroke>,
        textBoxes: List<TextBox>,
        backgroundId: String?,
        camera: Camera? = null,
        containers: List<Container> = emptyList(),
        canvasStyle: BoardCanvasStyle? = null,
    ) {
        secondaryStrokes = strokes
        secondaryTextBoxes = textBoxes
        secondaryContainers = containers
        secondaryBackgroundId = backgroundId
        camera?.let { secondaryCamera = CameraState(it.offsetX, it.offsetY, it.zoom) }
        canvasStyle?.let { secondaryCanvasStyle = it }

        secondarySaveJob?.cancel()
        secondarySaveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            writeSecondaryNow()
        }
    }

    private suspend fun writeSecondaryNow() {
        val pageId = _secondaryPageId.value ?: return
        val sessionId = _state.value.sessionId ?: return
        // Index comes from the page list, not the primary's index: the second
        // pane is a different page and must not overwrite its neighbour's slot.
        val index = _state.value.pages.indexOfFirst { it.id == pageId }.coerceAtLeast(0)

        boardRepository.savePage(
            page = BoardPage(
                id = pageId,
                sessionId = sessionId,
                pageIndex = index,
                widthPx = boardWidthPx,
                heightPx = boardHeightPx,
                backgroundId = secondaryBackgroundId,
                canvasStyle = secondaryCanvasStyle,
                camera = secondaryCamera,
            ),
            strokes = secondaryStrokes,
            textBoxes = secondaryTextBoxes,
            containers = secondaryContainers,
        )
    }

    // --- Named lessons ---

    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons: StateFlow<List<Lesson>> = _lessons.asStateFlow()

    /** The lesson the current session is saved as, or null while unsaved. */
    private val _currentLesson = MutableStateFlow<Lesson?>(null)
    val currentLesson: StateFlow<Lesson?> = _currentLesson.asStateFlow()

    fun refreshLessons() {
        viewModelScope.launch { _lessons.value = boardRepository.getLessons() }
    }

    /**
     * Names the current session, or renames it if already saved.
     *
     * Flushes pending ink FIRST: a teacher who draws and immediately taps Save
     * must not have that stroke sitting in the debounce window while the
     * lesson is written.
     */
    fun saveLesson(name: String) {
        val sessionId = _state.value.sessionId ?: return
        viewModelScope.launch {
            writeNow()
            _currentLesson.value = boardRepository.saveLesson(sessionId, name.trim())
            _lessons.value = boardRepository.getLessons()
        }
    }

    /**
     * Copies the session under a new name and switches to the copy.
     *
     * The original keeps its own name and content — that is the whole point of
     * Save as, and it is why this duplicates rather than renames.
     */
    fun saveLessonAs(name: String, onPageReady: (PageContentSnapshot) -> Unit) {
        val sessionId = _state.value.sessionId ?: return
        viewModelScope.launch {
            writeNow()
            val newSessionId = boardRepository.duplicateSession(sessionId, name.trim())
            openSessionInternal(newSessionId, onPageReady)
            _lessons.value = boardRepository.getLessons()
        }
    }

    /** Opens a saved lesson, after flushing whatever is on screen now. */
    fun openLesson(sessionId: String, onPageReady: (PageContentSnapshot) -> Unit) {
        if (sessionId == _state.value.sessionId) return
        viewModelScope.launch {
            writeNow()
            openSessionInternal(sessionId, onPageReady)
        }
    }

    /**
     * Starts a blank unsaved session.
     *
     * The work being left is flushed, not discarded: if it was a saved lesson
     * it stays saved, and if it was not, it remains reachable as the previous
     * session until something else claims "latest".
     */
    fun newLesson(onPageReady: (PageContentSnapshot) -> Unit) {
        viewModelScope.launch {
            writeNow()
            val sessionId = UUID.randomUUID().toString()
            val page = boardRepository.createPage(sessionId, 0, boardWidthPx, boardHeightPx)
            _state.update {
                it.copy(sessionId = sessionId, pages = listOf(page), currentPageId = page.id)
            }
            _currentLesson.value = null
            resetPendingContent()
            onPageReady(PageContentSnapshot(emptyList(), emptyList(), null, CameraState()))
        }
    }

    fun deleteLesson(sessionId: String) {
        viewModelScope.launch {
            boardRepository.deleteLesson(sessionId)
            if (sessionId == _state.value.sessionId) _currentLesson.value = null
            _lessons.value = boardRepository.getLessons()
        }
    }

    private suspend fun openSessionInternal(
        sessionId: String,
        onPageReady: (PageContentSnapshot) -> Unit,
    ) {
        val pages = boardRepository.getPages(sessionId)
        val first = pages.firstOrNull() ?: return
        _state.update {
            it.copy(sessionId = sessionId, pages = pages, currentPageId = first.id)
        }
        _currentLesson.value = boardRepository.getLesson(sessionId)
        loadPage(first.id, onPageReady)
    }

    /** Clears the pending-write set, so a new page cannot inherit old content. */
    private fun resetPendingContent() {
        pendingStrokes = emptyList()
        pendingTextBoxes = emptyList()
        pendingContainers = emptyList()
        pendingBackgroundId = null
        pendingCamera = CameraState()
        pendingCanvasStyle = BoardCanvasStyle()
    }

    /** Reports a media file that has gone missing since it was placed. */
    fun reportMediaMissing() {
        _backgroundState.value = BackgroundImportState(
            errorMessage = "That video is no longer on this board's storage.",
        )
    }

    /** Decodes the media for every IMAGE container on a freshly loaded page. */
    fun loadMediaFor(
        containers: List<Container>,
        onDecoded: (String, android.graphics.Bitmap) -> Unit,
    ) {
        val media = containers.filter { it.kind.isMedia && it.mediaPath != null }
        if (media.isEmpty()) return
        viewModelScope.launch {
            media.forEach { container ->
                // A VIDEO container's mediaPath is the VIDEO; what the board
                // draws is the poster frame saved beside it at import.
                val path = if (container.kind == ContainerKind.VIDEO) {
                    posterPathFor(container.mediaPath!!)
                } else {
                    container.mediaPath
                }
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(path)
                }
                // A missing file leaves the container drawn as an outline
                // rather than removing what the teacher placed.
                if (bitmap != null) onDecoded(container.id, bitmap)
            }
        }
    }

    fun onPdfPicked(uri: Uri) {
        _backgroundState.value = BackgroundImportState(isBusy = true, busyMessage = "Reading PDF…")
        viewModelScope.launch {
            when (val copied = safImporter.importPdf(uri)) {
                is AppResult.Success -> {
                    pendingPdf = copied.data
                    when (val count = pdfPageRenderer.pageCount(copied.data)) {
                        is AppResult.Success ->
                            _backgroundState.value = BackgroundImportState(pdfPageCount = count.data)

                        is AppResult.Failure ->
                            _backgroundState.value =
                                BackgroundImportState(errorMessage = count.error.message)
                    }
                }

                is AppResult.Failure ->
                    _backgroundState.value = BackgroundImportState(errorMessage = copied.error.message)
            }
        }
    }

    /**
     * Imports a whole PDF as board pages — one PDF page per board page.
     *
     * A teacher importing a textbook or a test paper wants the document, not
     * one page of it, and the board already has a pager built for exactly this
     * shape. Mapping onto board pages means annotation, undo and persistence
     * are per-page for free, and page 1's ink cannot bleed onto page 2.
     *
     * Nothing is RENDERED here. Each page gets a background record carrying
     * its source path and page index with an empty [BoardBackground.renderedPath];
     * the bitmap is produced by [loadBackgroundBitmap] the first time the page
     * is opened. A 200-page book therefore imports as fast as a 2-page one,
     * and pages the teacher never reaches cost nothing.
     */
    fun importPdfAsPages(uri: Uri, onPageReady: (PageContentSnapshot) -> Unit) {
        _backgroundState.value = BackgroundImportState(isBusy = true, busyMessage = "Reading PDF…")
        viewModelScope.launch {
            val copied = when (val result = safImporter.importPdf(uri)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> {
                    _backgroundState.value = BackgroundImportState(errorMessage = result.error.message)
                    return@launch
                }
            }

            val pageCount = when (val count = pdfPageRenderer.pageCount(copied)) {
                is AppResult.Success -> count.data
                is AppResult.Failure -> {
                    _backgroundState.value = BackgroundImportState(errorMessage = count.error.message)
                    return@launch
                }
            }

            val sessionId = _state.value.sessionId
            if (sessionId == null || pageCount <= 0) {
                _backgroundState.value =
                    BackgroundImportState(errorMessage = "That PDF has no pages.")
                return@launch
            }

            // Persist the page being left before adding any.
            writeNow()

            // A lone untouched page is REPLACED rather than left in front of
            // the document: importing a book should not leave the teacher
            // looking at an empty page 1 of 6 and having to page past it.
            val pagesBefore = _state.value.pages
            val blankToDrop = pagesBefore.singleOrNull()?.takeIf {
                it.backgroundId == null &&
                    pendingStrokes.isEmpty() &&
                    pendingTextBoxes.isEmpty() &&
                    pendingContainers.isEmpty()
            }

            val startIndex = if (blankToDrop != null) 0 else pagesBefore.size
            val created = ArrayList<BoardPage>(pageCount)

            for (index in 0 until pageCount) {
                val page = boardRepository.createPage(
                    sessionId = sessionId,
                    pageIndex = startIndex + index,
                    widthPx = boardWidthPx,
                    heightPx = boardHeightPx,
                )
                val background = BoardBackground(
                    id = UUID.randomUUID().toString(),
                    kind = BackgroundKind.PDF_PAGE,
                    sourcePath = copied.absolutePath,
                    pdfPageIndex = index,
                    // Filled in on first view; see loadBackgroundBitmap.
                    renderedPath = "",
                )
                boardRepository.saveBackground(background)
                boardRepository.savePage(
                    page = page.copy(backgroundId = background.id),
                    strokes = emptyList(),
                    textBoxes = emptyList(),
                )
                created += page.copy(backgroundId = background.id)
            }

            // Dropped only AFTER the document's pages exist, so a failure
            // partway through can never leave the board with zero pages.
            blankToDrop?.let { boardRepository.deletePage(it.id) }

            _state.update {
                val kept = if (blankToDrop == null) it.pages else it.pages - blankToDrop
                it.copy(pages = kept + created)
            }
            _backgroundState.value = null
            pendingPdf = null

            created.firstOrNull()?.let { first ->
                loadPage(first.id, onPageReady)
            }
        }
    }

    fun onPdfPageChosen(pageIndex: Int) {
        val pdf = pendingPdf ?: return
        _backgroundState.value = BackgroundImportState(
            isBusy = true,
            busyMessage = "Rendering page ${pageIndex + 1}…",
        )
        viewModelScope.launch {
            when (val result = pdfPageRenderer.renderPageToFile(pdf, pageIndex)) {
                is AppResult.Success -> applyBackgroundFile(
                    file = result.data,
                    kind = BackgroundKind.PDF_PAGE,
                    sourcePath = pdf.absolutePath,
                    pdfPageIndex = pageIndex,
                )

                is AppResult.Failure ->
                    _backgroundState.value = BackgroundImportState(errorMessage = result.error.message)
            }
        }
    }

    fun removeBackground(onRemoved: (BoardBackground?) -> Unit) {
        _backgroundBitmap.value?.recycle()
        _backgroundBitmap.value = null
        pendingBackgroundId = null
        _backgroundState.value = null
        onRemoved(null)
    }

    private var onBackgroundApplied: ((BoardBackground) -> Unit)? = null

    fun setBackgroundAppliedListener(listener: (BoardBackground) -> Unit) {
        onBackgroundApplied = listener
    }

    private suspend fun applyBackgroundFile(
        file: File,
        kind: BackgroundKind,
        sourcePath: String,
        pdfPageIndex: Int?,
    ) {
        val background = BoardBackground(
            id = UUID.randomUUID().toString(),
            kind = kind,
            sourcePath = sourcePath,
            pdfPageIndex = pdfPageIndex,
            renderedPath = file.absolutePath,
        )
        boardRepository.saveBackground(background)

        val bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(file.absolutePath)
        }
        _backgroundBitmap.value?.recycle()
        _backgroundBitmap.value = bitmap
        pendingBackgroundId = background.id
        _backgroundState.value = null
        pendingPdf = null
        onBackgroundApplied?.invoke(background)
        writeNow()
    }

    /**
     * Adopts a background prepared elsewhere — currently the material
     * viewer's "Annotate on board". Only the id crosses the screen boundary;
     * the record is already in the database.
     */
    fun adoptBackground(backgroundId: String, onApplied: (BoardBackground) -> Unit) {
        viewModelScope.launch {
            val background = boardRepository.getBackground(backgroundId) ?: return@launch

            // The canvas may not have been sized yet, in which case no page
            // exists and a save here would be dropped — or worse, the page
            // load that follows would overwrite this background. Wait for the
            // session to come up first.
            var waited = 0
            while (_state.value.currentPageId == null && waited < ADOPT_TIMEOUT_MS) {
                delay(ADOPT_POLL_MS)
                waited += ADOPT_POLL_MS.toInt()
            }

            val bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(background.renderedPath)
            }
            _backgroundBitmap.value?.recycle()
            _backgroundBitmap.value = bitmap
            pendingBackgroundId = background.id
            onApplied(background)
            writeNow()
        }
    }

    /** Restores a saved background bitmap when a page loads. */
    fun loadBackgroundBitmap(background: BoardBackground?) {
        if (background == null) {
            _backgroundBitmap.value?.recycle()
            _backgroundBitmap.value = null
            return
        }
        viewModelScope.launch {
            // An imported PDF page has no rendered file until the teacher
            // first opens it — that is what makes importing a 200-page book
            // instant. Render it now, then carry on as normal. The renderer
            // caches by file and page index, so flipping back is free.
            val ready = ensureRendered(background) ?: return@launch

            val bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(ready.renderedPath)
            }
            // A page the teacher is no longer on must not steal the bitmap:
            // rendering is slow enough that they can page past it first.
            if (pendingBackgroundId != ready.id) {
                bitmap?.recycle()
                return@launch
            }
            _backgroundBitmap.value?.recycle()
            _backgroundBitmap.value = bitmap
        }
    }

    /**
     * Returns [background] with a real [BoardBackground.renderedPath],
     * rendering the PDF page and saving the path if this is its first view.
     */
    private suspend fun ensureRendered(background: BoardBackground): BoardBackground? {
        if (background.renderedPath.isNotEmpty()) return background
        if (background.kind != BackgroundKind.PDF_PAGE) return null

        val pageIndex = background.pdfPageIndex ?: return null
        val source = File(background.sourcePath)
        if (!source.exists()) return null

        return when (val result = pdfPageRenderer.renderPageToFile(source, pageIndex)) {
            is AppResult.Success -> {
                val filled = background.copy(renderedPath = result.data.absolutePath)
                // Persisted so the path survives a restart; the rendered file
                // is cached on disk either way, so this only saves a lookup.
                boardRepository.saveBackground(filled)
                filled
            }

            is AppResult.Failure -> null
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveJob?.cancel()
        _backgroundBitmap.value?.recycle()
    }

    companion object {
        /**
         * Long enough that a burst of quick strokes is one write, short enough
         * that an unexpected power-off loses at most a second of ink.
         */
        const val SAVE_DEBOUNCE_MS = 1_000L

        /** How long adoptBackground waits for the board session to exist. */
        private const val ADOPT_TIMEOUT_MS = 3_000
        private const val ADOPT_POLL_MS = 50L
    }
}

data class PageContentSnapshot(
    val strokes: List<Stroke>,
    val textBoxes: List<TextBox>,
    val background: BoardBackground?,
    /** Saved pan/zoom, so a lesson resumes where the teacher left it. */
    val camera: CameraState? = null,
    val containers: List<Container> = emptyList(),
    /** The page's paper, so a reopened lesson looks as it was left. */
    val canvasStyle: BoardCanvasStyle = BoardCanvasStyle(),
)
