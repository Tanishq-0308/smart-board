package com.smartboard.teach.data.file

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.smartboard.teach.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds cropped board regions that are handed to ANOTHER app (Google Lens,
 * Photos, a browser) via a share intent.
 *
 * Unlike [NotesFileStore] this writes to `cacheDir`, not `filesDir`, and for a
 * specific reason: these crops are transient hand-offs, not user data. The
 * system may reclaim cacheDir under storage pressure, which is exactly the
 * right lifetime for a file whose only job is to survive long enough for
 * another app to read it.
 *
 * A FileProvider URI is mandatory here — passing a `file://` URI across an
 * app boundary throws FileUriExposedException on every API level this app
 * supports.
 */
@Singleton
class LookupCropStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private fun cropDir(): File = File(context.cacheDir, CROP_DIR).apply { mkdirs() }

    /**
     * Writes [bitmap] and returns a URI another app can read.
     *
     * Old crops are swept first so a day of lessons does not accumulate
     * hundreds of orphaned images. The sweep is best-effort: a file the
     * receiving app still holds open simply survives to the next sweep.
     */
    suspend fun writeShareableCrop(bitmap: Bitmap): Uri = withContext(ioDispatcher) {
        sweepOldCrops()

        val file = File(cropDir(), "region-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, SHARE_QUALITY, out)
        }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun sweepOldCrops() {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        cropDir().listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) runCatching { file.delete() }
        }
    }

    private companion object {
        const val CROP_DIR = "lookup-crops"

        /** High: the receiving app may run its own OCR over this. */
        const val SHARE_QUALITY = 95

        /** One teaching day. */
        const val MAX_AGE_MS = 12 * 60 * 60 * 1000L
    }
}
