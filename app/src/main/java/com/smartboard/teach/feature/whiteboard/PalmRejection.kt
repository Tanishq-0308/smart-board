package com.smartboard.teach.feature.whiteboard

/**
 * Pointer arbitration: decides which pointer, if any, is allowed to draw.
 *
 * A teacher at a 75" board rests a hand on the surface while writing. Without
 * arbitration that hand draws. This is pure logic with no Compose or Android
 * dependency precisely so it can be unit tested — on a tablet or emulator the
 * behaviour that matters here cannot be reproduced by hand.
 *
 * Policy, in priority order:
 *
 *  1. **Stylus priority.** While any stylus is in contact, every touch pointer
 *     is ignored. The stylus keeps priority for [STYLUS_GRACE_MS] after it
 *     lifts, so a pen that skips contact mid-word does not let the resting
 *     palm take over for a frame.
 *
 *  2. **Single drawing pointer.** Only the first accepted pointer draws.
 *     Phase 1 is single-writer; a second contact is a palm, a sleeve, or a
 *     second person, and none of those should add ink.
 *
 *  3. **Finger-mode proximity guard.** With no stylus present, a touch that
 *     arrives within [PALM_WINDOW_MS] of another touch starting is rejected.
 *     This catches the common palm-lands-then-finger-writes ordering. It is
 *     imperfect by construction — Compose does not expose contact size — which
 *     is why "stylus only" mode exists in Settings as the real fix on hardware
 *     with a pen.
 */
class PalmRejection(
    private var stylusOnlyMode: Boolean = false,
) {

    private var activeDrawingPointerId: Long? = null
    private var stylusInContact: Boolean = false

    // Null means "never happened". Do NOT use Long.MIN_VALUE as a sentinel:
    // `nowMs - Long.MIN_VALUE` overflows to a negative number, which made the
    // very first touch on a freshly opened board look like it fell inside the
    // palm window and get silently rejected.
    private var lastStylusSeenAtMs: Long? = null
    private var lastTouchStartedAtMs: Long? = null

    fun setStylusOnlyMode(enabled: Boolean) {
        stylusOnlyMode = enabled
    }

    /** True while the pen (or its eraser end) is on the glass. */
    fun isStylusActive(): Boolean = stylusInContact

    /**
     * Should this pointer be allowed to begin a stroke?
     *
     * @param pointerId stable id for this contact
     * @param isStylus true for stylus or eraser tool types
     * @param nowMs monotonic event time
     */
    fun shouldAcceptDown(pointerId: Long, isStylus: Boolean, nowMs: Long): Boolean {
        if (isStylus) {
            stylusInContact = true
            lastStylusSeenAtMs = nowMs
            // The pen always wins, even mid-stroke from a finger: if a palm
            // started a stroke and the pen then lands, the pen takes over.
            activeDrawingPointerId = pointerId
            return true
        }

        // --- touch from here down ---

        if (stylusOnlyMode) return false

        // Rule 1: stylus priority, including the post-lift grace window.
        if (stylusInContact) return false
        lastStylusSeenAtMs?.let { seen ->
            if (nowMs - seen < STYLUS_GRACE_MS) return false
        }

        // Rule 2: one drawing pointer at a time.
        if (activeDrawingPointerId != null) return false

        // Rule 3: a touch immediately following another touch is likely palm.
        lastTouchStartedAtMs?.let { started ->
            if (nowMs - started < PALM_WINDOW_MS) {
                lastTouchStartedAtMs = nowMs
                return false
            }
        }

        lastTouchStartedAtMs = nowMs
        activeDrawingPointerId = pointerId
        return true
    }

    /** Only the pointer that was accepted may extend the stroke. */
    fun shouldAcceptMove(pointerId: Long, isStylus: Boolean, nowMs: Long): Boolean {
        if (isStylus) lastStylusSeenAtMs = nowMs
        return activeDrawingPointerId == pointerId
    }

    /** @return true if this up-event ended the active stroke. */
    fun onPointerUp(pointerId: Long, isStylus: Boolean, nowMs: Long): Boolean {
        if (isStylus) {
            stylusInContact = false
            lastStylusSeenAtMs = nowMs
        }
        return if (activeDrawingPointerId == pointerId) {
            activeDrawingPointerId = null
            true
        } else {
            false
        }
    }

    /** Called when a gesture is cancelled or the tool changes mid-stroke. */
    fun reset() {
        activeDrawingPointerId = null
        stylusInContact = false
    }

    companion object {
        /**
         * How long the stylus keeps priority after lifting. Long enough to
         * cover the gap between letters, short enough that switching
         * deliberately to finger drawing does not feel broken.
         */
        const val STYLUS_GRACE_MS = 300L

        /**
         * Touches arriving closer together than this are treated as palm plus
         * finger rather than two intentional contacts.
         */
        const val PALM_WINDOW_MS = 150L
    }
}
