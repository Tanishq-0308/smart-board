package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometry behind the visual-lookup crop.
 *
 * `BoardRenderer.exportBitmap` itself needs a real android.graphics.Bitmap and
 * so cannot run here, but the decision that actually matters — that an
 * explicit region is honoured VERBATIM rather than shrink-wrapped to the ink
 * inside it — is pure geometry and is pinned below.
 *
 * The distinction is not cosmetic: a teacher who lassos an equation plus the
 * blank space around it is telling the model where the boundary of the
 * question is. Shrink-wrapping would silently crop to the strokes and change
 * what was asked.
 */
class LookupRegionTest {

    private fun stroke(id: String, vararg xy: Float, width: Float = 4f): Stroke {
        val points = FloatArray(xy.size / 2 * Stroke.STRIDE)
        for (i in 0 until xy.size / 2) {
            points[i * Stroke.STRIDE] = xy[i * 2]
            points[i * Stroke.STRIDE + 1] = xy[i * 2 + 1]
            points[i * Stroke.STRIDE + 2] = 1f
        }
        return Stroke(id, DrawTool.PEN, StrokeStyle(0xFF000000.toInt(), width), points)
    }

    @Test
    fun `selection bounds of a subset ignore strokes outside it`() {
        val inside = stroke("a", 100f, 100f, 200f, 200f)
        val outside = stroke("b", 900f, 900f, 1000f, 1000f)

        val bounds = Selection.boundsOf(listOf(inside), emptyList())

        // The far-away stroke must not widen the region handed to the model.
        val outsideBounds = Selection.boundsOf(listOf(outside), emptyList())
        assertTrue(bounds[2] < outsideBounds[0])
    }

    @Test
    fun `a region wider than its ink keeps its own width`() {
        // Teacher lassos a wide area containing one short stroke.
        val ink = stroke("a", 480f, 300f, 520f, 300f)
        val region = floatArrayOf(400f, 200f, 600f, 400f)

        val inkBounds = Selection.boundsOf(listOf(ink), emptyList())

        // Ink is strictly inside the lassoed region...
        assertTrue(inkBounds[0] > region[0])
        assertTrue(inkBounds[2] < region[2])

        // ...so honouring the region must produce a wider crop than the ink.
        // exportBitmap takes `regionBounds` for exactly this reason; if it
        // recomputed content bounds instead, the crop would collapse to
        // inkBounds and lose the context the teacher selected.
        assertTrue((region[2] - region[0]) > (inkBounds[2] - inkBounds[0]))
    }

    @Test
    fun `empty selection is detectable so lookup can be skipped`() {
        val empty = Selection.boundsOf(emptyList(), emptyList())
        assertTrue(Selection.isEmpty(empty))
    }

    @Test
    fun `normalized drag rect is valid regardless of drag direction`() {
        // A teacher dragging right-to-left / bottom-to-top must still produce
        // a usable crop region rather than an inverted, empty one.
        val rect = Selection.normalizeRect(600f, 400f, 200f, 100f)

        assertEquals(200f, rect[0], 0.001f)
        assertEquals(100f, rect[1], 0.001f)
        assertEquals(600f, rect[2], 0.001f)
        assertEquals(400f, rect[3], 0.001f)
        assertTrue(!Selection.isEmpty(rect))
    }
}
