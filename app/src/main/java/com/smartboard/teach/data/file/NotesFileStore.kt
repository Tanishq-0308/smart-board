package com.smartboard.teach.data.file

import android.content.Context
import android.graphics.Bitmap
import com.smartboard.teach.di.IoDispatcher
import com.smartboard.teach.domain.model.LessonNotes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where notes and their source snapshots live on disk.
 *
 * App-specific internal storage (`filesDir`) is used deliberately: it needs
 * ZERO permissions on every API level 28-36. MediaStore or the Documents tree
 * would drag in IS_PENDING handling and per-OEM provider quirks that are
 * frequently broken or absent on locked-down education boards. The trade-off
 * is that notes are invisible to a file manager, which is acceptable because
 * the app itself is the browser for them — and a per-note Export action lets a
 * teacher copy one out to USB via SAF when they actually need to.
 *
 * `getExternalFilesDir` is never used: boards often have no reliable external
 * volume.
 *
 * Layout:  filesDir/notes/{noteId}/snapshot.jpg
 *          filesDir/notes/{noteId}/note.md
 */
@Singleton
class NotesFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private fun notesRoot(): File = File(context.filesDir, "notes").apply { mkdirs() }

    fun noteDir(noteId: String): File = File(notesRoot(), noteId).apply { mkdirs() }

    /**
     * Writes the board snapshot BEFORE any network call.
     *
     * This ordering is the whole reason a teacher on dead Wi-Fi does not lose
     * their board: the image is already safe on disk when the AI request
     * fails, so the notes list can offer a working Retry.
     */
    suspend fun writeSnapshot(noteId: String, bitmap: Bitmap, quality: Int = 85): File =
        withContext(ioDispatcher) {
            val file = File(noteDir(noteId), SNAPSHOT_NAME)
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            file
        }

    suspend fun writeMarkdown(noteId: String, notes: LessonNotes): File =
        withContext(ioDispatcher) {
            val file = File(noteDir(noteId), MARKDOWN_NAME)
            file.writeText(renderMarkdown(notes))
            file
        }

    suspend fun readMarkdown(path: String): String? = withContext(ioDispatcher) {
        val file = File(path)
        if (file.exists()) file.readText() else null
    }

    suspend fun deleteNote(noteId: String) = withContext(ioDispatcher) {
        noteDir(noteId).deleteRecursively()
        Unit
    }

    fun exists(path: String?): Boolean = path != null && File(path).exists()

    /**
     * Renders structured notes to Markdown locally.
     *
     * The model returns structured JSON, not Markdown, on purpose — asking for
     * Markdown loses the structure and invites formatting drift between calls.
     * Formatting is a local, deterministic concern.
     */
    fun renderMarkdown(notes: LessonNotes): String = buildString {
        appendLine("# ${notes.title}")
        appendLine()

        if (notes.summary.isNotBlank()) {
            appendLine(notes.summary)
            appendLine()
        }

        if (notes.topics.isNotEmpty()) {
            appendLine("## Topics")
            notes.topics.forEach { appendLine("- $it") }
            appendLine()
        }

        if (notes.keyPoints.isNotEmpty()) {
            appendLine("## Key points")
            notes.keyPoints.forEach { appendLine("- $it") }
            appendLine()
        }

        if (notes.definitions.isNotEmpty()) {
            appendLine("## Definitions")
            notes.definitions.forEach { appendLine("- **${it.term}** — ${it.meaning}") }
            appendLine()
        }

        if (notes.formulas.isNotEmpty()) {
            appendLine("## Formulas")
            appendLine("```")
            notes.formulas.forEach { appendLine(it) }
            appendLine("```")
            appendLine()
        }

        if (notes.followUpQuestions.isNotEmpty()) {
            appendLine("## Questions to follow up")
            notes.followUpQuestions.forEachIndexed { i, q -> appendLine("${i + 1}. $q") }
            appendLine()
        }
    }.trimEnd() + "\n"

    companion object {
        const val SNAPSHOT_NAME = "snapshot.jpg"
        const val MARKDOWN_NAME = "note.md"
    }
}
