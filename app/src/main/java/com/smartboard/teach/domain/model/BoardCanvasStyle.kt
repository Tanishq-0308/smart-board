package com.smartboard.teach.domain.model

/**
 * The paper a page is drawn on, matching the reference panel's grid row.
 *
 * Drawn PROCEDURALLY in world space rather than tiled from an image: the
 * canvas is infinite, so there is no bitmap that could cover it, and a
 * world-anchored lattice keeps a square the same square when the board is
 * panned — which is the whole point of squared paper for a graph.
 */
enum class GridStyle {
    /** Plain paper. */
    NONE,

    /** Fine squares — the reference's "Thin Grid". */
    THIN,

    /** Fine squares with a heavier line every fifth — "Mix Grid". */
    MIX,

    /** Bold squares only — "Square Grid". */
    SQUARE,

    /** Dots at the lattice points, no lines. */
    DOTTED,

    /** Horizontal rules for handwriting — "Trace Grid". */
    LINED,

    /**
     * Diagonals over squares, for rangoli and symmetry work.
     *
     * The reference labels this "swastika grid" — in Indian classrooms this is
     * the rangoli lattice used to teach symmetry and pattern, and the name
     * refers to that geometric figure.
     */
    RANGOLI,
    ;

    /** True when the style draws anything at all. */
    val isVisible: Boolean get() = this != NONE
}

/**
 * A page's paper: its colour and its grid.
 *
 * Per page rather than per session, so one lesson can hold a squared page for
 * a graph and a lined page for writing. Stored ON the page row, so a reopened
 * lesson looks the way it was left.
 */
data class BoardCanvasStyle(
    val colorArgb: Int = DEFAULT_COLOR_ARGB,
    val grid: GridStyle = GridStyle.NONE,
    /** Grid line colour; derived from the paper unless a teacher overrides. */
    val gridColorArgb: Int? = null,
    /** Base square size in world units at 100% zoom. */
    val spacingWorld: Float = DEFAULT_SPACING,
) {
    companion object {
        /** The existing board surface, so upgrading changes nothing on screen. */
        const val DEFAULT_COLOR_ARGB: Int = 0xFFEDF1F7.toInt()

        /** ~1cm at the panel's density; big enough to write inside. */
        const val DEFAULT_SPACING: Float = 48f

        /**
         * Paper colours from the reference: six pale tints, then six deep
         * ones. A board is looked at for an hour at a time, so these are
         * muted rather than saturated.
         */
        val PALETTE: List<Int> = listOf(
            0xFFEDF1F7.toInt(), // paper white
            0xFFF7E4EC.toInt(), // pale pink
            0xFFE6F2E3.toInt(), // pale green
            0xFFDCEEF7.toInt(), // pale blue
            0xFFE9E4F7.toInt(), // pale violet
            0xFFF9E2F3.toInt(), // pale magenta
            0xFF2B3038.toInt(), // charcoal
            0xFF4A3535.toInt(), // deep maroon
            0xFF2E3F38.toInt(), // deep green
            0xFF1E3A5F.toInt(), // quiet blue
            0xFF33356B.toInt(), // deep indigo
            0xFF3A2B4A.toInt(), // deep violet
        )
    }
}

/**
 * Grid lines on a dark paper must be LIGHTER, on a pale paper DARKER.
 *
 * A single fixed grid colour disappears on half the palette — which is exactly
 * what a teacher would notice first after switching to the dark blue paper the
 * reference screenshots use.
 */
fun defaultGridColor(paperArgb: Int): Int =
    if (isDarkColor(paperArgb)) 0x33FFFFFF else 0x22000000

/**
 * Perceived lightness, not a plain average.
 *
 * Green contributes far more to how light a colour looks than blue does, so
 * averaging the channels calls the reference's quiet blue "light" and picks an
 * invisible grid for it.
 */
fun isDarkColor(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (r * 299 + g * 587 + b * 114) / 1000 < 128
}

/**
 * Grid spacing for the current zoom, doubling as the board is zoomed out.
 *
 * Without this, zooming out turns the grid into a solid grey wash and costs
 * thousands of draw calls for lines that are a pixel apart. Doubling keeps the
 * on-screen gap in a legible band at any zoom, and because it only ever
 * doubles, every line drawn is still a line of the original lattice.
 */
fun gridSpacingForZoom(baseSpacingWorld: Float, zoom: Float, minScreenGapPx: Float = 14f): Float {
    if (baseSpacingWorld <= 0f) return 0f
    var spacing = baseSpacingWorld
    val safeZoom = zoom.coerceAtLeast(0.001f)
    // Bounded: a pathological zoom must not spin here.
    var doublings = 0
    while (spacing * safeZoom < minScreenGapPx && doublings < MAX_DOUBLINGS) {
        spacing *= 2f
        doublings++
    }
    return spacing
}

private const val MAX_DOUBLINGS = 24
