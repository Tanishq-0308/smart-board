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
    /** The script this language is written in, when it is not Latin. */
    val script: Character.UnicodeScript? = null,
    /** Rough size of the handwriting model download, in MB, for the user. */
    val inkModelMb: Int,
) {
    // Long enough to cover the natural gap between words, so a phrase like
    // "hello world" converts as one line rather than word by word.
    ENGLISH("en", R.string.lang_english, listOf("en-US"), inkPauseMs = 1_500, inkModelMb = 20),
    HINDI(
        "hi", R.string.lang_hindi, listOf("hi", "en-US"), inkPauseMs = 2_000,
        script = Character.UnicodeScript.DEVANAGARI, inkModelMb = 40,
    ),
    ;

    companion object {
        val DEFAULT = ENGLISH

        fun fromCode(code: String?): BoardLanguage = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

/** Picking between several models' readings of the same ink. */
object InkChoice {

    /**
     * @param readings each model's reading, in the language's model order
     *        (its own script's model first).
     * @param script the language's own script, or null for a Latin language.
     *
     * Model scores are NOT compared: each model scores on its own scale (it
     * grows with the text's length), so "lower wins" picked the Hindi model's
     * garbled Latin over the English model's correct reading. Instead the
     * language's own model is trusted when its reading is in its own script,
     * and otherwise the line was written in English and the next model's
     * reading is used. A mixed line ("Force = बल") contains Devanagari, so it
     * stays with the Hindi reading.
     */
    fun best(readings: List<String>, script: Character.UnicodeScript?): String {
        val usable = readings.map { it.trim() }.filter { it.isNotEmpty() }
        if (usable.isEmpty()) return ""
        val primary = readings.first().trim()
        if (script == null || primary.isEmpty()) return usable.first()
        val inOwnScript = primary.any { Character.UnicodeScript.of(it.code) == script }
        return if (inOwnScript) primary else usable.firstOrNull { it != primary } ?: primary
    }
}
