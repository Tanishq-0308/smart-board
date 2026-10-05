package com.smartboard.teach.data.remote.erp

import android.graphics.Bitmap
import com.smartboard.teach.R
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.core.util.AppText
import com.smartboard.teach.core.util.BitmapUtils
import com.smartboard.teach.core.util.map
import com.smartboard.teach.domain.model.LessonNotes
import com.smartboard.teach.domain.model.LookupKind
import com.smartboard.teach.domain.model.VisualLookup
import com.smartboard.teach.domain.repository.NotesAiService
import com.smartboard.teach.domain.repository.VisualLookupService
import javax.inject.Inject
import javax.inject.Singleton

/*
 * Board AI through the school's ERP (`/api/ai/board/notes` and `/lookup`). The
 * ERP holds the provider keys and meters each call against the teacher and the
 * school's plan, so no key ships on the device. 403 = AI not in the plan, 429 =
 * the month's allowance is used up; the ERP's message says which.
 */

@Singleton
class ErpNotesAiService @Inject constructor(
    private val api: ErpApi,
) : NotesAiService {

    /**
     * The ERP holds the keys, so there is nothing to configure on the board. A
     * guest's call fails with NotAuthenticated, and the snapshot waits as a
     * pending note until a teacher signs in.
     */
    override val isConfigured: Boolean = true

    override val modelName: String = "Skolar AI"

    override suspend fun summarizeBoard(snapshot: Bitmap): AppResult<LessonNotes> =
        api.post("/api/ai/board/notes", ImageRequest(dataUrl(snapshot, BitmapUtils.AI_LONG_EDGE_PX, BitmapUtils.AI_JPEG_QUALITY)),
            ImageRequest.serializer(), NotesDto.serializer()).map { it.toDomain() }
}

@Singleton
class ErpLookupService @Inject constructor(
    private val api: ErpApi,
) : VisualLookupService {

    override val isConfigured: Boolean = true

    override suspend fun explainRegion(region: Bitmap): AppResult<VisualLookup> =
        // A crop is small to begin with; handwriting is thin line art that
        // JPEG artefacts hurt, hence the larger edge and higher quality.
        api.post("/api/ai/board/lookup", ImageRequest(dataUrl(region, LOOKUP_MAX_EDGE_PX, LOOKUP_JPEG_QUALITY)),
            ImageRequest.serializer(), LookupDto.serializer()).map { it.toDomain() }

    private companion object {
        const val LOOKUP_MAX_EDGE_PX = 2048
        const val LOOKUP_JPEG_QUALITY = 92
    }
}

/** Downscales a copy (never the caller's bitmap) and returns a JPEG data URL. */
internal fun dataUrl(source: Bitmap, maxEdge: Int, quality: Int): String {
    val scaled = BitmapUtils.downscale(source, maxEdge)
    try {
        return BitmapUtils.toDataUrl(BitmapUtils.toJpegBytes(scaled, quality))
    } finally {
        if (scaled !== source) scaled.recycle()
    }
}

internal fun NotesDto.toDomain() = LessonNotes(
    title = title.ifBlank { AppText.get(R.string.status_title_board_notes) },
    summary = summary,
    topics = topics,
    keyPoints = keyPoints,
    definitions = definitions.map { LessonNotes.Definition(it.term, it.meaning) },
    formulas = formulas,
    followUpQuestions = followUpQuestions,
)

internal fun LookupDto.toDomain() = VisualLookup(
    title = title.ifBlank { AppText.get(R.string.status_title_selected_region) },
    kind = runCatching { LookupKind.valueOf(kind) }.getOrDefault(LookupKind.OTHER),
    explanation = explanation,
    transcription = transcription,
    relatedTerms = relatedTerms,
    searchQuery = searchQuery.ifBlank { title },
    isUnreadable = isUnreadable,
)
