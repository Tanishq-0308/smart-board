package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.ContainerCell
import com.smartboard.teach.domain.model.ContainerKind
import java.util.UUID

/**
 * A study-material PDF on the board: every page an IMAGE container, stacked
 * top to bottom like a document. Being ordinary containers, pages get
 * selection, clipping, persistence and export for free, and ink written on a
 * page is tagged to it and moves with it.
 */
object DocumentStack {

    /** Space between pages, in world px. */
    const val GAP = 32f

    /**
     * @param pageSizes rendered pixel size of each page, in order
     * @return one IMAGE container per page, [width] wide, stacked from ([left], [top])
     */
    fun layout(
        pageFiles: List<String>,
        pageSizes: List<Pair<Int, Int>>,
        width: Float,
        left: Float,
        top: Float,
    ): List<Container> {
        var y = top
        return pageFiles.zip(pageSizes).map { (path, size) ->
            val (w, h) = size
            val height = width * h / w.coerceAtLeast(1)
            val container = Container(
                id = UUID.randomUUID().toString(),
                kind = ContainerKind.IMAGE,
                x = left,
                y = y,
                cells = listOf(ContainerCell(left = left, top = y, right = left + width, bottom = y + height)),
                mediaPath = path,
            )
            y += height + GAP
            container
        }
    }
}

/**
 * Which pictures to keep decoded. A board with a few pictures keeps them all
 * (no flicker while panning); one with a whole PDF keeps only those on or near
 * the screen, so a 40-page chapter costs the memory of about three pages on a
 * 2 GB board instead of forty.
 */
object MediaWindow {

    /** Up to this many pictures are always all kept. */
    const val KEEP_ALL_UP_TO = 8

    fun wanted(containers: List<Container>, visible: FloatArray): Set<String> {
        val media = containers.filter { it.kind.isMedia && it.mediaPath != null }
        if (media.size <= KEEP_ALL_UP_TO) return media.mapTo(HashSet()) { it.id }
        return media.filter { c ->
            val b = c.bounds()
            b[2] >= visible[0] && b[0] <= visible[2] && b[3] >= visible[1] && b[1] <= visible[3]
        }.mapTo(HashSet()) { it.id }
    }
}

/**
 * Which PDF pages and pictures are being looked at right now, for the
 * screen-time record a snapshot reads (see LessonPartPlanner). A picture counts
 * when at least half of it is on screen, or when it fills at least half the
 * screen (a page zoomed in on is never "half visible").
 */
object ScreenTime {

    const val MIN_SHARE = 0.5f

    fun onScreen(containers: List<Container>, visible: FloatArray): List<String> {
        val screenArea = area(visible)
        return containers.filter { it.kind == ContainerKind.IMAGE }.filter { c ->
            val b = c.bounds()
            val shown = area(floatArrayOf(maxOf(b[0], visible[0]), maxOf(b[1], visible[1]),
                minOf(b[2], visible[2]), minOf(b[3], visible[3])))
            shown > 0f && (shown >= MIN_SHARE * area(b) || shown >= MIN_SHARE * screenArea)
        }.map { it.id }
    }

    private fun area(r: FloatArray): Float = maxOf(0f, r[2] - r[0]) * maxOf(0f, r[3] - r[1])
}
