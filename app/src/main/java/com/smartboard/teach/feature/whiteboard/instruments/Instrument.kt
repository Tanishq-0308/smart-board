package com.smartboard.teach.feature.whiteboard.instruments

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Which instrument, if any, is on the board. */
enum class InstrumentKind {
    RULER,

    /** Isoceles right triangle — the 45/45/90 set square. */
    SET_SQUARE_45,

    /**
     * Scalene right triangle — the 30/60/90 set square.
     *
     * A separate instrument rather than a flag on one: the reference panel
     * offers both because they rule different angles, and a teacher reaches
     * for one or the other by shape.
     */
    SET_SQUARE_30,

    PROTRACTOR,
    COMPASS,
}

/**
 * True for instruments with a straight edge to rule against.
 *
 * The protractor measures and the compass sweeps its own arc; neither
 * constrains a freehand stroke, so ink near them draws normally.
 */
val InstrumentKind.hasRulingEdge: Boolean
    get() = this == InstrumentKind.RULER ||
        this == InstrumentKind.SET_SQUARE_45 ||
        this == InstrumentKind.SET_SQUARE_30

/** True for the two set squares, which share geometry but differ in shape. */
val InstrumentKind.isSetSquare: Boolean
    get() = this == InstrumentKind.SET_SQUARE_45 || this == InstrumentKind.SET_SQUARE_30

/**
 * Height of a set square as a fraction of its base.
 *
 * 1.0 gives the 45/45/90; tan(30) gives the 30/60/90, so the two are visibly
 * different tools rather than the same triangle twice.
 */
val InstrumentKind.setSquareRatio: Float
    get() = if (this == InstrumentKind.SET_SQUARE_30) 0.577f else 1f

/**
 * A geometry instrument lying on the board.
 *
 * Position and rotation are in WORLD coordinates, like everything else on the
 * canvas, so an instrument stays where the teacher put it when the board is
 * panned. Its SIZE, however, is fixed in physical centimetres — see
 * [InstrumentGeometry.PX_PER_CM] — because a ruler that grew when you zoomed
 * would no longer measure anything.
 */
data class Instrument(
    val kind: InstrumentKind,
    /** World position of the instrument's anchor (see [InstrumentGeometry]). */
    val x: Float,
    val y: Float,
    /** Radians, clockwise. */
    val rotation: Float = 0f,
    /** Length in cm for the ruler; leg length for the set square. */
    val lengthCm: Float = 10f,
    /** Compass only: radius in world units, and where the sweep has reached. */
    val radiusWorld: Float = 240f,
    val sweepStart: Float = 0f,
    val sweepEnd: Float = 0f,
    /** Mirrored, for the flip control on the ruler and set square. */
    val flipped: Boolean = false,
    /**
     * Stable identity, so a drag keeps hold of the instrument it started on.
     *
     * Last in the list so positional construction — `Instrument(kind, x, y)` —
     * still reads naturally at every call site.
     */
    val id: String = java.util.UUID.randomUUID().toString(),
)

/**
 * The maths behind the instruments.
 *
 * Pure functions with no Android types, so edge projection, angle readout and
 * snapping are unit-testable. Getting these subtly wrong produces a ruler that
 * draws lines slightly off its own edge — visible to a class, and very hard to
 * diagnose from a screenshot.
 */
object InstrumentGeometry {

    /**
     * Screen pixels per physical centimetre.
     *
     * Set once from the panel's reported DPI so a line measured with the
     * on-screen ruler matches a real ruler held against the glass — which is
     * the entire reason to teach with one. Falls back to a sane default when
     * a panel reports nonsense (some report 160dpi regardless of size).
     */
    var pxPerCm: Float = DEFAULT_PX_PER_CM
        private set

    fun setDisplayDensity(xdpi: Float) {
        val candidate = xdpi / CM_PER_INCH
        // A panel claiming under 20 or over 200 px/cm is not telling the
        // truth; a wrong ruler is worse than a nominal one.
        pxPerCm = if (candidate in 20f..200f) candidate else DEFAULT_PX_PER_CM
    }

    /**
     * The straight edge ink snaps to, as two world points.
     *
     * For the ruler this is its top edge; for the set square, the hypotenuse
     * — the edges a teacher actually rules against.
     */
    fun edgeOf(instrument: Instrument, zoom: Float): FloatArray {
        val baseWorld = instrument.lengthCm * pxPerCm / zoom
        val cos = cos(instrument.rotation)
        val sin = sin(instrument.rotation)

        // Local coordinates of the ruling edge, before rotation.
        //
        // A ruler rules along its TOP edge, but a set square rules along its
        // HYPOTENUSE — the sloping side. Returning the top edge for both made
        // set-square ink snap to a line that was nowhere near the blue edge
        // drawn on screen, and the smoothing filter then fought the projection
        // and produced a dense zigzag instead of a straight line.
        val x0: Float
        val y0: Float
        val x1: Float
        val y1: Float
        if (instrument.kind.isSetSquare) {
            val heightWorld = baseWorld * instrument.kind.setSquareRatio
            x0 = baseWorld; y0 = 0f
            x1 = 0f; y1 = heightWorld
        } else {
            x0 = 0f; y0 = 0f
            x1 = baseWorld; y1 = 0f
        }

        return floatArrayOf(
            instrument.x + x0 * cos - y0 * sin,
            instrument.y + x0 * sin + y0 * cos,
            instrument.x + x1 * cos - y1 * sin,
            instrument.y + x1 * sin + y1 * cos,
        )
    }

    /**
     * Perpendicular distance from a point to the edge SEGMENT.
     *
     * Segment rather than infinite line: ink drawn well beyond the end of a
     * ruler should not snap to the line the ruler happens to lie on.
     */
    fun distanceToEdge(edge: FloatArray, px: Float, py: Float): Float {
        val dx = edge[2] - edge[0]
        val dy = edge[3] - edge[1]
        val lengthSq = dx * dx + dy * dy
        if (lengthSq < 1e-6f) return hypot(px - edge[0], py - edge[1])

        val t = (((px - edge[0]) * dx + (py - edge[1]) * dy) / lengthSq).coerceIn(0f, 1f)
        return hypot(px - (edge[0] + t * dx), py - (edge[1] + t * dy))
    }

    /** Nearest point ON the edge segment — where a snapped sample lands. */
    fun projectOntoEdge(edge: FloatArray, px: Float, py: Float): FloatArray {
        val dx = edge[2] - edge[0]
        val dy = edge[3] - edge[1]
        val lengthSq = dx * dx + dy * dy
        if (lengthSq < 1e-6f) return floatArrayOf(edge[0], edge[1])

        val t = (((px - edge[0]) * dx + (py - edge[1]) * dy) / lengthSq).coerceIn(0f, 1f)
        return floatArrayOf(edge[0] + t * dx, edge[1] + t * dy)
    }

    /**
     * Should a sample at this distance snap?
     *
     * A band rather than always-on: the instrument sits on the board, and a
     * teacher must still be able to label a diagram beside it without first
     * putting the ruler away. The threshold is in SCREEN px so the band feels
     * the same at any zoom.
     */
    fun shouldSnap(distanceWorld: Float, zoom: Float): Boolean =
        distanceWorld * zoom <= SNAP_BAND_PX

    /**
     * Angle at [vertex] between two rays, in degrees, 0..180.
     *
     * What the protractor reads out. Unsigned because a protractor measures
     * the opening between two arms, not a direction.
     */
    fun angleBetween(
        vertexX: Float,
        vertexY: Float,
        aX: Float,
        aY: Float,
        bX: Float,
        bY: Float,
    ): Float {
        val a = atan2(aY - vertexY, aX - vertexX)
        val b = atan2(bY - vertexY, bX - vertexX)
        var deg = Math.toDegrees((b - a).toDouble()).toFloat()
        while (deg < 0f) deg += 360f
        while (deg > 360f) deg -= 360f
        return if (deg > 180f) 360f - deg else deg
    }

    /** Distance in centimetres, for the live length readout. */
    fun lengthInCm(x0: Float, y0: Float, x1: Float, y1: Float, zoom: Float): Float =
        hypot(x1 - x0, y1 - y0) * zoom / pxPerCm

    /**
     * Snaps an angle to the nearest common teaching angle when close.
     *
     * A protractor exists to produce exact angles; landing on 89.4 degrees
     * when a teacher clearly wants 90 undermines the point of using one.
     */
    fun snapAngle(degrees: Float): Float {
        COMMON_ANGLES.forEach { target ->
            if (abs(degrees - target) <= ANGLE_SNAP_DEGREES) return target
        }
        return degrees
    }

    /** Tick spacing for a scale, in world units. */
    fun tickSpacingWorld(zoom: Float, perCm: Int): Float = pxPerCm / zoom / perCm

    private const val CM_PER_INCH = 2.54f

    /** ~160dpi, the density baseline; only used when a panel misreports. */
    private const val DEFAULT_PX_PER_CM = 63f

    /** How near the edge ink is captured, in screen px. */
    const val SNAP_BAND_PX = 44f

    /** Millimetre ticks per centimetre on the metric scale. */
    const val MM_PER_CM = 10

    private const val ANGLE_SNAP_DEGREES = 1.5f

    private val COMMON_ANGLES = floatArrayOf(0f, 30f, 45f, 60f, 90f, 120f, 135f, 150f, 180f)
}
