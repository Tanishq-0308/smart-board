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
    fun `the radius is the distance from needle to pencil`() {
        // Needle at the centre, pencil on the circle — as on the real tool.
        val c = compass()
        val needle = compassNeedleTip(c, camera)
        val pencil = compassPencilTip(c, camera)
        assertEquals(hypot(pencil.x - needle.x, pencil.y - needle.y), c.compassRadius, 0.5f)
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
    fun `the needle tip and the grip move the compass`() {
        val c = compass()
        assertEquals(InstrumentDrag.MOVE, compassDragFor(c, camera, compassNeedleTip(c, camera)))
        assertEquals(InstrumentDrag.MOVE, compassDragFor(c, camera, compassParts(c, camera).grip))
    }

    @Test
    fun `every compass button does its own job`() {
        val c = compass()
        val p = compassParts(c, camera)
        assertEquals(InstrumentDrag.ROTATE, compassDragFor(c, camera, p.hinge))
        assertEquals(InstrumentDrag.SPREAD, compassDragFor(c, camera, p.spreadButton))
        assertEquals(InstrumentDrag.SWAP, compassDragFor(c, camera, p.swapButton))
        assertEquals(InstrumentDrag.CLOSE, compassDragFor(c, camera, p.close))
    }

    @Test
    fun `opening the legs keeps the needle planted and the button under the finger`() {
        val c = compass()
        val needle = compassNeedleTip(c, camera)
        val finger = compassParts(c.copy(spreadRad = 1.1f), camera).spreadButton
        // Measured from where the needle is, not from that hypothetical copy.
        val target = compassParts(compassKeepingNeedle(c, c.rotation, 1.1f), camera).spreadButton
        val opened = compassSpreadTo(c, camera, target)
        assertEquals(1.1f, opened.spreadRad, 0.01f)
        val after = compassNeedleTip(opened, camera)
        assertEquals(needle.x, after.x, 0.01f)
        assertEquals(needle.y, after.y, 0.01f)
        assertTrue(finger.x.isFinite())
    }

    @Test
    fun `turning the compass spins it about its middle`() {
        val c = compass()
        val middle = instrumentPivot(c, camera)
        val turned = rotatedAboutPivot(c, 0.4f, camera)
        val after = instrumentPivot(turned, camera)
        assertEquals(middle.x, after.x, 0.01f)
        assertEquals(middle.y, after.y, 0.01f)
        assertEquals(0.4f, turned.rotation, 0f)
        // And the hinge is not the pivot, so a drag starting on ↺ has a
        // direction from the first move.
        assertTrue(hypot(c.x - middle.x, c.y - middle.y) > 50f)
    }

    @Test
    fun `swapping trades needle and pencil`() {
        val c = compass()
        val s = c.copy(swapped = true)
        assertEquals(compassPencilTip(c, camera).x, compassNeedleTip(s, camera).x, 0.01f)
        assertEquals(compassNeedleTip(c, camera).x, compassPencilTip(s, camera).x, 0.01f)
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

    @Test
    fun `a leg opens the compass from anywhere along it`() {
        val c = compass(spread = 0.9f)
        val p = compassParts(c, camera)
        fun point(tip: Offset, t: Float) = Offset(p.hinge.x + (tip.x - p.hinge.x) * t, p.hinge.y + (tip.y - p.hinge.y) * t)
        assertEquals(InstrumentDrag.SPREAD, compassDragFor(c, camera, point(p.pencil, 0.5f)))
        assertEquals(InstrumentDrag.SPREAD_NEEDLE, compassDragFor(c, camera, point(p.needle, 0.5f)))
    }

    @Test
    fun `opening by the needle leg keeps the pencil planted`() {
        val c = compass(spread = 0.9f)
        val pencil = compassPencilTip(c, camera)
        val p = compassParts(c, camera)
        val grab = Offset(p.hinge.x + (p.needle.x - p.hinge.x) * 0.5f, p.hinge.y + (p.needle.y - p.hinge.y) * 0.5f)
        val along = compassGrabFraction(c, camera, grab, byNeedleLeg = true)
        assertEquals(0.5f, along, 0.01f)
        val opened = compassSpreadTo(c, camera, Offset(grab.x - 80f, grab.y), along, byNeedleLeg = true)
        assertTrue(opened.spreadRad > c.spreadRad)
        val after = compassPencilTip(opened, camera)
        assertEquals(pencil.x, after.x, 0.01f)
        assertEquals(pencil.y, after.y, 0.01f)
    }
}
