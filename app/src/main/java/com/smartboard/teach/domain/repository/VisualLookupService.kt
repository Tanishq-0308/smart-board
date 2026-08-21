package com.smartboard.teach.domain.repository

import android.graphics.Bitmap
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.domain.model.VisualLookup

/**
 * Explains a cropped region of the board.
 *
 * Separate interface from [NotesAiService] on purpose. The two differ in
 * prompt, schema, latency budget and failure handling — a lookup is
 * interactive and disposable (a teacher waits for it mid-sentence and may
 * cancel), while a summary is durable and must survive a failed network call.
 * Merging them would force one set of trade-offs onto both.
 *
 * Phase 2 swaps the binding to a server-proxy implementation exactly as with
 * [NotesAiService]; nothing above this interface changes.
 */
interface VisualLookupService {

    /** False when no API key is configured, so the UI can say something useful. */
    val isConfigured: Boolean

    /**
     * @param region the cropped board area, already rendered at capture scale.
     *        Implementations must not recycle it; the caller owns it.
     */
    suspend fun explainRegion(region: Bitmap): AppResult<VisualLookup>
}
