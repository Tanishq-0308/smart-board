package com.smartboard.teach.data.file

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Where an export landed, and how to describe it to the teacher. */
data class ExportResult(val displayPath: String, val uri: Uri?)

/**
 * Writes board exports to SHARED storage, where a teacher can find them.
 *
 * Deliberately different from [NotesFileStore], which uses app-internal
 * storage because the app is the only reader of notes. An export exists to
 * leave the app — onto a USB stick, into an email, onto a projector from the
 * gallery — so it has to be somewhere a file manager can see.
 *
 * On API 29+ that is MediaStore with no permission at all. On 28 there is no
 * scoped storage, so the public directory is written directly; the manifest
 * declares WRITE_EXTERNAL_STORAGE with a maxSdkVersion so newer panels are
 * never asked for it.
 */
@Singleton
class BoardExportStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun savePng(bitmap: Bitmap, baseName: String): AppResult<ExportResult> =
        write(
            fileName = "$baseName.png",
            mimeType = "image/png",
            relativeDir = Environment.DIRECTORY_PICTURES,
            subDir = EXPORT_DIR,
        ) { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }

    /**
     * Writes [bitmap] as a single-page PDF sized to the image.
     *
     * Framework [PdfDocument] rather than a library: one image on one page is
     * exactly what it does well.
     */
    suspend fun savePdf(bitmap: Bitmap, baseName: String): AppResult<ExportResult> =
        write(
            fileName = "$baseName.pdf",
            mimeType = "application/pdf",
            relativeDir = Environment.DIRECTORY_DOCUMENTS,
            subDir = EXPORT_DIR,
        ) { out ->
            val document = PdfDocument()
            try {
                val info = PdfDocument.PageInfo
                    .Builder(bitmap.width, bitmap.height, 1)
                    .create()
                val page = document.startPage(info)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                document.finishPage(page)
                document.writeTo(out)
                true
            } finally {
                document.close()
            }
        }

    private suspend fun write(
        fileName: String,
        mimeType: String,
        relativeDir: String,
        subDir: String,
        body: (OutputStream) -> Boolean,
    ): AppResult<ExportResult> = withContext(ioDispatcher) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeDir/$subDir")
                    // Hidden from other apps until the bytes are all written,
                    // so a gallery never indexes a half-written export.
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = if (mimeType.startsWith("image/")) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val uri = context.contentResolver.insert(collection, values)
                    ?: return@withContext AppResult.Failure(
                        AppError.Storage("Could not create the export file."),
                    )

                val wrote = context.contentResolver.openOutputStream(uri)?.use(body) ?: false
                if (!wrote) {
                    context.contentResolver.delete(uri, null, null)
                    return@withContext AppResult.Failure(
                        AppError.Storage("Could not write the export."),
                    )
                }

                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
                AppResult.Success(ExportResult("$relativeDir/$subDir/$fileName", uri))
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(relativeDir), subDir)
                dir.mkdirs()
                val file = File(dir, fileName)
                val wrote = file.outputStream().use(body)
                if (!wrote) {
                    file.delete()
                    return@withContext AppResult.Failure(
                        AppError.Storage("Could not write the export."),
                    )
                }
                AppResult.Success(ExportResult(file.absolutePath, null))
            }
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage("Could not save the export: ${t.message}"))
        }
    }

    private companion object {
        /** One folder, so a term of exports is not scattered through Pictures. */
        const val EXPORT_DIR = "SmartBoard"
    }
}
