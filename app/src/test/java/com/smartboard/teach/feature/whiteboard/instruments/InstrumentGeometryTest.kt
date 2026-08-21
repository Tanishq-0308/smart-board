package com.smartboard.teach.feature.whiteboard.instruments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentGeometryTest {

    /** A horizontal ruler edge from (0,0) to (100,0). */
    private val edge = floatArrayOf(0f, 0f, 100f, 0f)

    // --- edge projection ---

    @Test
    fun `a point above the edge projects straight down onto it`() {
        val p = InstrumentGeometry.projectOntoEdge(edge, 40f, 25f)
        assertEquals(40f, p[0], 0.01f)
        assertEquals(0f, p[1], 0.01f)
    }

    @Test
    fun `projection is clamped to the ends of the ruler`() {
        // Ink drawn past the end of a real ruler runs off it; it does not
        // continue along the line the ruler happens to lie on.
        val past = InstrumentGeometry.projectOntoEdge(edge, 500f, 10f)
        assertEquals(100f, past[0], 0.01f)

        val before = InstrumentGeometry.projectOntoEdge(edge, -500f, 10f)
        assertEquals(0f, before[0], 0.01f)
    }

    @Test
    fun `distance is measured to the segment, not the infinite line`() {
        // Directly above the middle: distance is the perpendicular.
        assertEquals(25f, InstrumentGeometry.distanceToEdge(edge, 50f, 25f), 0.01f)
        // Far beyond the end: distance grows, so it will not snap.
        assertTrue(InstrumentGeometry.distanceToEdge(edge, 400f, 0f) > 250f)
    }

    @Test
    fun `a degenerate edge does not divide by zero`() {
        val point = floatArrayOf(5f, 5f, 5f, 5f)
        assertEquals(0f, InstrumentGeometry.distanceToEdge(point, 5f, 5f), 0.01f)
        val p = InstrumentGeometry.projectOntoEdge(point, 90f, 90f)
        assertEquals(5f, p[0], 0.01f)
    }

    // --- snapping band ---

    @Test
    fun `ink near the edge snaps and ink further away does not`() {
        // The band is what lets a teacher label a diagram beside the ruler
        // without putting the ruler away first.
        assertTrue(InstrumentGeometry.shouldSnap(distanceWorld = 10f, zoom = 1f))
        assertFalse(InstrumentGeometry.shouldSnap(distanceWorld = 200f, zoom = 1f))
    }

    @Test
    fun `the snap band is a constant size on screen at any zoom`() {
        // At 2x zoom the same screen distance is half the world distance, so
        // the band must halve too or it feels twice as sticky zoomed in.
        val atOneX = InstrumentGeometry.SNAP_BAND_PX * 0.9f
        assertTrue(InstrumentGeometry.shouldSnap(atOneX, zoom = 1f))
        assertFalse(InstrumentGeometry.shouldSnap(atOneX, zoom = 2f))
        assertTrue(InstrumentGeometry.shouldSnap(atOneX / 2f, zoom = 2f))
    }

    // --- ruler edge ---

    @Test
    fun `an unrotated ruler edge runs along positive x`() {
        val ruler = Instrument(InstrumentKind.RULER, x = 10f, y = 20f, lengthCm = 10f)
        val e = InstrumentGeometry.edgeOf(ruler, zoom = 1f)
        assertEquals(10f, e[0], 0.01f)
        assertEquals(20f, e[1], 0.01f)
        assertEquals(20f, e[3], 0.01f, )
        assertTrue("edge should extend right", e[2] > e[0])
    }

    @Test
    fun `a ruler rotated a quarter turn runs down the screen`() {
        val ruler = Instrument(
            InstrumentKind.RULER,
            x = 0f, y = 0f,
            rotation = (Math.PI / 2).toFloat(),
            lengthCm = 10f,
        )
        val e = InstrumentGeometry.edgeOf(ruler, zoom = 1f)
        assertEquals("x should not move", 0f, e[2], 0.5f)
        assertTrue("edge should extend downward", e[3] > 1f)
    }

    @Test
    fun `zooming in shrinks the ruler in world units so it stays the same size on screen`() {
        val ruler = Instrument(InstrumentKind.RULER, 0f, 0f, lengthCm = 10f)
        val atOneX = InstrumentGeometry.edgeOf(ruler, zoom = 1f)[2]
        val atTwoX = InstrumentGeometry.edgeOf(ruler, zoom = 2f)[2]
        assertEquals("world length should halve at 2x", atOneX / 2f, atTwoX, 0.01f)
    }

    // --- the ruling edge is the edge that is DRAWN ---

    @Test
    fun `a set square rules along its hypotenuse, not its top`() {
        // The bug this guards: edgeOf returned the horizontal top edge for
        // every instrument, so set-square ink snapped to a line nowhere near
        // the blue edge on screen and came out as a dense zigzag.
        val square = Instrument(
            InstrumentKind.SET_SQUARE_45,
            x = 0f, y = 0f,
            lengthCm = 10f,
        )
        val e = InstrumentGeometry.edgeOf(square, zoom = 1f)
        val base = 10f * InstrumentGeometry.pxPerCm

        // Runs from the far end of the base DOWN to the far end of the height.
        assertEquals(base, e[0], 0.5f)
        assertEquals(0f, e[1], 0.5f)
        assertEquals(0f, e[2], 0.5f)
        assertEquals(base, e[3], 0.5f)
    }

    @Test
    fun `the two set squares slope differently`() {
        val ss45 = InstrumentGeometry.edgeOf(
            Instrument(InstrumentKind.SET_SQUARE_45, 0f, 0f, lengthCm = 10f),
            zoom = 1f,
        )
        val ss30 = InstrumentGeometry.edgeOf(
            Instrument(InstrumentKind.SET_SQUARE_30, 0f, 0f, lengthCm = 10f),
            zoom = 1f,
        )
        // Same base, shallower rise — otherwise they are the same tool twice.
        assertEquals(ss45[0], ss30[0], 0.5f)
        assertTrue("30/60 should be shallower", ss30[3] < ss45[3] - 1f)
    }

    @Test
    fun `a ruler still rules along its top edge`() {
        val ruler = Instrument(InstrumentKind.RULER, x = 5f, y = 7f, lengthCm = 10f)
        val e = InstrumentGeometry.edgeOf(ruler, zoom = 1f)
        assertEquals(5f, e[0], 0.01f)
        assertEquals(7f, e[1], 0.01f)
        assertEquals("edge must stay horizontal", 7f, e[3], 0.01f)
    }

    @Test
    fun `a rotated set square rules along its rotated hypotenuse`() {
        val turned = Instrument(
            InstrumentKind.SET_SQUARE_45,
            x = 0f, y = 0f,
            rotation = (Math.PI / 2).toFloat(),
            lengthCm = 10f,
        )
        val e = InstrumentGeometry.edgeOf(turned, zoom = 1f)
        val base = 10f * InstrumentGeometry.pxPerCm
        // A quarter turn maps (base,0) -> (0,base) and (0,base) -> (-base,0).
        assertEquals(0f, e[0], 0.5f)
        assertEquals(base, e[1], 0.5f)
        assertEquals(-base, e[2], 0.5f)
        assertEquals(0f, e[3], 0.5f)
    }

    // --- several instruments at once ---

    @Test
    fun `two instruments have distinct identities`() {
        // A drag has to keep hold of the one it started on, so identity
        // cannot come from the kind — two rulers are a normal setup.
        val a = Instrument(kind = InstrumentKind.RULER, x = 0f, y = 0f)
        val b = Instrument(kind = InstrumentKind.RULER, x = 0f, y = 0f)
        assertTrue("ids must differ", a.id != b.id)
    }

    @Test
    fun `ink rules against the nearest edge when two are in range`() {
        // Resting a set square on a ruler is exactly why two are on the board;
        // picking the further one would rule the line at the wrong angle.
        val near = Instrument(kind = InstrumentKind.RULER, x = 0f, y = 0f, lengthCm = 10f)
        val far = Instrument(kind = InstrumentKind.RULER, x = 0f, y = 300f, lengthCm = 10f)

        val toNear = InstrumentGeometry.distanceToEdge(
            InstrumentGeometry.edgeOf(near, 1f), 100f, 20f,
        )
        val toFar = InstrumentGeometry.distanceToEdge(
            InstrumentGeometry.edgeOf(far, 1f), 100f, 20f,
        )
        assertTrue("the nearer edge must win", toNear < toFar)
    }

    // --- readouts ---

    @Test
    fun `a right angle reads as ninety degrees`() {
        // Arms along +x and -y from the vertex.
        val angle = InstrumentGeometry.angleBetween(0f, 0f, 100f, 0f, 0f, -100f)
        assertEquals(90f, angle, 0.01f)
    }

    @Test
    fun `angle is unsigned so it never reads above one eighty`() {
        // A protractor measures the opening between two arms, not a direction,
        // so mirrored arms must read the same.
        val a = InstrumentGeometry.angleBetween(0f, 0f, 100f, 0f, -50f, -87f)
        val b = InstrumentGeometry.angleBetween(0f, 0f, 100f, 0f, -50f, 87f)
        assertEquals(a, b, 0.5f)
        assertTrue(a <= 180f)
    }

    @Test
    fun `a straight line reads as one eighty`() {
        assertEquals(
            180f,
            InstrumentGeometry.angleBetween(0f, 0f, 100f, 0f, -100f, 0f),
            0.01f,
        )
    }

    @Test
    fun `near-common angles snap so a protractor produces exact ones`() {
        assertEquals(90f, InstrumentGeometry.snapAngle(89.4f), 0.01f)
        assertEquals(45f, InstrumentGeometry.snapAngle(45.9f), 0.01f)
        // Genuinely in between: left alone, or the tool would lie.
        assertEquals(72f, InstrumentGeometry.snapAngle(72f), 0.01f)
    }

    @Test
    fun `length reads back in centimetres at the display scale`() {
        val oneCm = InstrumentGeometry.pxPerCm
        assertEquals(
            1f,
            InstrumentGeometry.lengthInCm(0f, 0f, oneCm, 0f, zoom = 1f),
            0.01f,
        )
    }

    @Test
    fun `a bogus panel density falls back rather than producing a wrong ruler`() {
        val sane = InstrumentGeometry.pxPerCm
        // Some panels report 160dpi regardless of physical size.
        InstrumentGeometry.setDisplayDensity(xdpi = 1f)
        assertEquals("should reject nonsense", sane, InstrumentGeometry.pxPerCm, 0.01f)

        InstrumentGeometry.setDisplayDensity(xdpi = 254f)   // 100 px/cm
        assertEquals(100f, InstrumentGeometry.pxPerCm, 0.5f)

        // Restore, so test order cannot affect the assertions above.
        InstrumentGeometry.setDisplayDensity(xdpi = sane * 2.54f)
    }
}
