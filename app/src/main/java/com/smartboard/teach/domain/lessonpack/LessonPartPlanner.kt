package com.smartboard.teach.domain.lessonpack

import com.smartboard.teach.domain.model.BackgroundKind
import com.smartboard.teach.domain.model.BoardBackground
import com.smartboard.teach.domain.model.ContainerKind
import com.smartboard.teach.domain.repository.PageContent
import java.io.File

/**
 * Decides what a snapshot sends: only what was taught.
 *
 * A PDF page or picture counts as taught when the teacher wrote on it or kept
 * it on screen for [TAUGHT_MS]. Ink is tagged to the page it was written on, so
 * "wrote on it" needs no extra record; screen time comes from ScreenTime.
 * Everything else the teacher wrote (free ink, text, tables, mindmaps) is one
 * board part per board page. Untaught pages of a 100-page chapter are never
 * rendered or sent.
 */
object LessonPartPlanner {

    const val TAUGHT_MS = 20_000L

    /** Each part is one metered AI call, so a lesson sends at most this many. */
    const val MAX_PARTS = 8

    enum class Kind { BOARD, PICTURE }

    data class Spec(
        val pageId: String,
        val kind: Kind,
        /** The picture, for [Kind.PICTURE]. */
        val containerId: String? = null,
        /** For [Kind.BOARD]: draw the page's backdrop (a taught PDF page or photo). */
        val withBackground: Boolean = false,
        /** Where a picture came from (its container label), if known. */
        val sourceLabel: String? = null,
        /** A taught PDF backdrop (Insert > PDF): its document title and 1-based page. */
        val backdropPdf: Pair<String, Int>? = null,
        /** 1-based board page number, for "Board, page 2". */
        val pageNumber: Int,
        val inked: Boolean,
        val dwellMs: Long,
    )

    data class Plan(val parts: List<Spec>, val leftOut: Int)

    /** [pages] in board order; [dwell] is screen time by container or page id. */
    fun plan(pages: List<PageContent>, dwell: Map<String, Long>): Plan {
        val candidates = pages.flatMapIndexed { index, page -> partsOf(page, index + 1, dwell) }
        if (candidates.size <= MAX_PARTS) return Plan(candidates, 0)

        // Ink is the strongest sign of teaching; after that, the longest watched.
        val keep = candidates.sortedWith(compareByDescending<Spec> { it.inked }.thenByDescending { it.dwellMs })
            .take(MAX_PARTS).toSet()
        return Plan(candidates.filter { it in keep }, candidates.size - MAX_PARTS)
    }

    private fun partsOf(page: PageContent, pageNumber: Int, dwell: Map<String, Long>): List<Spec> {
        val pictures = page.containers.filter { it.kind == ContainerKind.IMAGE }
        val pictureIds = pictures.mapTo(HashSet()) { it.id }
        val inkOnPicture = page.strokes.mapNotNullTo(HashSet()) { it.containerId?.takeIf(pictureIds::contains) }

        val out = mutableListOf<Spec>()
        val boardInk = page.strokes.any { it.containerId == null || it.containerId !in pictureIds }
        val boardOther = page.textBoxes.isNotEmpty() ||
            page.containers.any { it.kind == ContainerKind.TABLE || it.kind == ContainerKind.MINDMAP }
        val bg = page.background
        val bgDwell = dwell[page.page.id] ?: 0L
        val bgTaught = bg != null && (boardInk || bgDwell >= TAUGHT_MS)
        if (boardInk || boardOther || bgTaught) {
            out += Spec(
                pageId = page.page.id, kind = Kind.BOARD, withBackground = bgTaught,
                backdropPdf = bg?.takeIf { bgTaught }?.let(::backdropPdf),
                pageNumber = pageNumber, inked = boardInk || boardOther, dwellMs = bgDwell,
            )
        }
        pictures.forEach { c ->
            val inked = c.id in inkOnPicture
            val ms = dwell[c.id] ?: 0L
            if (inked || ms >= TAUGHT_MS) {
                out += Spec(page.page.id, Kind.PICTURE, containerId = c.id, sourceLabel = c.label,
                    pageNumber = pageNumber, inked = inked, dwellMs = ms)
            }
        }
        return out
    }

    private fun backdropPdf(bg: BoardBackground): Pair<String, Int>? =
        bg.pdfPageIndex?.takeIf { bg.kind == BackgroundKind.PDF_PAGE }
            ?.let { pdfTitle(File(bg.sourcePath)) to it + 1 }
}

/**
 * A readable name for a PDF on the board. Copies are stored as
 * "<id>_<file name>.pdf" (study material and Insert > PDF), so the id prefix
 * and underscores are dropped; a bare id (older imports) reads as "PDF".
 */
fun pdfTitle(pdf: File): String {
    val stem = pdf.nameWithoutExtension
    val withoutId = stem.replace(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}_?"), "")
    return withoutId.replace('_', ' ').replace(Regex("\\s+"), " ").trim().ifEmpty { "PDF" }
}
