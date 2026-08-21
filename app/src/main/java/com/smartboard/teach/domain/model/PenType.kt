package com.smartboard.teach.domain.model

/**
 * The five nibs offered in the pen popover.
 *
 * Deliberately NOT new [DrawTool] values. A pen type only decides how a
 * freehand stroke is styled — width, alpha, pressure response — so it maps
 * onto the existing PEN and HIGHLIGHTER tools and leaves shape recognition,
 * the eraser hit-test and stroke serialization completely untouched.
 *
 * Each type carries its own remembered colour and width (see BoardState), so
 * switching nib mid-lesson restores what that nib was last set to rather than
 * inheriting the previous one's settings.
 */
enum class PenType(
    val label: String,
    /** Which underlying tool this nib draws with. */
    val tool: DrawTool,
    val defaultWidth: Float,
    val defaultAlpha: Float,
    val pressureSensitive: Boolean,
) {
    /** Even, opaque line. The default, and what most teaching is written with. */
    PEN("Pen", DrawTool.PEN, defaultWidth = 6f, defaultAlpha = 1f, pressureSensitive = true),

    /** Thicker and flat: headings and circling an answer. */
    MARKER("Marker", DrawTool.PEN, defaultWidth = 14f, defaultAlpha = 1f, pressureSensitive = false),

    /** Translucent, so ink underneath still reads. */
    HIGHLIGHTER(
        "Highlighter",
        DrawTool.HIGHLIGHTER,
        defaultWidth = 28f,
        defaultAlpha = 0.35f,
        pressureSensitive = false,
    ),

    /** Strong pressure response — thin on a light touch, broad when pressed. */
    FOUNTAIN(
        "Fountain",
        DrawTool.PEN,
        defaultWidth = 8f,
        defaultAlpha = 1f,
        pressureSensitive = true,
    ),

    /** Soft and slightly transparent, for shading rather than writing. */
    BRUSH("Brush", DrawTool.PEN, defaultWidth = 18f, defaultAlpha = 0.75f, pressureSensitive = true);

    val isHighlighter: Boolean get() = this == HIGHLIGHTER
}
