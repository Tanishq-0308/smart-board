package com.smartboard.teach.domain.repository

import android.graphics.Bitmap
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.domain.model.LessonNotes

/**
 * Turns a board snapshot into structured lesson notes.
 *
 * This interface exists so the Phase 2 change is a binding swap, exactly like
 * the ERP repositories. Phase 1 calls OpenAI directly with a key compiled into
 * the APK; Phase 2 must route through a server proxy on the school's backend
 * so the key never ships to a device. Nothing above this interface changes.
 */
interface NotesAiService {

    /** False when no API key is configured, so the UI can say something useful. */
    val isConfigured: Boolean

    /** Model identifier recorded on the note for provenance. */
    val modelName: String

    suspend fun summarizeBoard(snapshot: Bitmap): AppResult<LessonNotes>
}
