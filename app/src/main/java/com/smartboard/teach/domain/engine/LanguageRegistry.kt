package com.smartboard.teach.domain.engine

import androidx.annotation.StringRes
import com.smartboard.teach.R

/**
 * A language the board can work in, and what each engine needs for it.
 *
 * Adding a language is an entry here plus its strings; features look things
 * up here instead of hard-coding "en-US". Fonts: Android ships Noto for
 * Devanagari and the other Indian scripts, so none is bundled yet.
 */
enum class BoardLanguage(
    /** BCP-47 code, also the key it is saved under. */
    val code: String,
    @StringRes val label: Int,
    /**
     * ML Kit Digital Ink models, best first. More than one means each line is
     * read by all of them and the most confident reading wins, so a mixed
     * line like "Force = बल" can still convert.
     */
    val inkModels: List<String>,
    /**
     * How long the pen must rest before a word converts. Devanagari needs
     * longer: matras and the shirorekha (headline) are written after the
     * letters, and converting early splits the word.
     */
    val inkPauseMs: Long,
    val rightToLeft: Boolean = false,
    /** Rough size of the handwriting model download, in MB, for the user. */
    val inkModelMb: Int,
) {
    ENGLISH("en", R.string.lang_english, listOf("en-US"), inkPauseMs = 900, inkModelMb = 20),
    HINDI("hi", R.string.lang_hindi, listOf("hi", "en-US"), inkPauseMs = 1_700, inkModelMb = 40),
    ;

    companion object {
        val DEFAULT = ENGLISH

        fun fromCode(code: String?): BoardLanguage = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

/** Picking between several models' readings of the same ink. */
object InkChoice {

    /**
     * @param readings (text, score) per model, in the language's model order.
     *        ML Kit scores are "lower is more confident" and only some models
     *        give one.
     * @return the most confident non-empty reading; with no scores, the first
     *         model's (the language's own script) wins.
     */
    fun best(readings: List<Pair<String, Float?>>): String {
        val usable = readings.filter { it.first.isNotBlank() }
        if (usable.isEmpty()) return ""
        val scored = usable.filter { it.second != null }
        return if (scored.size == usable.size && scored.size > 1) {
            scored.minBy { it.second!! }.first
        } else {
            usable.first().first
        }
    }
}
