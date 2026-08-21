package com.smartboard.teach.data.local.entity

import com.smartboard.teach.domain.model.BoardCanvasStyle
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A named lesson.
 *
 * Sessions already existed as a bare  column on pages; this gives
 * them a NAME so a teacher can find one again. A session with no row here is
 * an unsaved working session — the board has always had one of those, and it
 * keeps working exactly as before.
 */
@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val sessionId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "board_pages", indices = [Index("sessionId")])
data class BoardPageEntity(
    @PrimaryKey val id: String,
    /** Groups pages into one lesson. */
    val sessionId: String,
    val pageIndex: Int,
    val widthPx: Int,
    val heightPx: Int,
    val backgroundId: String? = null,
    /** App-files path to a small PNG used by the page strip. */
    val thumbnailPath: String? = null,
    /**
     * Camera state, so a lesson resumes at the pan and zoom the teacher left
     * it at. On an infinite canvas this matters: without it, reopening the
     * board drops you at the origin, which may be nowhere near the work.
     */
    /** Per-page paper; defaults reproduce the old fixed surface, no grid. */
    val canvasColorArgb: Int = BoardCanvasStyle.DEFAULT_COLOR_ARGB,
    val gridStyle: String = "NONE",
    val gridColorArgb: Int? = null,
    val gridSpacing: Float = BoardCanvasStyle.DEFAULT_SPACING,
    val cameraOffsetX: Float = 0f,
    val cameraOffsetY: Float = 0f,
    val cameraZoom: Float = 1f,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "strokes",
    foreignKeys = [
        ForeignKey(
            entity = BoardPageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pageId")],
)
data class StrokeEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    /** Preserves z-order / insertion order on reload. */
    val orderIndex: Int,
    val tool: String,
    val colorArgb: Int,
    val baseWidthPx: Float,
    val alpha: Float,
    val pressureSensitive: Boolean,
    /** Binary blob; see StrokeSerializer. */
    val points: ByteArray,
    /**
     * Table/mindmap cell this ink was written into, or null for free ink.
     *
     * Deliberately NOT a foreign key. `savePageContent` clears and re-inserts a
     * page's strokes wholesale, which would fight an FK's insert ordering. A
     * dangling id is harmless — the renderer falls back to drawing the stroke
     * unclipped, so ink is never lost, only its clip.
     */
    val containerId: String? = null,
    val cellIndex: Int = -1,
) {
    // ByteArray needs structural equality spelled out, otherwise Room diffing
    // and test assertions compare references.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StrokeEntity) return false
        return id == other.id &&
            pageId == other.pageId &&
            orderIndex == other.orderIndex &&
            tool == other.tool &&
            colorArgb == other.colorArgb &&
            baseWidthPx == other.baseWidthPx &&
            alpha == other.alpha &&
            pressureSensitive == other.pressureSensitive &&
            containerId == other.containerId &&
            cellIndex == other.cellIndex &&
            points.contentEquals(other.points)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + pageId.hashCode()
        result = 31 * result + orderIndex
        result = 31 * result + tool.hashCode()
        result = 31 * result + colorArgb
        result = 31 * result + baseWidthPx.hashCode()
        result = 31 * result + alpha.hashCode()
        result = 31 * result + pressureSensitive.hashCode()
        result = 31 * result + (containerId?.hashCode() ?: 0)
        result = 31 * result + cellIndex
        result = 31 * result + points.contentHashCode()
        return result
    }
}

@Entity(
    tableName = "containers",
    foreignKeys = [
        ForeignKey(
            entity = BoardPageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pageId")],
)
data class ContainerEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    /** Z-order among containers on the page. */
    val orderIndex: Int,
    val kind: String,
    val x: Float,
    val y: Float,
    val rows: Int,
    val cols: Int,
    val strokeColorArgb: Int,
    val lineWidthPx: Float,
    /** File backing an IMAGE container; null for frames. */
    val mediaPath: String? = null,
)

/**
 * One cell rect. Its own table rather than a JSON blob on the container,
 * because deleting a row has to cascade to the cells (and their ink) — which
 * is one SQL statement here and hand-written reindexing in a blob.
 */
@Entity(
    tableName = "container_cells",
    foreignKeys = [
        ForeignKey(
            entity = ContainerEntity::class,
            parentColumns = ["id"],
            childColumns = ["containerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("containerId")],
    primaryKeys = ["containerId", "cellIndex"],
)
data class ContainerCellEntity(
    val containerId: String,
    val cellIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val row: Int,
    val col: Int,
)

@Entity(tableName = "board_backgrounds")
data class BoardBackgroundEntity(
    @PrimaryKey val id: String,
    val kind: String,
    /** Copy of the imported file inside app storage. */
    val sourcePath: String,
    val pdfPageIndex: Int? = null,
    /** Cached rendered bitmap for this page/image. */
    val renderedPath: String,
    val createdAt: Long,
    /**
     * World-space placement. Defaults reproduce the old behaviour exactly:
     * pinned at the origin, native size, unrotated.
     */
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

@Entity(
    tableName = "text_boxes",
    foreignKeys = [
        ForeignKey(
            entity = BoardPageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pageId")],
)
data class TextBoxEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val x: Float,
    val y: Float,
    val widthPx: Float,
    val text: String,
    val colorArgb: Int,
    val fontSizeSp: Float,
)
