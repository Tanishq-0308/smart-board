package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.domain.model.BackgroundKind
import com.smartboard.teach.domain.model.BoardBackground
import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.ContainerCell
import com.smartboard.teach.domain.model.ContainerKind
import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.TextBox
import com.smartboard.teach.feature.notes.SnapshotDialog
import com.smartboard.teach.feature.shell.LocalHideClock
import com.smartboard.teach.feature.shell.LocalOpenBoardMenu
import com.smartboard.teach.feature.whiteboard.container.MindmapChrome
import com.smartboard.teach.feature.whiteboard.container.MindmapLayout
import com.smartboard.teach.feature.whiteboard.container.TableGrid
import com.smartboard.teach.feature.whiteboard.instruments.Instrument
import com.smartboard.teach.feature.whiteboard.instruments.InstrumentGeometry
import com.smartboard.teach.feature.whiteboard.instruments.InstrumentKind
import com.smartboard.teach.feature.whiteboard.instruments.InstrumentLayer

@Composable
fun WhiteboardScreen(
    modifier: Modifier = Modifier,
    /** Set when arriving from "Annotate on board" in the material viewer. */
    pendingBackgroundId: String? = null,
    onOpenNotes: () -> Unit = {},
    viewModel: WhiteboardViewModel = hiltViewModel(),
) {
    val state = remember { BoardState() }
    val renderer = remember { BoardRenderer() }
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val snapshotPhase by viewModel.snapshotPhase.collectAsStateWithLifecycle()
    val lookupState by viewModel.lookupState.collectAsStateWithLifecycle()
    val backgroundState by viewModel.backgroundState.collectAsStateWithLifecycle()
    val exportPhase by viewModel.exportPhase.collectAsStateWithLifecycle()
    val lessons by viewModel.lessons.collectAsStateWithLifecycle()
    val currentLesson by viewModel.currentLesson.collectAsStateWithLifecycle()
    val backgroundBitmap by viewModel.backgroundBitmap.collectAsStateWithLifecycle()
    val inputSettings by viewModel.inputSettings.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val context = LocalContext.current
    val dimens = SmartBoardTheme.dimens
    val openMenu = LocalOpenBoardMenu.current

    // The instruments measure in real centimetres, so they need the panel's
    // physical density rather than its logical one.
    LaunchedEffect(Unit) {
        InstrumentGeometry.setDisplayDensity(context.resources.displayMetrics.xdpi)
    }
    var debugInfo by remember { mutableStateOf<PointerDebugInfo?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onImagePicked) }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onPdfPicked) }

    fun persist() {
        viewModel.scheduleSave(
            strokes = state.strokes.toList(),
            textBoxes = state.textBoxes.toList(),
            backgroundId = state.background?.id,
            camera = state.camera,
            containers = state.containers.toList(),
        )
    }

    /** Re-rasterizes the viewport cache once a pan or zoom has finished. */
    fun settleCamera() {
        renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
        state.markCommittedDirty()
        persist()
    }

    fun applySnapshot(snapshot: PageContentSnapshot) {
        applyLoadedPage(
            state, renderer,
            snapshot.strokes, snapshot.textBoxes, snapshot.background, snapshot.containers,
        )
        snapshot.camera?.let { saved ->
            state.camera.restore(saved.offsetX, saved.offsetY, saved.zoom)
        }
        state.canvasStyle = snapshot.canvasStyle
        state.clearMedia()
        viewModel.loadMediaFor(snapshot.containers) { id, bitmap ->
            state.putMedia(id, bitmap)
            renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
        }
        renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
        state.markCommittedDirty()
        viewModel.loadBackgroundBitmap(snapshot.background)
    }

    // Imports a whole PDF as board pages, rather than setting one page as a
    // backdrop — a teacher bringing in a textbook or test paper wants the
    // document. The backdrop path still exists, from the board menu.
    val insertPdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importPdfAsPages(it, ::applySnapshot) }
    }

    // The second pane has its OWN state and renderer: they hold a page's
    // strokes, camera and viewport cache, and sharing either would make both
    // panes show the same page at the same pan.
    val secondaryState = remember { BoardState() }
    val secondaryRenderer = remember { BoardRenderer() }
    val secondaryPageId by viewModel.secondaryPageId.collectAsStateWithLifecycle()
    val isSplit = secondaryPageId != null

    // Tool selection lives on the TOOLBAR, which only knows the primary
    // state. Mirroring it means the pen a teacher picks writes in whichever
    // pane they touch, rather than the second pane being stuck on its own
    // defaults with no control that reaches it.
    if (isSplit) {
        secondaryState.tool = state.tool
        secondaryState.mode = state.mode
        secondaryState.penColor = state.penColor
        secondaryState.penWidth = state.penWidth
        secondaryState.highlighterColor = state.highlighterColor
        secondaryState.highlighterWidth = state.highlighterWidth
        secondaryState.eraserScreenRadius = state.eraserScreenRadius
        secondaryState.stylusOnlyMode = state.stylusOnlyMode
        secondaryState.honourEraserButton = state.honourEraserButton
    }

    /** Whether the lesson (New/Open/Save) menu is open. */
    var showLessonMenu by remember { mutableStateOf(false) }

    /** Whether the background settings panel is open. */
    var showBackgroundSettings by remember { mutableStateOf(false) }

    /** Whether the web search pane is docked. */
    var showWebSearch by remember { mutableStateOf(false) }

    // Right-edge chrome shifts left by the pane's width while it is docked,
    // rather than sitting under it. The pane is an overlay, so nothing moves
    // it out of their way automatically.
    val rightInset = if (showWebSearch) WEB_PANE_WIDTH else 0.dp

    // The clock is drawn above this screen, so it would paint over the pane's
    // header however opaque the pane is; it has to stand down instead.
    val hideClock = LocalHideClock.current
    DisposableEffect(showWebSearch) {
        hideClock.value = showWebSearch
        onDispose { hideClock.value = false }
    }

    /** Whether the lesson timer is on the board. */
    var showTimer by remember { mutableStateOf(false) }

    // The video currently open in the full-screen player, or null.
    var playingVideoPath by remember { mutableStateOf<String?>(null) }

    /** World region an in-progress export covers, captured when it began. */
    var exportBounds by remember { mutableStateOf<FloatArray?>(null) }

    /** Places a media container at the viewport centre, fitted to the board. */
    fun placeMedia(container: Container, bitmap: android.graphics.Bitmap) {
        // A 4000px photo or a 1080p frame dropped at native size would fill
        // the page and hide the lesson, so both are fitted to a fraction of
        // the board with their aspect ratio preserved.
        val maxW = state.viewportWidth / state.camera.zoom * 0.45f
        val maxH = state.viewportHeight / state.camera.zoom * 0.45f
        val scale = minOf(maxW / bitmap.width, maxH / bitmap.height, 1f)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val cx = state.camera.screenToWorldX(state.viewportWidth / 2f)
        val cy = state.camera.screenToWorldY(state.viewportHeight / 2f)

        val placed = container.copy(
            x = cx - w / 2f,
            y = cy - h / 2f,
            cells = listOf(
                ContainerCell(
                    left = cx - w / 2f,
                    top = cy - h / 2f,
                    right = cx + w / 2f,
                    bottom = cy + h / 2f,
                ),
            ),
        )
        state.containers.add(placed)
        state.putMedia(placed.id, bitmap)
        state.history.record(BoardCommand.AddContainer(placed))
        state.refreshHistoryFlags()
        renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
        state.markCommittedDirty()
        persist()
    }

    val insertVideoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.insertVideo(it, ::placeMedia) }
    }

    val insertImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.insertImage(it, ::placeMedia) }
    }

    // A freshly IMPORTED background (image or PDF page) has to reach
    // state.background, not just the drawn bitmap. Without this the record
    // stayed null, so the image rendered but could never be hit-tested,
    // selected, moved or resized.
    LaunchedEffect(Unit) {
        viewModel.setBackgroundAppliedListener { background ->
            val before = state.background
            state.background = background
            state.history.record(BoardCommand.SetBackground(before, background))
            state.refreshHistoryFlags()
        }
    }

    // Arriving from "Annotate on board": adopt the PDF page the material
    // viewer prepared. Applied once per id.
    LaunchedEffect(pendingBackgroundId) {
        if (pendingBackgroundId != null) {
            viewModel.adoptBackground(pendingBackgroundId) { background ->
                val before = state.background
                state.background = background
                state.history.record(BoardCommand.SetBackground(before, background))
                state.refreshHistoryFlags()
            }
        }
    }

    // Settings toggles feed the canvas live, so an installer can correct a
    // misbehaving pen stack without a rebuild.
    LaunchedEffect(inputSettings) {
        state.stylusOnlyMode = inputSettings.stylusOnlyMode
        state.pressureSensitivity = inputSettings.pressureSensitivity
        state.honourEraserButton = inputSettings.honourEraserButton
        state.showPointerDebug = inputSettings.showPointerDebug
        state.shapeRecognition = inputSettings.shapeRecognition
    }

    // Force a write when the board is backgrounded or switched off.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { renderer.release() }
    }

    // Canvas first and FULL-BLEED; every control floats on top of it. No
    // chrome reserves layout space, so the board gets the whole window.
    Box(modifier.fillMaxSize()) {
        // The canvas needs the decoded size to hit-test and bound the
        // background; it lives in the ViewModel, so hand it over here.
        LaunchedEffect(backgroundBitmap) {
            state.backgroundWidthPx = backgroundBitmap?.width?.toFloat() ?: 0f
            state.backgroundHeightPx = backgroundBitmap?.height?.toFloat() ?: 0f

            // Fit an imported page to the board the first time it is shown.
            //
            // PDF pages render up to 2048px on the long edge, well past the
            // panel's height, so a freshly imported sheet would hang off the
            // board and a teacher would have to pinch every page into view.
            // Only when scale is still 1 — reset on a page the teacher has
            // already sized themselves would fight them.
            val bitmap = backgroundBitmap
            val bg = state.background
            if (bitmap != null && bg != null && bg.scale == 1f &&
                bg.kind == BackgroundKind.PDF_PAGE &&
                state.viewportWidth > 0f && state.viewportHeight > 0f
            ) {
                // Measured in WORLD units, so a page opened at a restored
                // zoom still fits the glass rather than the world rect that
                // happens to match it at 100%.
                val worldW = state.viewportWidth / state.camera.zoom
                val worldH = state.viewportHeight / state.camera.zoom
                val fit = minOf(worldW / bitmap.width, worldH / bitmap.height)
                if (fit < 1f) {
                    val fitted = bg.copy(
                        scale = fit,
                        // Centred on the board, so the sheet sits where the
                        // teacher is looking rather than in a corner.
                        x = state.camera.screenToWorldX(0f) +
                            (worldW - bitmap.width * fit) / 2f,
                        y = state.camera.screenToWorldY(0f) +
                            (worldH - bitmap.height * fit) / 2f,
                    )
                    state.background = fitted
                    viewModel.saveBackgroundPlacement(fitted)
                    state.markCommittedDirty()
                }
            }
        }

        BoardCanvas(
            // Half width when split, so the primary pane's pointer handling
            // and viewport cache match what it actually occupies. Leaving it
            // full width would put its ink under the second pane.
            modifier = if (isSplit) Modifier.fillMaxWidth(0.5f) else Modifier,
            state = state,
            renderer = renderer,
            backgroundBitmap = backgroundBitmap,
            onSized = { w, h ->
                state.viewportWidth = w.toFloat()
                state.viewportHeight = h.toFloat()
                viewModel.onCanvasSized(w, h, ::applySnapshot)
            },
            onStrokeFinished = { drawn ->
                // Snap rough freehand to a clean shape when confident.
                // The SUBSTITUTED stroke is what gets recorded in history, so
                // undo removes the shape in one step rather than revealing
                // the original ink underneath — a teacher who did not want
                // the snap presses undo once and the board is clear.
                val stroke = maybeSnapToShape(state, drawn)
                state.strokes.add(stroke)
                renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
                state.markCommittedDirty()
                state.history.record(BoardCommand.AddStroke(stroke))
                state.refreshHistoryFlags()
                persist()
            },
            onStrokesErased = { erased ->
                state.history.record(BoardCommand.EraseStrokes(erased))
                state.refreshHistoryFlags()
                renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
                state.markCommittedDirty()
                persist()
            },
            onSelectionMoved = { strokesBefore, boxesBefore ->
                // One undo step per completed gesture, not per frame.
                state.history.record(
                    BoardCommand.TransformSelection(
                        strokesBefore = strokesBefore,
                        strokesAfter = state.selectedStrokes(),
                        boxesBefore = boxesBefore,
                        boxesAfter = state.selectedTextBoxes(),
                    ),
                )
                state.refreshHistoryFlags()
                // The placement lives on the background row, not the page.
                state.background?.takeIf { state.backgroundSelected }
                    ?.let(viewModel::saveBackgroundPlacement)
                persist()
            },
            onCameraSettled = ::settleCamera,
            onPlayVideo = { path ->
                // A file deleted outside the app leaves the container on the
                // board; opening it would show a black screen with no
                // explanation, so say what happened instead.
                if (videoExists(path)) {
                    playingVideoPath = path
                } else {
                    viewModel.reportMediaMissing()
                }
            },
            onPointerDebug = if (state.showPointerDebug) {
                { info -> debugInfo = info }
            } else {
                null
            },
        )

        if (isSplit) {
            SecondaryPane(
                state = secondaryState,
                renderer = secondaryRenderer,
                onPersist = {
                    viewModel.scheduleSecondarySave(
                        strokes = secondaryState.strokes.toList(),
                        textBoxes = secondaryState.textBoxes.toList(),
                        backgroundId = secondaryState.background?.id,
                        camera = secondaryState.camera,
                        containers = secondaryState.containers.toList(),
                        canvasStyle = secondaryState.canvasStyle,
                    )
                },
                pages = uiState.pages,
                currentPageId = secondaryPageId,
                onSelectPage = { pageId ->
                    viewModel.loadSecondaryPage(pageId) { snapshot ->
                        applyToPane(secondaryState, secondaryRenderer, snapshot)
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        // Text boxes sit above the ink so they stay selectable and editable;
        // they are never rasterized into the cache.
        TextBoxLayer(
            state = state,
            isPlacementMode = state.mode == BoardMode.TextPlacement,
            onPlacementConsumed = { state.mode = BoardMode.Draw },
            onChanged = { persist() },
        )

        // Instruments sit above the ink they rule, and below the chrome.
        InstrumentLayer(state = state)

        if (showLessonMenu) {
            LessonMenu(
                currentLesson = currentLesson,
                lessons = lessons,
                onNew = { viewModel.newLesson(::applySnapshot) },
                onOpen = { viewModel.openLesson(it, ::applySnapshot) },
                onSave = { viewModel.saveLesson(it) },
                onSaveAs = { viewModel.saveLessonAs(it, ::applySnapshot) },
                onDelete = { viewModel.deleteLesson(it) },
                onClose = { showLessonMenu = false },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = dimens.gutter),
            )
        }

        if (showBackgroundSettings) {
            BackgroundSettingsPanel(
                style = state.canvasStyle,
                onStyleChanged = { updated ->
                    state.canvasStyle = updated
                    // The grid is drawn outside the renderer cache, so the
                    // canvas only needs a repaint — no re-rasterize.
                    state.markCommittedDirty()
                    viewModel.setCanvasStyle(updated)
                },
                onClose = { showBackgroundSettings = false },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = dimens.gutter),
            )
        }

        // Docked on the RIGHT EDGE as an overlay rather than taking width from
        // the canvas: resizing the viewport would invalidate the render cache
        // and re-rasterize the whole board every time the pane opens or shuts.
        // The board keeps its full size behind it and stays fully drawable.
        if (showWebSearch) {
            WebSearchPane(
                onClose = { showWebSearch = false },
                onImagePicked = { url -> viewModel.insertWebImage(url, ::placeMedia) },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        // Top-centre by default, clear of the toolbar and the page strip;
        // the teacher drags it wherever the lesson needs it.
        if (showTimer) {
            TimerPanel(
                onClose = { showTimer = false },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = dimens.gutter),
            )
        }

        MindmapChrome(
            state = state,
            onAddChild = { index ->
                editMindmap(state, renderer) { MindmapLayout.addChild(it, index) }
                persist()
            },
            onAddSibling = { index, _ ->
                // Above/below is decided by the reflow, not by the button: the
                // layout keeps siblings evenly spread around their parent, and
                // honouring the side would mean fighting it every time.
                editMindmap(state, renderer) { MindmapLayout.addSibling(it, index) }
                persist()
            },
            onDeleteNode = { index ->
                deleteMindmapNode(state, renderer, index)
                persist()
            },
        )

        ToolPalette(
            state = state,
            onUndo = {
                performUndo(state, renderer)
                persist()
            },
            onRedo = {
                performRedo(state, renderer)
                persist()
            },
            onClear = {
                performClear(state, renderer)
                viewModel.onBackgroundChanged(null)
                persist()
            },
            onSnapshot = {
                viewModel.captureAndSummarize {
                    // The renderer owns every layer, so the flattened export is
                    // composed here rather than screenshotting the View. On an
                    // infinite canvas this exports CONTENT bounds, not the
                    // viewport — a snapshot should capture the whole lesson.
                    renderer.exportBitmap(
                        strokes = state.strokes.toList(),
                        textBoxes = state.textBoxes.map { box ->
                            TextBoxRender(
                                x = box.x,
                                y = box.y,
                                text = box.text,
                                colorArgb = box.colorArgb,
                                fontSizePx = with(density) { box.fontSizeSp.sp.toPx() },
                            )
                        },
                        // Background deliberately OMITTED. An imported photo or
                        // worksheet is reference material the teacher wrote
                        // ON TOP of, not lesson content they produced. Flattening
                        // it in made the AI summarise the source document
                        // instead of the teaching, so notes for a page annotated
                        // over a textbook scan came back describing the scan.
                        background = null,
                        containers = state.containers.toList(),
                    )
                }
            },
            onImportBackground = viewModel::openBackgroundSheet,
            onInsertPdf = { insertPdfPicker.launch(arrayOf("application/pdf")) },
            onInsertVideo = { insertVideoPicker.launch(arrayOf("video/*")) },
            onShowTimer = { showTimer = true },
            onWebSearch = { showWebSearch = true },
            onBackgroundSettings = { showBackgroundSettings = true },
            onLessons = {
                // Refreshed on open rather than observed: the list only
                // changes through this menu, so a hot Flow would run a query
                // for every page save all lesson long.
                viewModel.refreshLessons()
                showLessonMenu = true
            },
            onInsertImage = { insertImagePicker.launch(arrayOf("image/*")) },
            onInsertInstrument = { kind ->
                // Dropped at the viewport centre, unrotated, so it lands where
                // the teacher is looking.
                // Left of centre and below the midline, so the instrument
                // lands fully on screen with room for its controls to its
                // right — a protractor placed at the very centre puts its
                // arc off the top of the board.
                // Placed so the instrument's own extent lands on screen: a
                // set square grows DOWN from its anchor and a protractor grows
                // UP, so a single fixed drop point puts one of them off the
                // board. Anchor from the size the instrument will actually be.
                val anchor = Instrument(kind = kind, x = 0f, y = 0f)
                val extentPx = anchor.lengthCm * InstrumentGeometry.pxPerCm
                val topBias = if (kind == InstrumentKind.PROTRACTOR) 0.72f else 0.30f
                // ADDS rather than replaces: teachers routinely work with two
                // at once — a set square rested against a ruler is the standard
                // way to draw parallel lines.
                state.instruments.add(
                    anchor.copy(
                        x = state.camera.screenToWorldX(
                            (state.viewportWidth - extentPx).coerceAtLeast(0f) * 0.35f,
                        ),
                        // Staggered DOWN the board rather than across: two
                        // instruments dropped at the same height put their
                        // control clusters on top of each other, and a stack
                        // of unreadable overlapping buttons is worse than
                        // either tool being slightly off-centre.
                        y = state.camera.screenToWorldY(
                            state.viewportHeight * topBias +
                                state.instruments.size * STACK_OFFSET_PX,
                        ),
                    ),
                )
            },
            onInsertTable = {
                // Dropped at the viewport centre rather than at a tap: that
                // would need a placement mode and a second pointer path, and
                // the teacher drags it where they want anyway.
                val cols = 2
                val rows = 2
                val worldW = TableGrid.DEFAULT_CELL_WIDTH * cols
                val worldH = TableGrid.DEFAULT_CELL_HEIGHT * rows
                val table = TableGrid.create(
                    x = state.camera.screenToWorldX(state.viewportWidth / 2f) - worldW / 2f,
                    y = state.camera.screenToWorldY(state.viewportHeight / 2f) - worldH / 2f,
                    rows = rows,
                    cols = cols,
                )
                state.containers.add(table)
                state.history.record(BoardCommand.AddContainer(table))
                state.refreshHistoryFlags()
                renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
                state.markCommittedDirty()
                persist()
            },
            onInsertMindmap = {
                // A lone root, placed left of centre: the tree grows to the
                // right, so centring the root would push every child it gains
                // off the edge of the board.
                val map = MindmapLayout.create(
                    x = state.camera.screenToWorldX(state.viewportWidth * 0.2f),
                    y = state.camera.screenToWorldY(state.viewportHeight / 2f) -
                        MindmapLayout.NODE_HEIGHT / 2f,
                )
                state.containers.add(map)
                state.history.record(BoardCommand.AddContainer(map))
                // Selected on insert with its root focused, so the + button is
                // already there — otherwise a fresh mindmap is one empty box
                // with no visible way to grow it.
                state.clearSelection()
                state.selectedContainerId = map.id
                state.selectedCellIndex = 0
                state.bumpSelection()
                state.refreshHistoryFlags()
                renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
                state.markCommittedDirty()
                persist()
            },
            onExportSelection = {
                // Bounds captured NOW rather than when a format is chosen: the
                // dialog is modal but the selection is still live behind it,
                // and an export must be of what the teacher had selected when
                // they asked.
                val bounds = state.selectionBounds()
                if (!Selection.isEmpty(bounds)) {
                    exportBounds = bounds
                    viewModel.beginExport()
                }
            },
            onDeleteSelection = {
                deleteSelection(state, renderer)
                persist()
            },
            onDuplicateSelection = {
                duplicateSelection(state, renderer)
                persist()
            },
            onLookupSelection = {
                val bounds = state.selectionBounds()
                if (!Selection.isEmpty(bounds)) {
                    // The crop is captured NOW, from the current selection.
                    // Deferring it into the coroutine would race the teacher
                    // clearing or moving the selection while the request runs.
                    val strokes = state.strokes.toList()
                    val textBoxes = state.textBoxes.map { box ->
                        TextBoxRender(
                            x = box.x,
                            y = box.y,
                            text = box.text,
                            colorArgb = box.colorArgb,
                            fontSizePx = with(density) { box.fontSizeSp.sp.toPx() },
                        )
                    }
                    val bg = backgroundBitmap
                    val containers = state.containers.toList()
                    viewModel.lookupSelection {
                        renderer.exportBitmap(
                            strokes = strokes,
                            textBoxes = textBoxes,
                            background = bg,
                            containers = containers,
                            regionBounds = bounds,
                            // Small selections are UPSCALED here. A lassoed
                            // equation may be only ~200px of world space, and
                            // the vision model reads a larger render of the
                            // same strokes far more reliably.
                            maxScale = LOOKUP_MAX_SCALE,
                            paddingWorld = LOOKUP_PADDING_WORLD,
                        )
                    }
                }
            },
            // BOTTOM-LEFT, matching the reference panel.
            //
            // A teacher stands at the board and writes from the top down, so
            // the top edge is where content goes and the bottom edge is where
            // the hand already is. A top-centre toolbar sits exactly where a
            // lesson heading belongs and forces a reach up to a 1080px-tall
            // panel for every tool change.
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = dimens.gutterSmall, bottom = dimens.gutter),
        )

        ZoomControls(
            state = state,
            onZoomChanged = ::settleCamera,
            onFitToContent = {
                val bounds = Selection.boundsOf(
                    state.strokes.toList(),
                    state.textBoxes.toList(),
                )
                // An empty table has no ink to bound it but is still content
                // the teacher expects "fit" to include.
                state.containers.forEach { container ->
                    val b = container.bounds()
                    if (Selection.isEmpty(bounds)) {
                        bounds[0] = b[0]; bounds[1] = b[1]; bounds[2] = b[2]; bounds[3] = b[3]
                    } else {
                        bounds[0] = minOf(bounds[0], b[0]); bounds[1] = minOf(bounds[1], b[1])
                        bounds[2] = maxOf(bounds[2], b[2]); bounds[3] = maxOf(bounds[3], b[3])
                    }
                }
                if (!Selection.isEmpty(bounds)) {
                    state.camera.fitTo(
                        bounds[0], bounds[1], bounds[2], bounds[3],
                        state.viewportWidth, state.viewportHeight,
                    )
                } else {
                    state.camera.reset()
                }
                settleCamera()
            },
            // Sits ABOVE the page bar at the right edge: the bottom-left is
            // now the toolbar's, and zoom is a glance-and-adjust control
            // rather than one reached mid-sentence.
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = dimens.gutterSmall + rightInset,
                    bottom = dimens.chromeButton + dimens.gutter * 2,
                ),
        )

        PageStrip(
            pages = uiState.pages,
            currentPageId = uiState.currentPageId,
            onSelectPage = { viewModel.switchToPage(it, ::applySnapshot) },
            onAddPage = { viewModel.addPage(::applySnapshot) },
            onDeletePage = { viewModel.deleteCurrentPage(::applySnapshot) },
            onOpenMenu = openMenu,
            isSplit = isSplit,
            onToggleSplit = {
                if (isSplit) {
                    viewModel.closeSecondaryPane()
                } else {
                    viewModel.openSecondaryPane { snapshot ->
                        applyToPane(secondaryState, secondaryRenderer, snapshot)
                    }
                }
            },
            // BOTTOM-RIGHT, opposite the toolbar, as on the reference panel.
            // Page navigation is a between-topics action, not a mid-sentence
            // one, so it sits at the far end from the tools.
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // Extra bottom padding clears the system gesture bar, which
                // sits over the window even with safeDrawing insets applied.
                .padding(end = dimens.gutterSmall + rightInset, bottom = dimens.gutter),
        )

        // Inside the Box so it can align to the chrome, and anchored TopEnd
        // under the clock island: the toolbar owns the top-centre, the page
        // strip the bottom-centre, and the debug overlay the bottom-end.
        lookupState?.let { lookup ->
            LookupPanel(
                state = lookup,
                onDismiss = viewModel::dismissLookup,
                onShareToLens = {
                    val uri = when (lookup) {
                        is LookupState.Working -> lookup.previewUri
                        is LookupState.Ready -> lookup.shareUri
                        is LookupState.Failed -> lookup.shareUri
                        is LookupState.NotConfigured -> lookup.shareUri
                    }
                    uri?.let { LensShare.shareImage(context, it) }
                },
                onSearchWeb = { query -> LensShare.searchWeb(context, query) },
                onSaveToNotes = {
                    viewModel.dismissLookup()
                    onOpenNotes()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = dimens.headerHeight + dimens.gutter,
                        end = dimens.gutterSmall,
                    ),
            )
        }

        if (state.showPointerDebug) {
            PointerDebugOverlay(
                info = debugInfo,
                strokeCount = state.strokes.size,
                zoom = state.camera.zoom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(dimens.gutterSmall),
            )
        }
    }

    exportPhase?.let { phase ->
        /** Renders the captured region on the MAIN thread; see exportSelection. */
        fun renderExport(): android.graphics.Bitmap? {
            val bounds = exportBounds ?: return null
            return renderer.exportBitmap(
                strokes = state.strokes.toList(),
                textBoxes = state.textBoxes.map { box ->
                    TextBoxRender(
                        x = box.x,
                        y = box.y,
                        text = box.text,
                        colorArgb = box.colorArgb,
                        fontSizePx = with(density) { box.fontSizeSp.sp.toPx() },
                    )
                },
                background = backgroundBitmap,
                containers = state.containers.toList(),
                media = state.mediaBitmaps.toMap(),
                regionBounds = bounds,
                // No padding: the teacher selected a region, and an export is
                // a document rather than a crop for a model to read.
                paddingWorld = 0f,
            )
        }

        ExportDialog(
            phase = phase,
            onSavePng = { viewModel.exportSelection(asPdf = false, render = ::renderExport) },
            onSavePdf = { viewModel.exportSelection(asPdf = true, render = ::renderExport) },
            onDismiss = {
                viewModel.dismissExport()
                exportBounds = null
            },
        )
    }

    playingVideoPath?.let { path ->
        VideoPlayerDialog(
            path = path,
            onDismiss = { playingVideoPath = null },
            onCaptureFrame = { positionMs ->
                // The player closes on capture: the frame is now on the board
                // and annotating it is the next thing the teacher does.
                // Leaving the video open would hide the thing they just took.
                playingVideoPath = null
                viewModel.captureVideoFrame(path, positionMs, ::placeMedia)
            },
        )
    }

    snapshotPhase?.let { phase ->
        SnapshotDialog(
            phase = phase,
            onDismiss = viewModel::dismissSnapshotDialog,
            onOpenNotes = {
                viewModel.dismissSnapshotDialog()
                onOpenNotes()
            },
        )
    }

    backgroundState?.let { bg ->
        BackgroundImportSheet(
            uiState = bg,
            onPickImage = { imagePicker.launch(arrayOf("image/*")) },
            onPickPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
            onChoosePage = viewModel::onPdfPageChosen,
            onRemoveBackground = {
                viewModel.removeBackground { background ->
                    val before = state.background
                    state.background = background
                    state.history.record(BoardCommand.SetBackground(before, background))
                    state.refreshHistoryFlags()
                    persist()
                }
            },
            onDismiss = viewModel::dismissBackgroundSheet,
        )
    }
}

/**
 * Live pointer telemetry — the bring-up tool for real hardware.
 *
 * Board vendors ship idiosyncratic pen stacks (pressure pinned to 1.0, the pen
 * reported as a finger, phantom contacts). This answers "what is the board
 * actually sending?" in seconds rather than needing an instrumented build.
 */
@Composable
private fun PointerDebugOverlay(
    info: PointerDebugInfo?,
    strokeCount: Int,
    zoom: Float,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens
    Box(
        modifier
            .padding(dimens.gutter)
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .background(Color(0xCC101820))
            .padding(dimens.gutter),
    ) {
        Column {
            Text(
                text = "POINTER DEBUG",
                color = Color(0xFF8FC7FF),
                fontSize = dimens.labelSize,
                fontFamily = FontFamily.Monospace,
            )
            val lines = if (info == null) {
                listOf("waiting for input...")
            } else {
                listOf(
                    "type      ${info.pointerType}",
                    "pressure  ${"%.3f".format(info.pressure)}",
                    "id        ${info.pointerId}",
                    "contacts  ${info.contactCount}",
                )
            }
            lines.forEach {
                Text(
                    text = it,
                    color = Color.White,
                    fontSize = dimens.labelSize,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                text = "strokes   $strokeCount",
                color = Color(0xFF7DE3A0),
                fontSize = dimens.labelSize,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "zoom      ${"%.2f".format(zoom)}x",
                color = Color(0xFF7DE3A0),
                fontSize = dimens.labelSize,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// --- History operations -----------------------------------------------------
//
// The viewport cache cannot "un-rasterize", so any operation that removes or
// alters content invalidates it and rebuilds. That is O(visible strokes) and
// happens on a discrete user action rather than per frame.

internal fun performUndo(state: BoardState, renderer: BoardRenderer) {
    when (val command = state.history.undo()) {
        is BoardCommand.AddStroke -> state.strokes.remove(command.stroke)

        is BoardCommand.EraseStrokes -> state.strokes.addAll(command.strokes)

        is BoardCommand.ClearPage -> {
            state.strokes.addAll(command.strokes)
            state.textBoxes.addAll(command.boxes)
            state.containers.addAll(command.containers)
            state.background = command.background
        }

        is BoardCommand.AddTextBox -> state.textBoxes.remove(command.box)

        is BoardCommand.DeleteTextBox -> state.textBoxes.add(command.box)

        is BoardCommand.EditTextBox -> replaceTextBox(state, command.after, command.before)

        is BoardCommand.SetBackground -> state.background = command.before

        is BoardCommand.TransformSelection -> {
            restoreStrokes(state, command.strokesBefore)
            restoreTextBoxes(state, command.boxesBefore)
        }

        is BoardCommand.DeleteSelection -> {
            state.strokes.addAll(command.strokes)
            state.textBoxes.addAll(command.boxes)
        }

        is BoardCommand.DuplicateSelection -> {
            state.strokes.removeAll { s -> command.strokes.any { it.id == s.id } }
            state.textBoxes.removeAll { b -> command.boxes.any { it.id == b.id } }
        }

        is BoardCommand.AddContainer ->
            state.containers.removeAll { it.id == command.container.id }

        is BoardCommand.DeleteContainer -> {
            state.containers.add(command.container)
            state.strokes.addAll(command.strokes)
        }

        is BoardCommand.EditContainer -> {
            replaceContainer(state, command.before)
            restoreStrokes(state, command.strokesBefore)
            state.strokes.addAll(command.removedStrokes)
        }

        null -> return
    }
    state.clearSelection()
    renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
    state.markCommittedDirty()
    state.refreshHistoryFlags()
}

internal fun performRedo(state: BoardState, renderer: BoardRenderer) {
    when (val command = state.history.redo()) {
        is BoardCommand.AddStroke -> state.strokes.add(command.stroke)

        is BoardCommand.EraseStrokes -> state.strokes.removeAll(command.strokes)

        is BoardCommand.ClearPage -> {
            state.strokes.clear()
            state.textBoxes.clear()
            state.containers.clear()
            state.background = null
        }

        is BoardCommand.AddTextBox -> state.textBoxes.add(command.box)

        is BoardCommand.DeleteTextBox -> state.textBoxes.remove(command.box)

        is BoardCommand.EditTextBox -> replaceTextBox(state, command.before, command.after)

        is BoardCommand.SetBackground -> state.background = command.after

        is BoardCommand.TransformSelection -> {
            restoreStrokes(state, command.strokesAfter)
            restoreTextBoxes(state, command.boxesAfter)
        }

        is BoardCommand.DeleteSelection -> {
            state.strokes.removeAll { s -> command.strokes.any { it.id == s.id } }
            state.textBoxes.removeAll { b -> command.boxes.any { it.id == b.id } }
        }

        is BoardCommand.DuplicateSelection -> {
            state.strokes.addAll(command.strokes)
            state.textBoxes.addAll(command.boxes)
        }

        is BoardCommand.AddContainer -> state.containers.add(command.container)

        is BoardCommand.DeleteContainer -> {
            state.containers.removeAll { it.id == command.container.id }
            state.strokes.removeAll { s -> command.strokes.any { it.id == s.id } }
        }

        is BoardCommand.EditContainer -> {
            replaceContainer(state, command.after)
            restoreStrokes(state, command.strokesAfter)
            state.strokes.removeAll { s -> command.removedStrokes.any { it.id == s.id } }
        }

        null -> return
    }
    state.clearSelection()
    renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
    state.markCommittedDirty()
    state.refreshHistoryFlags()
}

internal fun performClear(state: BoardState, renderer: BoardRenderer) {
    if (state.strokes.isEmpty() && state.textBoxes.isEmpty() &&
        state.background == null && state.containers.isEmpty()
    ) {
        return
    }

    state.history.record(
        BoardCommand.ClearPage(
            strokes = state.strokes.toList(),
            boxes = state.textBoxes.toList(),
            background = state.background,
            containers = state.containers.toList(),
        ),
    )
    state.strokes.clear()
    state.textBoxes.clear()
    state.containers.clear()
    state.background = null
    state.clearSelection()
    renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
    state.markCommittedDirty()
    state.refreshHistoryFlags()
}

internal fun deleteSelection(state: BoardState, renderer: BoardRenderer) {
    if (!state.hasSelection) return
    val strokes = state.selectedStrokes()
    val boxes = state.selectedTextBoxes()

    state.history.record(BoardCommand.DeleteSelection(strokes, boxes))
    state.strokes.removeAll(strokes)
    state.textBoxes.removeAll(boxes)
    state.clearSelection()
    renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
    state.markCommittedDirty()
    state.refreshHistoryFlags()
}

internal fun duplicateSelection(state: BoardState, renderer: BoardRenderer) {
    if (!state.hasSelection) return
    val newStrokes = Selection.duplicateStrokes(state.selectedStrokes())
    val newBoxes = Selection.duplicateTextBoxes(state.selectedTextBoxes())

    state.strokes.addAll(newStrokes)
    state.textBoxes.addAll(newBoxes)
    state.history.record(BoardCommand.DuplicateSelection(newStrokes, newBoxes))
    // Leave the copy selected: the next action is almost always to move it.
    state.selectOnly(newStrokes.map { it.id }, newBoxes.map { it.id })
    renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
    state.markCommittedDirty()
    state.refreshHistoryFlags()
}

private fun restoreStrokes(state: BoardState, snapshot: List<Stroke>) {
    snapshot.forEach { saved ->
        val index = state.strokes.indexOfFirst { it.id == saved.id }
        if (index >= 0) state.strokes[index] = saved
    }
}

private fun restoreTextBoxes(state: BoardState, snapshot: List<TextBox>) {
    snapshot.forEach { saved ->
        val index = state.textBoxes.indexOfFirst { it.id == saved.id }
        if (index >= 0) state.textBoxes[index] = saved
    }
}

private fun replaceTextBox(state: BoardState, from: TextBox, to: TextBox) {
    val index = state.textBoxes.indexOfFirst { it.id == from.id }
    if (index >= 0) state.textBoxes[index] = to
}

/** Swaps a container in place, keeping its position in the z-order. */
private fun replaceContainer(state: BoardState, container: Container) {
    val index = state.containers.indexOfFirst { it.id == container.id }
    if (index >= 0) state.containers[index] = container
}

/**
 * Applies a structural edit to the selected mindmap and moves its ink to follow.
 *
 * Reflow repositions every node, so handwriting inside one would be left
 * behind at the node's old location — it lives in world coordinates. Each
 * stroke is translated by ITS OWN cell's delta, which is why this cannot be a
 * single container-wide offset: adding one child pushes different branches by
 * different amounts.
 */
private fun editMindmap(
    state: BoardState,
    renderer: BoardRenderer,
    edit: (Container) -> Container,
) {
    val id = state.selectedContainerId ?: return
    val before = state.containerById(id) ?: return
    if (before.kind != ContainerKind.MINDMAP) return

    val after = edit(before)
    if (after === before) return

    val strokesBefore = state.strokes.filter { it.containerId == id }
    val strokesAfter = strokesBefore.map { stroke ->
        val old = before.cellAt(stroke.cellIndex)
        val new = after.cellAt(stroke.cellIndex)
        if (old == null || new == null) {
            stroke
        } else {
            Selection.translateStroke(stroke, new.left - old.left, new.top - old.top)
        }
    }

    state.history.record(
        BoardCommand.EditContainer(
            before = before,
            after = after,
            strokesBefore = strokesBefore,
            strokesAfter = strokesAfter,
        ),
    )
    replaceContainer(state, after)
    restoreStrokes(state, strokesAfter)
    state.bumpSelection()
    state.refreshHistoryFlags()
    renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
    state.markCommittedDirty()
}

/**
 * Deletes a node, its branch, and every stroke written in them.
 *
 * Surviving ink must be RETAGGED, not just moved: removing a node renumbers
 * the cells after it, so a stroke keeping its old index would silently jump
 * into a different node — visible only after a reload.
 */
private fun deleteMindmapNode(state: BoardState, renderer: BoardRenderer, index: Int) {
    val id = state.selectedContainerId ?: return
    val before = state.containerById(id) ?: return
    if (before.kind != ContainerKind.MINDMAP) return

    val removal = MindmapLayout.deleteSubtree(before, index)
    if (removal.removedIndices.isEmpty()) return

    // Deleting the root empties the tree; remove the whole container rather
    // than leaving an invisible object on the page that still swallows taps.
    if (removal.container.cells.isEmpty()) {
        val doomed = state.strokes.filter { it.containerId == id }
        state.history.record(BoardCommand.DeleteContainer(before, doomed))
        state.containers.removeAll { it.id == id }
        state.strokes.removeAll(doomed)
        state.clearSelection()
        state.refreshHistoryFlags()
        renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
        state.markCommittedDirty()
        return
    }

    val removed = removal.removedIndices.toSet()
    val contained = state.strokes.filter { it.containerId == id }
    val doomed = contained.filter { it.cellIndex in removed }
    val kept = contained.filter { it.cellIndex !in removed }

    val after = removal.container
    val strokesAfter = kept.map { stroke ->
        val newIndex = removal.reindex[stroke.cellIndex] ?: stroke.cellIndex
        val old = before.cellAt(stroke.cellIndex)
        val new = after.cellAt(newIndex)
        val moved = if (old == null || new == null) {
            stroke
        } else {
            Selection.translateStroke(stroke, new.left - old.left, new.top - old.top)
        }
        moved.copyWith(cellIndex = newIndex)
    }

    state.history.record(
        BoardCommand.EditContainer(
            before = before,
            after = after,
            strokesBefore = kept,
            strokesAfter = strokesAfter,
            removedStrokes = doomed,
        ),
    )
    replaceContainer(state, after)
    state.strokes.removeAll(doomed)
    restoreStrokes(state, strokesAfter)
    // The focused index has just been renumbered out from under the chrome.
    state.selectedCellIndex = -1
    state.bumpSelection()
    state.refreshHistoryFlags()
    renderer.rebuildCache(state.strokes, state.camera, state.containers, state.mediaBitmaps)
    state.markCommittedDirty()
}

internal fun applyLoadedPage(
    state: BoardState,
    renderer: BoardRenderer,
    strokes: List<Stroke>,
    textBoxes: List<TextBox>,
    background: BoardBackground?,
    containers: List<Container> = emptyList(),
) {
    state.loadPage(strokes, textBoxes, background, containers)
    renderer.invalidateCache()
}

/**
 * Replaces a rough freehand stroke with a clean shape, when confident.
 *
 * Returns the original stroke untouched if recognition is off, the tool is
 * not the pen, or the recognizer declined. Leaving ink alone is always the
 * safe outcome — wrongly "correcting" a deliberate squiggle is far more
 * annoying than failing to tidy a rough circle.
 *
 * The substituted stroke keeps the original's id and style, so it inherits
 * the teacher's colour and width, and so undo treats it as the one stroke
 * they just drew.
 */
internal fun maybeSnapToShape(state: BoardState, drawn: Stroke): Stroke {
    if (!state.shapeRecognition) return drawn
    // Only freehand pen strokes are candidates. The highlighter is for
    // marking up, and the shape tools already produce exact geometry.
    if (drawn.tool != DrawTool.PEN) return drawn
    // Ink written inside a table cell or mindmap node is handwriting, not
    // drawing: a "0" in a cell must stay a zero rather than being snapped to
    // a clean circle.
    if (drawn.containerId != null) return drawn

    val result = ShapeRecognizer.recognise(drawn) ?: return drawn

    // A polygon stores every vertex; the other shapes store two endpoints.
    val points = result.vertices?.let { verts ->
        FloatArray(verts.size / 2 * Stroke.STRIDE).also { out ->
            for (i in 0 until verts.size / 2) {
                out[i * Stroke.STRIDE] = verts[i * 2]
                out[i * Stroke.STRIDE + 1] = verts[i * 2 + 1]
                out[i * Stroke.STRIDE + 2] = 1f
            }
        }
    } ?: floatArrayOf(
        result.endpoints[0], result.endpoints[1], 1f,
        result.endpoints[2], result.endpoints[3], 1f,
    )

    return drawn.copyWith(tool = result.tool, points = points)
}

/**
 * Upscale ceiling for a lookup crop.
 *
 * Higher than the snapshot export cap of 2x on purpose. A snapshot covers a
 * whole lesson, so it is already large; a lasso may enclose a single
 * handwritten fraction only a couple of hundred world-pixels across, and
 * rendering that at native size gives the vision model a thumbnail to read.
 * The strokes are vectors, so upscaling re-rasterizes them cleanly rather
 * than interpolating a small bitmap.
 */
private const val LOOKUP_MAX_SCALE = 6f

/**
 * Breathing room around the lassoed region, in world units.
 *
 * Smaller than the snapshot padding: a lookup wants the region tight so the
 * model is not distracted by neighbouring content, but not so tight that
 * descenders and superscripts are clipped at the boundary.
 */
private const val LOOKUP_PADDING_WORLD = 16f

/** Offset between stacked instruments, so each stays grabbable. */
private const val STACK_OFFSET_PX = 150f
