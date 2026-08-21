package com.smartboard.teach.data.repository

import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.data.local.StrokeSerializer
import com.smartboard.teach.data.local.dao.BoardDao
import com.smartboard.teach.data.local.entity.BoardBackgroundEntity
import com.smartboard.teach.data.local.entity.BoardPageEntity
import com.smartboard.teach.data.local.entity.LessonEntity
import com.smartboard.teach.data.local.entity.ContainerCellEntity
import com.smartboard.teach.data.local.entity.ContainerEntity
import com.smartboard.teach.data.local.entity.StrokeEntity
import com.smartboard.teach.data.local.entity.TextBoxEntity
import com.smartboard.teach.di.IoDispatcher
import com.smartboard.teach.domain.model.BackgroundKind
import com.smartboard.teach.domain.model.BoardBackground
import com.smartboard.teach.domain.model.BoardCanvasStyle
import com.smartboard.teach.domain.model.Lesson
import com.smartboard.teach.domain.model.GridStyle
import com.smartboard.teach.domain.model.BoardPage
import com.smartboard.teach.domain.model.CameraState
import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.ContainerCell
import com.smartboard.teach.domain.model.ContainerKind
import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import com.smartboard.teach.domain.model.TextBox
import com.smartboard.teach.domain.repository.BoardRepository
import com.smartboard.teach.domain.repository.PageContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoardRepositoryImpl @Inject constructor(
    private val boardDao: BoardDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BoardRepository {

    override fun observePages(sessionId: String): Flow<List<BoardPage>> =
        boardDao.observePages(sessionId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getPages(sessionId: String): List<BoardPage> = withContext(ioDispatcher) {
        boardDao.getPages(sessionId).map { it.toDomain() }
    }

    override suspend fun latestSessionId(): String? = withContext(ioDispatcher) {
        boardDao.latestSessionId()
    }

    override suspend fun getLessons(): List<Lesson> = withContext(ioDispatcher) {
        boardDao.getLessons().map { row ->
            Lesson(
                sessionId = row.sessionId,
                name = row.name,
                updatedAt = row.updatedAt,
                pageCount = boardDao.pageCount(row.sessionId),
            )
        }
    }

    override suspend fun getLesson(sessionId: String): Lesson? = withContext(ioDispatcher) {
        boardDao.getLesson(sessionId)?.let {
            Lesson(it.sessionId, it.name, it.updatedAt, boardDao.pageCount(it.sessionId))
        }
    }

    override suspend fun saveLesson(sessionId: String, name: String): Lesson =
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            // createdAt is preserved on a re-save, so the list does not shuffle
            // a long-standing lesson to the top of "newest" orderings.
            val created = boardDao.getLesson(sessionId)?.createdAt ?: now
            boardDao.upsertLesson(
                LessonEntity(
                    sessionId = sessionId,
                    name = name,
                    createdAt = created,
                    updatedAt = now,
                ),
            )
            Lesson(sessionId, name, now, boardDao.pageCount(sessionId))
        }

    override suspend fun deleteLesson(sessionId: String) = withContext(ioDispatcher) {
        // Pages first: they have no FK to lessons, so nothing cascades for us
        // and orphaned pages would linger as an invisible unsaved session.
        boardDao.deletePagesForSession(sessionId)
        boardDao.deleteLesson(sessionId)
    }

    override suspend fun duplicateSession(sessionId: String, newName: String): String =
        withContext(ioDispatcher) {
            val newSessionId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            boardDao.getPages(sessionId).forEach { page ->
                val newPageId = UUID.randomUUID().toString()
                // Backgrounds are SHARED, not copied: the rendered file is
                // immutable and identical, and duplicating a 40-page PDF
                // import would double its disk use for no benefit.
                // Containers get NEW ids, and the strokes written inside them
                // must be retagged to match — a copied stroke still pointing
                // at the original container would be clipped by a table on a
                // different lesson, or dangle if that lesson is deleted.
                val containers = boardDao.getContainers(page.id)
                val idMap = containers.associate { it.id to UUID.randomUUID().toString() }
                val copiedContainers = containers.map {
                    it.copy(id = idMap.getValue(it.id), pageId = newPageId)
                }
                // One query for every container's cells rather than one per
                // container: a 6x6 table page would otherwise be 36 round trips.
                val copiedCells = boardDao.getContainerCells(containers.map { it.id })
                    .map { it.copy(containerId = idMap.getValue(it.containerId)) }

                boardDao.savePageContent(
                    page = page.copy(
                        id = newPageId,
                        sessionId = newSessionId,
                        createdAt = now,
                        updatedAt = now,
                    ),
                    strokes = boardDao.getStrokes(page.id).map { stroke ->
                        stroke.copy(
                            pageId = newPageId,
                            containerId = stroke.containerId?.let { idMap[it] },
                        )
                    },
                    textBoxes = boardDao.getTextBoxes(page.id).map { it.copy(pageId = newPageId) },
                    containers = copiedContainers,
                    cells = copiedCells,
                )
            }

            boardDao.upsertLesson(
                LessonEntity(
                    sessionId = newSessionId,
                    name = newName,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            newSessionId
        }

    override suspend fun loadPage(pageId: String): PageContent? = withContext(ioDispatcher) {
        val page = boardDao.getPage(pageId) ?: return@withContext null
        val strokes = boardDao.getStrokes(pageId).mapNotNull { it.toDomain() }
        val boxes = boardDao.getTextBoxes(pageId).map { it.toDomain() }
        val background = page.backgroundId?.let { boardDao.getBackground(it)?.toDomain() }

        val containerRows = boardDao.getContainers(pageId)
        val containers = if (containerRows.isEmpty()) {
            emptyList()
        } else {
            val cellsByContainer = boardDao
                .getContainerCells(containerRows.map { it.id })
                .groupBy { it.containerId }
            containerRows.mapNotNull { row ->
                row.toDomain(cellsByContainer[row.id].orEmpty())
            }
        }

        PageContent(page.toDomain(), strokes, boxes, background, containers)
    }

    override suspend fun savePage(
        page: BoardPage,
        strokes: List<Stroke>,
        textBoxes: List<TextBox>,
        containers: List<Container>,
    ): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            boardDao.savePageContent(
                page = page.toEntity(),
                strokes = strokes.mapIndexed { index, stroke -> stroke.toEntity(page.id, index) },
                textBoxes = textBoxes.map { it.toEntity(page.id) },
                containers = containers.mapIndexed { index, c -> c.toEntity(page.id, index) },
                cells = containers.flatMap { it.cellEntities() },
            )
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage("Could not save the board: ${t.message}"))
        }
    }

    override suspend fun createPage(
        sessionId: String,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
    ): BoardPage = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val entity = BoardPageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            pageIndex = pageIndex,
            widthPx = widthPx,
            heightPx = heightPx,
            createdAt = now,
            updatedAt = now,
        )
        boardDao.upsertPage(entity)
        entity.toDomain()
    }

    override suspend fun deletePage(pageId: String): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            // Strokes and text boxes cascade via foreign keys.
            boardDao.deletePage(pageId)
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage("Could not delete the page: ${t.message}"))
        }
    }

    override suspend fun saveBackground(background: BoardBackground): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                boardDao.upsertBackground(background.toEntity())
                AppResult.Success(Unit)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Storage(t.message ?: "Could not save the background."))
            }
        }

    override suspend fun getBackground(id: String): BoardBackground? = withContext(ioDispatcher) {
        boardDao.getBackground(id)?.toDomain()
    }

    override suspend fun setPageThumbnail(pageId: String, path: String) = withContext(ioDispatcher) {
        boardDao.getPage(pageId)?.let {
            boardDao.upsertPage(it.copy(thumbnailPath = path, updatedAt = System.currentTimeMillis()))
        }
        Unit
    }
}

// --- mapping -----------------------------------------------------------------

private fun BoardPageEntity.toDomain() = BoardPage(
    id = id,
    sessionId = sessionId,
    pageIndex = pageIndex,
    widthPx = widthPx,
    heightPx = heightPx,
    backgroundId = backgroundId,
    thumbnailPath = thumbnailPath,
    canvasStyle = BoardCanvasStyle(
        colorArgb = canvasColorArgb,
        // Unknown style name falls back to plain paper rather than throwing:
        // a row written by a newer build must not break an older one.
        grid = runCatching { GridStyle.valueOf(gridStyle) }.getOrDefault(GridStyle.NONE),
        gridColorArgb = gridColorArgb,
        spacingWorld = gridSpacing,
    ),
    camera = CameraState(cameraOffsetX, cameraOffsetY, cameraZoom),
)

private fun BoardPage.toEntity(): BoardPageEntity {
    val now = System.currentTimeMillis()
    return BoardPageEntity(
        id = id,
        sessionId = sessionId,
        pageIndex = pageIndex,
        widthPx = widthPx,
        heightPx = heightPx,
        backgroundId = backgroundId,
        thumbnailPath = thumbnailPath,
        canvasColorArgb = canvasStyle.colorArgb,
        gridStyle = canvasStyle.grid.name,
        gridColorArgb = canvasStyle.gridColorArgb,
        gridSpacing = canvasStyle.spacingWorld,
        cameraOffsetX = camera.offsetX,
        cameraOffsetY = camera.offsetY,
        cameraZoom = camera.zoom,
        createdAt = now,
        updatedAt = now,
    )
}

/**
 * Returns null for a stroke whose blob failed to decode. One corrupt row
 * should cost one stroke, not the whole page mid-lesson.
 */
private fun StrokeEntity.toDomain(): Stroke? {
    val points = StrokeSerializer.decode(points)
    if (points.isEmpty()) return null
    val drawTool = runCatching { DrawTool.valueOf(tool) }.getOrNull() ?: return null
    return Stroke(
        id = id,
        tool = drawTool,
        style = StrokeStyle(
            colorArgb = colorArgb,
            baseWidthPx = baseWidthPx,
            alpha = alpha,
            isPressureSensitive = pressureSensitive,
        ),
        points = points,
        containerId = containerId,
        cellIndex = cellIndex,
    )
}

private fun Stroke.toEntity(pageId: String, orderIndex: Int) = StrokeEntity(
    id = id,
    pageId = pageId,
    orderIndex = orderIndex,
    tool = tool.name,
    colorArgb = style.colorArgb,
    baseWidthPx = style.baseWidthPx,
    alpha = style.alpha,
    pressureSensitive = style.isPressureSensitive,
    points = StrokeSerializer.encode(points),
    containerId = containerId,
    cellIndex = cellIndex,
)

/** Null for an unknown kind, so one bad row costs one container, not the page. */
private fun ContainerEntity.toDomain(cells: List<ContainerCellEntity>): Container? {
    val containerKind = runCatching { ContainerKind.valueOf(kind) }.getOrNull() ?: return null
    return Container(
        id = id,
        kind = containerKind,
        x = x,
        y = y,
        rows = rows,
        cols = cols,
        // Sorted by index: cellIndex is what strokes are tagged with, so the
        // list position must match it or ink clips against the wrong cell.
        cells = cells.sortedBy { it.cellIndex }.map { it.toDomain() },
        strokeColorArgb = strokeColorArgb,
        lineWidthPx = lineWidthPx,
        mediaPath = mediaPath,
    )
}

private fun ContainerCellEntity.toDomain() = ContainerCell(
    left = left,
    top = top,
    right = right,
    bottom = bottom,
    row = row,
    col = col,
)

private fun Container.toEntity(pageId: String, orderIndex: Int) = ContainerEntity(
    id = id,
    pageId = pageId,
    orderIndex = orderIndex,
    kind = kind.name,
    x = x,
    y = y,
    rows = rows,
    cols = cols,
    strokeColorArgb = strokeColorArgb,
    lineWidthPx = lineWidthPx,
    mediaPath = mediaPath,
)

private fun Container.cellEntities(): List<ContainerCellEntity> =
    cells.mapIndexed { index, cell ->
        ContainerCellEntity(
            containerId = id,
            cellIndex = index,
            left = cell.left,
            top = cell.top,
            right = cell.right,
            bottom = cell.bottom,
            row = cell.row,
            col = cell.col,
        )
    }

private fun TextBoxEntity.toDomain() = TextBox(
    id = id,
    x = x,
    y = y,
    widthPx = widthPx,
    text = text,
    colorArgb = colorArgb,
    fontSizeSp = fontSizeSp,
)

private fun TextBox.toEntity(pageId: String) = TextBoxEntity(
    id = id,
    pageId = pageId,
    x = x,
    y = y,
    widthPx = widthPx,
    text = text,
    colorArgb = colorArgb,
    fontSizeSp = fontSizeSp,
)

private fun BoardBackgroundEntity.toDomain() = BoardBackground(
    id = id,
    kind = runCatching { BackgroundKind.valueOf(kind) }.getOrDefault(BackgroundKind.IMAGE),
    sourcePath = sourcePath,
    pdfPageIndex = pdfPageIndex,
    renderedPath = renderedPath,
    x = x,
    y = y,
    scale = scale,
    rotation = rotation,
)

private fun BoardBackground.toEntity() = BoardBackgroundEntity(
    id = id,
    kind = kind.name,
    sourcePath = sourcePath,
    pdfPageIndex = pdfPageIndex,
    renderedPath = renderedPath,
    createdAt = System.currentTimeMillis(),
    x = x,
    y = y,
    scale = scale,
    rotation = rotation,
)
