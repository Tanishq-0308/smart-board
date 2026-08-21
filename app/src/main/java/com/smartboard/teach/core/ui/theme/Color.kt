package com.smartboard.teach.core.ui.theme

import androidx.compose.ui.graphics.Color

// Chrome — the header/sidebar surround. Deliberately dark so the white board
// area reads as the focal surface in a lit classroom.
val ChromeDark = Color(0xFF1C2530)
val ChromeDarkElevated = Color(0xFF25313F)
val ChromeBorder = Color(0xFF35434F)

val Accent = Color(0xFF2F6FED)
val AccentMuted = Color(0xFF6E9BF2)

/**
 * Soft blue-grey, not pure white.
 *
 * Matches the reference panel: a lit classroom reflects a white board back at
 * the room, and a slight tint takes the glare off without weakening ink
 * contrast. Blue and black ink both still read at full strength.
 */
val BoardSurface = Color(0xFFEDF1F8)
val BoardGrid = Color(0xFFE9EDF2)

// Floating chrome that sits over the canvas. Near-opaque rather than fully
// solid so a teacher can tell an island is hovering above their work, but
// opaque enough that ink underneath never makes a control unreadable.
val IslandSurface = Color(0xFA1C2530)
val IslandBorder = Color(0x33FFFFFF)
val IslandSurfaceLight = Color(0xFAFFFFFF)

val TextOnChrome = Color(0xFFF2F5F8)
val TextOnChromeMuted = Color(0xFF9BA9B8)
val TextOnSurface = Color(0xFF17202A)
val TextOnSurfaceMuted = Color(0xFF5C6874)

// Attendance status — chosen to stay distinguishable for colour-blind viewers,
// and always paired with a letter (P/A/L) so colour is never the only signal.
val StatusPresent = Color(0xFF1E8E5A)
val StatusAbsent = Color(0xFFC8382F)
val StatusLate = Color(0xFFCE8006)

val ErrorRed = Color(0xFFC8382F)
val WarningAmber = Color(0xFFCE8006)

/** Default pen colours offered in the tool palette. */
val PenColors: List<Color> = listOf(
    Color(0xFF17202A), // near-black
    Color(0xFF2F6FED), // blue
    Color(0xFFC8382F), // red
    Color(0xFF1E8E5A), // green
    Color(0xFFCE8006), // amber
    Color(0xFF7A3FBF), // purple
    Color(0xFFFFFFFF), // white (for dark backgrounds)
)

/** Highlighter colours — used at low alpha over ink. */
val HighlighterColors: List<Color> = listOf(
    Color(0xFFFFE14D),
    Color(0xFF7DE3A0),
    Color(0xFF8FC7FF),
    Color(0xFFFF9BC4),
)

/** Clock text drawn directly on the board, not on a dark island. */
val ClockOnBoard = Color(0xFF41505F)
val ClockOnBoardMuted = Color(0xFF7A8A9B)

/**
 * The pen popover's colour grid: two columns, six rows.
 *
 * Ordered so the left column runs neutral-to-warm and the right column carries
 * the saturated hues, matching the reference panel's arrangement — a teacher
 * reaching for "the red one" finds it in the same place every time.
 */
val PenPaletteColors: List<Color> = listOf(
    Color(0xFFFFFFFF), Color(0xFF2F6FED), // white,      blue
    Color(0xFF6B7784), Color(0xFFE8A33D), // grey,       amber
    Color(0xFF17202A), Color(0xFF17C7C7), // near-black, cyan
    Color(0xFFF07167), Color(0xFF3DA5E8), // coral,      light blue
    Color(0xFF5FD07A), Color(0xFF1E8E5A), // green,      deep green
    Color(0xFFE8479B), Color(0xFF7A3FBF), // pink,       purple
)
