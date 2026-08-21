package com.smartboard.teach.domain.usecase

import android.graphics.Bitmap
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.domain.model.VisualLookup
import com.smartboard.teach.domain.repository.VisualLookupService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cropped board region -> explanation.
 *
 * Deliberately thin, and deliberately does NOT persist anything — the opposite
 * choice from [GenerateNotesFromSnapshotUseCase], where writing the snapshot
 * before the network call is the whole point.
 *
 * The difference is what a failure costs. A failed snapshot loses a lesson, so
 * it must be recoverable. A failed lookup loses nothing: the ink is still on
 * the board and the teacher can simply tap again. Persisting every lookup
 * would fill the notes list with transient mid-lesson questions that nobody
 * wants to read back later.
 *
 * A teacher who DOES want to keep a lookup uses "Save to notes" in the result
 * panel, which routes through the notes flow proper.
 */
@Singleton
class ExplainBoardRegionUseCase @Inject constructor(
    private val lookupService: VisualLookupService,
) {

    val isConfigured: Boolean get() = lookupService.isConfigured

    suspend operator fun invoke(region: Bitmap): AppResult<VisualLookup> =
        lookupService.explainRegion(region)
}
