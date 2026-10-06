package com.smartboard.teach.data.ink

import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.RecognitionContext
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.smartboard.teach.R
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.core.util.AppText
import com.smartboard.teach.domain.engine.BoardLanguage
import com.smartboard.teach.domain.engine.InkChoice
import com.smartboard.teach.domain.engine.InkRecognizer
import com.smartboard.teach.domain.model.Stroke
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Handwriting to text, on device, in any language in BoardLanguage.
 *
 * ML Kit Digital Ink rather than a vision model: it runs offline, costs
 * nothing per use, and returns in tens of milliseconds — a teacher writing at
 * the board cannot wait on a network round trip, and a panel in a classroom
 * with dead Wi-Fi must still work.
 *
 * The English model is UNBUNDLED and downloads on first use. That keeps the
 * APK small and means panels where the text pen is never touched never pay
 * for it; after one download it works offline forever.
 */
@Singleton
class MlKitInkRecognizer @Inject constructor() : InkRecognizer {

    /** One client per model tag ("en-US", "hi"), created once its model is on the board. */
    private val recognizers = mutableMapOf<String, DigitalInkRecognizer>()

    /**
     * Ensures every model [language] reads with is on the device, downloading
     * any that are missing. Safe to call repeatedly: ready models cost nothing.
     */
    override suspend fun prepare(language: BoardLanguage): AppResult<Unit> {
        for (tag in language.inkModels) {
            if (tag in recognizers) continue
            val identifier = try {
                DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)
            } catch (error: MlKitException) {
                null
            } ?: return AppResult.Failure(
                AppError.Storage(AppText.get(R.string.error_ink_unavailable_panel)),
            )

            val built = DigitalInkRecognitionModel.builder(identifier).build()
            val manager = RemoteModelManager.getInstance()

            val downloaded = suspendCancellableCoroutine { cont ->
                manager.isModelDownloaded(built)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(false) }
            }

            if (!downloaded) {
                // Wi-Fi not required: a panel on a metered hotspot should still be
                // able to fetch a one-off model rather than silently failing.
                val conditions = DownloadConditions.Builder().build()
                val ok = suspendCancellableCoroutine { cont ->
                    manager.download(built, conditions)
                        .addOnSuccessListener { cont.resume(true) }
                        .addOnFailureListener { cont.resume(false) }
                }
                if (!ok) {
                    return AppResult.Failure(AppError.Storage(AppText.get(R.string.error_ink_download)))
                }
            }

            recognizers[tag] = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(built).build(),
            )
        }
        return AppResult.Success(Unit)
    }

    /**
     * Recognises [strokes] as a line of text.
     *
     * Coordinates are passed in SCREEN space by the caller. ML Kit's model was
     * trained on writing at a natural on-screen size, so feeding it world
     * coordinates from a zoomed-out board would present handwriting at a scale
     * it has never seen.
     *
     * A language with several models (Hindi reads with Hindi and English, for
     * mixed lines) runs each and keeps the reading in the right script; see InkChoice.
     */
    override suspend fun recognize(strokes: List<Stroke>, language: BoardLanguage, preContext: String): AppResult<String> {
        val engines = language.inkModels.mapNotNull { recognizers[it] }
        if (engines.isEmpty()) {
            return AppResult.Failure(AppError.Storage(AppText.get(R.string.error_ink_not_ready)))
        }
        if (strokes.isEmpty()) return AppResult.Success("")

        val inkBuilder = Ink.builder()
        strokes.forEach { stroke ->
            val strokeBuilder = Ink.Stroke.builder()
            for (i in 0 until stroke.pointCount) {
                strokeBuilder.addPoint(Ink.Point.create(stroke.x(i), stroke.y(i)))
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }
        val ink = inkBuilder.build()
        // The words just written, so a new word is read in context (ML Kit uses
        // up to the last 20 characters).
        val context = RecognitionContext.builder().setPreContext(preContext.takeLast(PRE_CONTEXT_CHARS)).build()

        val readings = ArrayList<String>(engines.size)
        var failure: String? = null
        for (engine in engines) {
            suspendCancellableCoroutine { cont ->
                engine.recognize(ink, context)
                    .addOnSuccessListener { result ->
                        readings += result.candidates.firstOrNull()?.text.orEmpty()
                        cont.resume(Unit)
                    }
                    .addOnFailureListener { error ->
                        failure = error.message.orEmpty()
                        cont.resume(Unit)
                    }
            }
        }
        if (readings.isEmpty() && failure != null) {
            return AppResult.Failure(AppError.Storage(AppText.get(R.string.error_ink_read, failure.orEmpty())))
        }
        return AppResult.Success(InkChoice.best(readings, language.script))
    }

    private companion object {
        const val PRE_CONTEXT_CHARS = 20
    }

    override fun close() {
        recognizers.values.forEach { it.close() }
        recognizers.clear()
    }
}
