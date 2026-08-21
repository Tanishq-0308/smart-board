package com.smartboard.teach.feature.whiteboard

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.smartboard.teach.domain.model.BoardBackground
import com.smartboard.teach.domain.model.BoardCanvasStyle
import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.PenType
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import com.smartboard.teach.domain.model.TextBox

/**
 * What the board is currently doing.
 *
 * Modelled explicitly rather than as a set of booleans, because these states
 * are genuinely exclusive — being mid-resize while also drawing is not a
 * thing, and boolean flags let that happen.
 */
sealed interface BoardMode {
    data object Draw : BoardMode
    data object Select : BoardMode
    data object Pan : BoardMode
    data object TextPlacement : BoardMode
}

sealed interface DragState {
    data object None : DragState
    data object Drawing : DragState
    data class Marquee(val startX: Float, val startY: Float) : DragState
    data class MovingSelection(val lastX: Float, val lastY: Float) : DragState
    /**
     * Resize in progress.
     *
     * Carries the objects as they were at press. Every frame rebuilds from
     * THESE rather than applying an incremental step to the current state:
     * geometry converges under repeated stepping but stroke width does not,
     * and a slow drag compounded a hairline into a fat blob.
     */
    data class ResizingSelection(
        val handle: Selection.Handle,
        val originalBounds: FloatArray,
        val originalStrokes: List<Stroke>,
        val originalBoxes: List<TextBox>,
        /** Placement at press, when the background is what is being resized. */
        val originalBackground: BoardBackground? = null,
    ) : DragState

    /** Rotation in progress; also rebuilds from the originals each frame. */
    data class RotatingSelection(
        val pivotX: Float,
        val pivotY: Float,
        val startAngle: Float,
        val originalStrokes: List<Stroke>,
        val originalBoxes: List<TextBox>,
        val originalBackground: BoardBackground? = null,
        /** Bounds at press; the chrome rotates THIS rather than refitting. */
        val originalBounds: FloatArray? = null,
    ) : DragState

    data object Panning : DragState
}

/**
 * Mutable state for one board page, plus tool and camera.
 *
 * [liveStrokeVersion] and [committedVersion] are read ONLY inside the Canvas
 * draw lambda. A draw-phase read invalidates just the draw pass; a
 * composition-phase read would recompose the canvas on every pointer sample
 * and destroy the frame budget.
 */
@Stable
class BoardState {

    // --- Camera ---
    val camera = Camera()

    /** Canvas size in px. Needed to anchor button zooms on the viewport centre. */
    var viewportWidth by mutableStateOf(0f)
    var viewportHeight by mutableStateOf(0f)

    // --- Tool selection ---
    var tool by mutableStateOf(DrawTool.PEN)
    var mode by mutableStateOf<BoardMode>(BoardMode.Draw)
    var penColor by mutableStateOf(Color(0xFF17202A))
    var highlighterColor by mutableStateOf(Color(0xFFFFE14D))
    var penWidth by mutableStateOf(6f)
    var highlighterWidth by mutableStateOf(28f)

    /**
     * The geometry instrument lying on the board, if any.
     *
     * Never part of page content: an instrument is a thing on the board, not
     * something drawn on it, so it stays out of the stroke list, the undo
     * stack, exports and the database.
     */
    val instruments: SnapshotStateList<com.smartboard.teach.feature.whiteboard.instruments.Instrument> =
        emptyList<com.smartboard.teach.feature.whiteboard.instruments.Instrument>()
            .toMutableStateList()

    /**
     * The instrument a drag is currently acting on.
     *
     * Held for the whole gesture so a fast move cannot hand the drag to a
     * different instrument it happens to pass over.
     */
    var draggedInstrumentId by mutableStateOf<String?>(null)

    /** The instrument a snapped stroke is ruling against, resolved at press. */
    var snapInstrumentId by mutableStateOf<String?>(null)

    fun instrumentById(id: String?): com.smartboard.teach.feature.whiteboard.instruments.Instrument? =
        if (id == null) null else instruments.firstOrNull { it.id == id }

    fun replaceInstrument(updated: com.smartboard.teach.feature.whiteboard.instruments.Instrument) {
        val index = instruments.indexOfFirst { it.id == updated.id }
        if (index >= 0) instruments[index] = updated
    }

    /**
     * Whether the stroke in progress is being ruled against the instrument.
     *
     * Plain var, not state: read only inside pointer handling, never in
     * composition or the draw phase.
     */
    var snappingToInstrument: Boolean = false

    /** What a drag on the instrument is doing, set at press. */
    var instrumentDragMode by mutableStateOf(
        com.smartboard.teach.feature.whiteboard.instruments.InstrumentDrag.NONE,
    )

    /** Which nib the pen button is currently holding. */
    var penType by mutableStateOf(PenType.PEN)

    /**
     * Colour and width per nib.
     *
     * Switching from a yellow highlighter back to the pen must give back the
     * black pen, not a yellow one — so each nib remembers its own settings
     * rather than sharing one colour across all five.
     */
    private val nibColor = mutableStateMapOf<PenType, Color>()
    private val nibWidth = mutableStateMapOf<PenType, Float>()

    fun colorFor(type: PenType): Color = nibColor[type] ?: when (type) {
        PenType.HIGHLIGHTER -> Color(0xFFFFE14D)
        else -> Color(0xFF17202A)
    }

    fun widthFor(type: PenType): Float = nibWidth[type] ?: type.defaultWidth

    fun setColorFor(type: PenType, color: Color) {
        nibColor[type] = color
        syncLegacyStyle(type)
    }

    fun setWidthFor(type: PenType, width: Float) {
        nibWidth[type] = width
        syncLegacyStyle(type)
    }

    /**
     * Keeps [penColor]/[penWidth] in step with the active nib.
     *
     * currentStyle() and the persistence layer still read those fields, so the
     * nib maps stay the source of truth and these mirror the active one.
     */
    private fun syncLegacyStyle(type: PenType) {
        if (type.isHighlighter) {
            highlighterColor = colorFor(type)
            highlighterWidth = widthFor(type)
        } else {
            penColor = colorFor(type)
            penWidth = widthFor(type)
        }
    }

    /** Selects a nib and applies its remembered colour and width. */
    fun selectPenType(type: PenType) {
        penType = type
        tool = type.tool
        mode = BoardMode.Draw
        syncLegacyStyle(type)
    }

    /** Eraser radius in SCREEN px; converted to world at hit-test time so it
     *  stays the same physical size however far the board is zoomed. */
    var eraserScreenRadius by mutableStateOf(30f)

    // --- Input behaviour (mirrors Settings) ---
    var stylusOnlyMode by mutableStateOf(false)
    var pressureSensitivity by mutableStateOf(true)
    var honourEraserButton by mutableStateOf(true)
    var showPointerDebug by mutableStateOf(false)
    var shapeRecognition by mutableStateOf(true)

    // --- Page content, all in WORLD coordinates ---
    val strokes: SnapshotStateList<Stroke> = emptyList<Stroke>().toMutableStateList()
    val textBoxes: SnapshotStateList<TextBox> = emptyList<TextBox>().toMutableStateList()
    var background by mutableStateOf<BoardBackground?>(null)

    /**
     * Tables and mindmaps on this page.
     *
     * The ink they hold is NOT stored here — it stays in [strokes], tagged
     * with the container and cell it was written into, so the renderer,
     * eraser, selection and save paths keep working on one list.
     */
    val containers: SnapshotStateList<Container> = emptyList<Container>().toMutableStateList()

    /** Container id -> container, rebuilt when the list changes. Draw-phase lookup. */
    fun containerById(id: String): Container? = containers.firstOrNull { it.id == id }

    /**
     * Decoded bitmaps for IMAGE containers, keyed by container id.
     *
     * Kept here rather than on the Container so a move — which copies the
     * container every frame — never copies a bitmap. Decoding happens once,
     * off the main thread, when a page loads or an image is inserted.
     */
    val mediaBitmaps = mutableStateMapOf<String, android.graphics.Bitmap>()

    /** Bumped when a bitmap arrives, so the draw phase picks it up. */
    var mediaVersion by mutableIntStateOf(0)
        private set

    fun putMedia(containerId: String, bitmap: android.graphics.Bitmap) {
        mediaBitmaps[containerId] = bitmap
        mediaVersion++
        markCommittedDirty()
    }

    fun clearMedia() {
        // Bitmaps belong to the cache, not to a page: recycling here would
        // free one still being drawn if a page switch raced a decode.
        mediaBitmaps.clear()
        mediaVersion++
    }

    // --- Selection ---
    val selectedStrokeIds: SnapshotStateList<String> = emptyList<String>().toMutableStateList()

    /**
     * The selected container, if any.
     *
     * A single id rather than a list: containers are large objects a teacher
     * places one at a time, and multi-select would need the transform paths to
     * merge several frames' cell rects for no gain today.
     */
    var selectedContainerId by mutableStateOf<String?>(null)

    /**
     * Which NODE of the selected mindmap is focused, or -1.
     *
     * A mindmap is edited node by node — add a child here, delete that branch
     * — so the chrome has to know which one the teacher means. An index
     * alongside the existing id, rather than a parallel selection concept, so
     * move, undo, persistence and clipping all keep working unchanged.
     */
    var selectedCellIndex by mutableStateOf(-1)

    /**
     * Mindmap node the current press landed in, or null.
     *
     * Set at press and resolved at release: if the pen never travelled far
     * enough to be handwriting, the press was a TAP meant to focus the node,
     * and its one-dot stroke is thrown away. Plain var, not state — read only
     * inside pointer handling, never in composition or the draw phase.
     */
    var tappedCell: com.smartboard.teach.feature.whiteboard.container.CellHit? = null
    val selectedTextBoxIds: SnapshotStateList<String> = emptyList<String>().toMutableStateList()

    var dragState by mutableStateOf<DragState>(DragState.None)

    /** Live marquee rect in world coords while dragging, else null. */
    var marqueeRect by mutableStateOf<FloatArray?>(null)

    /**
     * The background is selected as a whole rather than by id.
     *
     * There is at most one per page, so a flag beats threading a fifth id
     * list through selection, resize and rotate. It is deliberately exclusive
     * with ink selection: dragging a handle has to mean one thing.
     */
    var backgroundSelected by mutableStateOf(false)

    val hasSelection: Boolean
        get() = selectedStrokeIds.isNotEmpty() || selectedTextBoxIds.isNotEmpty() ||
            backgroundSelected || selectedContainerId != null

    /** Ink written inside the selected container, so it moves with it. */
    fun selectedContainerStrokes(): List<Stroke> {
        val id = selectedContainerId ?: return emptyList()
        return strokes.filter { it.containerId == id }
    }

    fun selectedStrokes(): List<Stroke> =
        strokes.filter { it.id in selectedStrokeIds }

    fun selectedTextBoxes(): List<TextBox> =
        textBoxes.filter { it.id in selectedTextBoxIds }

    fun selectionBounds(): FloatArray {
        selectedContainerId?.let { id ->
            containerById(id)?.let { return it.bounds() }
        }
        if (backgroundSelected) {
            val bg = background ?: return Selection.emptyBounds()
            val w = backgroundWidthPx * bg.scale
            val h = backgroundHeightPx * bg.scale
            // Axis-aligned bounds of the UNROTATED box. The chrome draws the
            // handles here; rotation is shown by the image itself turning.
            return floatArrayOf(bg.x, bg.y, bg.x + w, bg.y + h)
        }
        return Selection.boundsOf(selectedStrokes(), selectedTextBoxes())
    }

    /**
     * The page's paper: colour and grid.
     *
     * Per PAGE, so one lesson can hold a squared page for a graph and a lined
     * page for writing. Reset by loadPage like any other page content.
     */
    var canvasStyle by mutableStateOf(BoardCanvasStyle())

    /** Native pixel size of the decoded background, set by the canvas. */
    var backgroundWidthPx by mutableStateOf(0f)
    var backgroundHeightPx by mutableStateOf(0f)

    fun clearSelection() {
        if (selectedStrokeIds.isNotEmpty()) selectedStrokeIds.clear()
        if (selectedTextBoxIds.isNotEmpty()) selectedTextBoxIds.clear()
        if (backgroundSelected) backgroundSelected = false
        if (selectedContainerId != null) selectedContainerId = null
        selectedCellIndex = -1
        selectionRotation = 0f
        selectionVersion++
    }

    fun selectOnly(strokeIds: List<String>, textBoxIds: List<String>) {
        backgroundSelected = false
        selectedContainerId = null
        selectedCellIndex = -1
        selectionRotation = 0f
        selectedStrokeIds.clear()
        selectedStrokeIds.addAll(strokeIds)
        selectedTextBoxIds.clear()
        selectedTextBoxIds.addAll(textBoxIds)
        selectionVersion++
    }

    /**
     * Accumulated rotation of the current selection, in radians.
     *
     * The chrome needs this because it CANNOT be recovered from the content:
     * once strokes are rotated their axis-aligned bounds are upright again, so
     * a box rebuilt from them stays square and merely grows. Tracking the
     * angle lets the box turn with what it encloses.
     *
     * Reset whenever the selection changes, since a new selection starts from
     * its own orientation.
     */
    var selectionRotation by mutableStateOf(0f)

    var selectionVersion by mutableIntStateOf(0)
        private set

    fun bumpSelection() {
        selectionVersion++
    }

    // --- In-progress stroke ---
    var liveBuilder: StrokeBuilder? = null
        private set

    var liveStrokeVersion by mutableIntStateOf(0)
        private set

    var liveStroke by mutableStateOf<Stroke?>(null)
        private set

    /**
     * Pressure from the previous accepted sample.
     *
     * Compose's historical points carry position but not pressure, so this is
     * the "from" end of the ramp used to approximate pressure across a batch.
     * Plain var, not state: read only inside pointer handling.
     */
    var lastPressure: Float = 1f

    val history = BoardHistory()

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Bumped to force the committed layer to be re-blitted. */
    var committedVersion by mutableIntStateOf(0)
        private set

    fun currentStyle(): StrokeStyle {
        // Shape tools are not nibs; they draw with the pen's current colour.
        val nib = if (tool.isFreehand) penType else PenType.PEN
        return StrokeStyle(
            colorArgb = colorFor(nib).toArgb(),
            // Width is stored in WORLD units, so a stroke drawn zoomed-in is
            // the same physical thickness as one drawn zoomed-out.
            baseWidthPx = widthFor(nib) / camera.zoom,
            alpha = nib.defaultAlpha,
            // The global pressure setting can only turn pressure OFF; a nib
            // that is not pressure-varying never gains it.
            isPressureSensitive = nib.pressureSensitive && pressureSensitivity,
        )
    }

    fun beginStroke(
        tool: DrawTool,
        initialPressure: Float = 1f,
        containerId: String? = null,
        cellIndex: Int = -1,
    ): StrokeBuilder {
        val builder = StrokeBuilder(tool, currentStyle(), containerId, cellIndex)
        liveBuilder = builder
        lastPressure = initialPressure
        return builder
    }

    fun onLivePointAdded() {
        liveStrokeVersion++
        liveStroke = liveBuilder?.build()
    }

    fun endStroke(): Stroke? {
        val stroke = liveBuilder?.build()
        liveBuilder = null
        liveStroke = null
        liveStrokeVersion++
        return stroke
    }

    fun cancelStroke() {
        liveBuilder = null
        liveStroke = null
        // A gesture that turned into a pan is no longer a tap on a node.
        tappedCell = null
        liveStrokeVersion++
    }

    fun markCommittedDirty() {
        committedVersion++
    }

    fun refreshHistoryFlags() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    fun loadPage(
        strokes: List<Stroke>,
        textBoxes: List<TextBox>,
        background: BoardBackground?,
        containers: List<Container> = emptyList(),
    ) {
        this.strokes.clear()
        this.strokes.addAll(strokes)
        this.textBoxes.clear()
        this.textBoxes.addAll(textBoxes)
        // Containers must land BEFORE the caller rebuilds the render cache,
        // or the first frame after a page switch draws contained ink with no
        // frame and no clip.
        this.containers.clear()
        this.containers.addAll(containers)
        this.background = background
        clearSelection()
        // Undo history is per page and does not survive a page switch.
        history.clear()
        refreshHistoryFlags()
        markCommittedDirty()
    }

    companion object {
        /** Low enough to read ink through it, high enough to see the mark. */
        const val HIGHLIGHTER_ALPHA = 0.35f
    }
}
