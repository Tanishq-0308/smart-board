package com.smartboard.teach.feature.whiteboard.instruments

import com.smartboard.teach.feature.whiteboard.Camera
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

/**
 * Guards the compass's geometry.
 *
 * The tool and the circle it draws come from one number — the leg spread — so
 * these tests exist to keep them from drifting apart. A compass whose drawn
 * legs disagree with its preview circle teaches the wrong thing about how a
 * circle is constructed, and nothing about it looks broken.
 */
class CompassTest {

    private val camera = Camera()

    private fun compass(spread: Float = 0.42f, rotation: Float = (PI / 2).toFloat()) =
        Instrument(
            kind = InstrumentKind.COMPASS,
            x = 500f,
            y = 300f,
            rotation = rotation,
            spreadRad = spread,
        )

    @Test
    fun `the radius is half the distance between the leg tips`() {
        val c = compass()
        val needle = compassNeedleTip(c, camera)
        val pencil = compassPencilTip(c, camera)
        val half = hypot(pencil.x - needle.x, pencil.y - needle.y) / 2f
        assertEquals(half, c.compassRadius, 0.5f)
    }

    @Test
    fun `opening the legs widens the circle`() {
        assertTrue(compass(spread = 0.9f).compassRadius > compass(spread = 0.3f).compassRadius)
    }

    @Test
    fun `a closed compass draws nothing`() {
        // Not negative, and not some minimum it never actually reaches.
        assertEquals(0f, compass(spread = 0f).compassRadius, 0.01f)
    }

    @Test
    fun `both legs hang the same distance from the hinge`() {
        val c = compass()
        val hinge = compassHinge(c, camera)
        val toNeedle = compassNeedleTip(c, camera).let { hypot(it.x - hinge.x, it.y - hinge.y) }
        val toPencil = compassPencilTip(c, camera).let { hypot(it.x - hinge.x, it.y - hinge.y) }
        assertEquals(toNeedle, toPencil, 0.5f)
    }

    @Test
    fun `a press on the pencil tip sweeps`() {
        val c = compass()
        val tip = compassPencilTip(c, camera)
        assertEquals(InstrumentDrag.SWEEP, compassDragFor(c, camera, tip))
    }

    @Test
    fun `a press on the needle tip sets the spread`() {
        val c = compass()
        val tip = compassNeedleTip(c, camera)
        assertEquals(InstrumentDrag.SPREAD, compassDragFor(c, camera, tip))
    }

    @Test
    fun `a press on the hinge moves the whole compass`() {
        val c = compass()
        assertEquals(InstrumentDrag.MOVE, compassDragFor(c, camera, Offset(c.x, c.y)))
    }

    @Test
    fun `the space between the legs belongs to the board`() {
        // Otherwise the inside of every circle a teacher draws becomes
        // undrawable, which is far worse than a slightly fiddly grab.
        val c = compass()
        val needle = compassNeedleTip(c, camera)
        val pencil = compassPencilTip(c, camera)
        val midpoint = Offset((needle.x + pencil.x) / 2f, (needle.y + pencil.y) / 2f)
        assertEquals(InstrumentDrag.NONE, compassDragFor(c, camera, midpoint))
    }

    @Test
    fun `an untouched compass has swept nothing`() {
        assertTrue(!compass().hasSweep)
    }

    @Test
    fun `a swept compass reports its arc`() {
        assertTrue(compass().copy(sweepStart = 0f, sweepEnd = 1.5f).hasSweep)
    }
}
