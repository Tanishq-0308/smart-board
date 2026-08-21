package com.smartboard.teach.domain.model

/**
 * Result of explaining a lassoed region of the board.
 *
 * Deliberately NOT [LessonNotes]: a note summarises a whole lesson for later
 * reading, whereas this answers "what is this thing I just circled" mid-class.
 * The fields are what a teacher can act on within a few seconds of reading —
 * a headline they can say out loud, a short explanation, and the transcription
 * so they can see what the model actually read off the board.
 */
data class VisualLookup(
    /** Short label for the region, e.g. "Titration curve" or "Quadratic formula". */
    val title: String,

    /** What kind of content this is; drives the icon and the search query. */
    val kind: LookupKind,

    /** Two or three sentences a teacher could read aloud. */
    val explanation: String,

    /**
     * Text/equations read off the region, verbatim.
     *
     * Shown to the teacher because handwriting recognition is the step most
     * likely to go wrong. If the transcription is visibly wrong, the
     * explanation below it can be discounted at a glance rather than trusted.
     */
    val transcription: String,

    /** Key terms worth expanding on, if any. */
    val relatedTerms: List<String>,

    /**
     * A web search query the model considers most useful for this region.
     *
     * The model writes this rather than the app concatenating the title,
     * because "titration curve equivalence point" is a far better query than
     * whatever the teacher happened to scrawl.
     */
    val searchQuery: String,

    /** True when the region could not be read; explanation says why. */
    val isUnreadable: Boolean = false,
)

enum class LookupKind {
    TEXT,
    EQUATION,
    DIAGRAM,
    CHEMISTRY,
    GEOMETRY,
    OTHER,
}
