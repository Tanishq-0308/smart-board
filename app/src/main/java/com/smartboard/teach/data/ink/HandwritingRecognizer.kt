package com.smartboard.teach.data.ink

import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.domain.model.Stroke
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What the text pen is currently able to do. */
sealed interface RecognizerState {
    data object Idle : RecognizerState
    data object Downloading : RecognizerState
    data object Ready : RecognizerState
    data class Unavailable(val message: String) : RecognizerState
}

/**
 * Handwriting to text, on device.
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
class HandwritingRecognizer @Inject constructor() {

    private var recognizer: DigitalInkRecognizer? = null
    private var model: DigitalInkRecognitionModel? = null

    /**
     * Ensures the model is on the device, downloading it if not.
     *
     * Safe to call repeatedly: once [recognizer] exists this returns
     * immediately, so the per-conversion path costs nothing.
     */
    suspend fun prepare(): AppResult<Unit> {
        recognizer?.let { return AppResult.Success(Unit) }

        val identifier = try {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag(LANGUAGE_TAG)
        } catch (error: MlKitException) {
            null
        } ?: return AppResult.Failure(
            AppError.Storage("Handwriting recognition is not available on this panel."),
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
            // able to fetch a one-off 20MB model rather than silently failing.
            val conditions = DownloadConditions.Builder().build()
            val ok = suspendCancellableCoroutine { cont ->
                manager.download(built, conditions)
                    .addOnSuccessListener { cont.resume(true) }
                    .addOnFailureListener { cont.resume(false) }
            }
            if (!ok) {
                return AppResult.Failure(
                    AppError.Storage(
                        "Could not download the handwriting model. Connect to a network and try again.",
                    ),
                )
            }
        }

        model = built
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(built).build(),
        )
        return AppResult.Success(Unit)
    }

    /**
     * Recognises [strokes] as a line of text.
     *
     * Coordinates are passed in SCREEN space by the caller. ML Kit's model was
     * trained on writing at a natural on-screen size, so feeding it world
     * coordinates from a zoomed-out board would present handwriting at a scale
     * it has never seen.
     */
    suspend fun recognize(strokes: List<Stroke>): AppResult<String> {
        val engine = recognizer
            ?: return AppResult.Failure(AppError.Storage("Handwriting model is not ready."))
        if (strokes.isEmpty()) return AppResult.Success("")

        val inkBuilder = Ink.builder()
        strokes.forEach { stroke ->
            val strokeBuilder = Ink.Stroke.builder()
            for (i in 0 until stroke.pointCount) {
                strokeBuilder.addPoint(Ink.Point.create(stroke.x(i), stroke.y(i)))
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }

        return suspendCancellableCoroutine { cont ->
            engine.recognize(inkBuilder.build())
                .addOnSuccessListener { result ->
                    // Highest-scoring candidate; the rest are alternatives the
                    // board has nowhere to show.
                    cont.resume(AppResult.Success(result.candidates.firstOrNull()?.text.orEmpty()))
                }
                .addOnFailureListener { error ->
                    cont.resume(
                        AppResult.Failure(
                            AppError.Storage("Could not read that handwriting: ${error.message}"),
                        ),
                    )
                }
        }
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }

    private companion object {
        /**
         * English only for now.
         *
         * ML Kit ships models for many languages including Devanagari; adding
         * them is a language picker plus another download, not new plumbing.
         */
        const val LANGUAGE_TAG = "en-US"
    }
}
