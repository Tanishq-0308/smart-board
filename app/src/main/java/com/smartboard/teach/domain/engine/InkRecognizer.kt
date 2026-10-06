package com.smartboard.teach.domain.engine

import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.domain.model.Stroke

/** What the text pen is currently able to do. */
sealed interface RecognizerState {
    data object Idle : RecognizerState
    data object Downloading : RecognizerState
    data object Ready : RecognizerState
    data class Unavailable(val message: String) : RecognizerState
}

/**
 * Handwriting to text. Features depend on this, never on a vendor SDK, so a
 * second engine (another language, another vendor) is a binding in
 * di/EngineModule rather than a change to the board.
 */
interface InkRecognizer {
    /** Makes [language] usable, fetching its models if needed. Cheap once ready. */
    suspend fun prepare(language: BoardLanguage): AppResult<Unit>

    /** Recognises [strokes], given in SCREEN coordinates, as one line of text in [language]. */
    suspend fun recognize(strokes: List<Stroke>, language: BoardLanguage): AppResult<String>

    fun close()
}
