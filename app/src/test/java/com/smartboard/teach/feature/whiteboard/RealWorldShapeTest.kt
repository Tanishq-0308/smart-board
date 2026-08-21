package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the thresholds against measurements taken from strokes ACTUALLY
 * drawn by hand on the target tablet.
 *
 * The first version of the recognizer was tuned on synthetic shapes and
 * silently did nothing on the device: a real hand-drawn circle has roughly
 * twice the radial variance of a generated one. These figures come from
 * decoding the stored stroke blobs off the tablet, so they encode what a
 * person's hand actually produces rather than what a sine wave does.
 */
class RealWorldShapeTest {

    // Measured on-device. See the values in ShapeRecognizer's threshold docs.
    private val REAL_CIRCLE_CV = 0.220f
    private val REAL_SQUARE_CV = 0.156f
    private val REAL_BLOB_CV = 0.311f
    private val REAL_OPEN_ARC_CV = 0.338f

    private val REAL_CIRCLE_GAP_RATIO = 0.148f
    private val REAL_SQUARE_GAP_RATIO = 0.220f
    private val REAL_OPEN_ARC_GAP_RATIO = 0.594f

    /** Mirrors circleConfidence, which is private. */
    private fun circleConfidence(cv: Float): Float =
        (1f - cv / ShapeRecognizer.CIRCLE_CV_LIMIT).coerceIn(0f, 1f)

    @Test
    fun `a real hand-drawn circle clears the confidence threshold`() {
        val confidence = circleConfidence(REAL_CIRCLE_CV)
        assertTrue(
            "a circle drawn by hand scored $confidence, below " +
                "${ShapeRecognizer.MIN_CONFIDENCE} — snapping would never fire on device",
            confidence >= ShapeRecognizer.MIN_CONFIDENCE,
        )
    }

    @Test
    fun `a real hand-drawn square also reads as round enough to be a candidate`() {
        // It should still lose to the rectangle test, but must not be
        // rejected outright before the comparison happens.
        assertTrue(circleConfidence(REAL_SQUARE_CV) > 0f)
    }

    @Test
    fun `a shapeless blob stays below the threshold`() {
        val confidence = circleConfidence(REAL_BLOB_CV)
        assertTrue(
            "a blob scored $confidence and would be wrongly snapped",
            confidence < ShapeRecognizer.MIN_CONFIDENCE,
        )
    }

    @Test
    fun `an open arc stays below the threshold`() {
        assertTrue(circleConfidence(REAL_OPEN_ARC_CV) < ShapeRecognizer.MIN_CONFIDENCE)
    }

    @Test
    fun `real closed shapes are treated as closed`() {
        assertTrue(
            "a hand-drawn circle closes at $REAL_CIRCLE_GAP_RATIO",
            REAL_CIRCLE_GAP_RATIO < ShapeRecognizer.CLOSING_GAP_RATIO,
        )
        assertTrue(
            "a hand-drawn square closes at $REAL_SQUARE_GAP_RATIO",
            REAL_SQUARE_GAP_RATIO < ShapeRecognizer.CLOSING_GAP_RATIO,
        )
    }

    @Test
    fun `a real open arc is not treated as closed`() {
        assertTrue(REAL_OPEN_ARC_GAP_RATIO > ShapeRecognizer.CLOSING_GAP_RATIO)
    }

    @Test
    fun `real path-to-diagonal ratios stay under the scribble cutoff`() {
        // Measured 2.41-2.66 for real shapes; handwriting runs far higher.
        listOf(2.45f, 2.41f, 2.66f).forEach { ratio ->
            assertTrue(
                "a real shape at ratio $ratio must not be dismissed as a scribble",
                ratio < ShapeRecognizer.MAX_PATH_TO_DIAGONAL,
            )
        }
    }

    @Test
    fun `real strokes carry enough points to be recognised`() {
        // Measured 83-111 points for deliberate shapes.
        listOf(83, 104, 111, 85).forEach { count ->
            assertTrue(count >= ShapeRecognizer.MIN_POINTS)
        }
    }

    @Test
    fun `real shapes are large enough to be candidates`() {
        // Measured diagonals of 241-451 world units.
        listOf(241f, 406f, 451f).forEach { diagonal ->
            assertTrue(diagonal > ShapeRecognizer.MIN_SIZE)
        }
    }
}
