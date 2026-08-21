package com.smartboard.teach.feature.whiteboard

import android.net.Uri
import com.smartboard.teach.domain.model.VisualLookup

/**
 * State of the visual-lookup panel.
 *
 * A sealed hierarchy rather than a flags-and-nullables data class, because
 * the panel renders one of four mutually exclusive things and "loading with a
 * stale result still set" is a state that should not be representable.
 */
sealed interface LookupState {

    /** Crop taken, request in flight. */
    data class Working(val previewUri: Uri? = null) : LookupState

    data class Ready(
        val lookup: VisualLookup,
        /** Set once the crop has been written for sharing; null until then. */
        val shareUri: Uri? = null,
    ) : LookupState

    data class Failed(
        val message: String,
        /** Present even on failure: sharing to Lens is the offline fallback. */
        val shareUri: Uri? = null,
    ) : LookupState

    /**
     * The AI is not configured, but sharing to Lens still works.
     *
     * A distinct state because the panel offers a genuinely useful action
     * here — hand the crop to Google Lens — rather than just apologising.
     */
    data class NotConfigured(val shareUri: Uri? = null) : LookupState
}
