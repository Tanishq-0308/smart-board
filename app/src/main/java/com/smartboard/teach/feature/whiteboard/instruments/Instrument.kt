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
    /**
     * Radians, clockwise, with zero pointing right.
     *
     * For the compass this is the direction the LEGS hang, so it defaults to a
     * quarter turn: a compass sits point-down on the board, not sideways.
     */
    val rotation: Float = 0f,
    /**
     * The instrument's size: length for the ruler, base for the set square,
     * diameter-ish for the protractor (see [protractorRadiusPx]) and leg
     * length for the compass. Changed by the resize handle.
     */
    val lengthCm: Float = 10f,
    /**
     * Compass only: how far the legs are opened, in radians.
     *
     * The radius follows from this and the leg length, exactly as on a real
     * compass — opening the legs wider draws a bigger circle. Stored rather
     * than the radius so the drawn tool and the circle it makes can never
     * disagree.
     */
    val spreadRad: Float = 0.42f,
    /**
     * Compass only: the arc swept so far, in radians, measured from the
     * needle. Equal values mean nothing has been swept yet.
     */
    val sweepStart: Float = 0f,
    val sweepEnd: Float = 0f,
    /**
     * Protractor only: the measuring arm's angle above the base line, in
     * radians, 0..pi — what the big readout in the middle shows.
     */
    val armRad: Float = 0f,
    /**
     * Compass only: needle and pencil change legs (the ⇆ button). False puts
     * the pencil on the right as the legs hang down, as on the reference.
     */
    val swapped: Boolean = false,
    /**
     * Stable identity, so a drag keeps hold of the instrument it started on.
     *
     * Last in the list so positional construction — `Instrument(kind, x, y)` —
     * still reads naturally at every call site.
     */
    val id: String = java.util.UUID.randomUUID().toString(),
)

/** Longest a compass leg is made, in cm — the physical tool it models. */
const val COMPASS_LEG_CM = 11f

/** Size limits for the resize handle, in cm of [Instrument.lengthCm]. */
const val MIN_LENGTH_CM = 4f
const val MAX_LENGTH_CM = 30f

/** Compass leg length in WORLD units (the compass scales with zoom). */
val Instrument.compassLegWorld: Float
    get() = lengthCm * InstrumentGeometry.pxPerCm

/**
 * The radius this compass would draw.
 *
 * The FULL distance between the two leg tips — the needle is the centre and
 * the pencil is on the circle. Opening the hinge by `spread` puts each leg
 * `spread / 2` off the centre line, so the tips are
 * `2 * legLength * sin(spread / 2)` apart. (Half that distance drew every arc
 * at half the radius the legs showed, inside the pencil instead of under it.)
 */
val Instrument.compassRadius: Float
    get() = 2f * compassLegWorld * sin(spreadRad / 2f)

/** Protractor radius in screen px; the default 10cm size gives a 4cm radius. */
val Instrument.protractorRadiusPx: Float
    get() = lengthCm * 0.4f * InstrumentGeometry.pxPerCm

/** True once the compass has been swept far enough to have drawn something. */
val Instrument.hasSweep: Boolean
    get() = abs(sweepEnd - sweepStart) > 0.01f

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
    fun edgeOf(instrument: Instrument, zoom: Float, far: Boolean = false): FloatArray {
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
            // [far] is the ruler's other long edge: both rule, as on a real one.
            val y = if (far) rulerBodyPx() / zoom else 0f
            x0 = 0f; y0 = y
            x1 = baseWorld; y1 = y
        }

        return floatArrayOf(
            instrument.x + x0 * cos - y0 * sin,
            instrument.y + x0 * sin + y0 * cos,
            instrument.x + x1 * cos - y1 * sin,
            instrument.y + x1 * sin + y1 * cos,
        )
    }

    /**
     * Every edge ink can be ruled against, nearest wins: both long sides of
     * the ruler, and all three sides of a set square — the legs draw the
     * right angle, the hypotenuse the 45 or 30/60 slope.
     */
    fun rulingEdges(instrument: Instrument, zoom: Float): List<FloatArray> {
        if (instrument.kind == InstrumentKind.RULER) {
            return listOf(edgeOf(instrument, zoom), edgeOf(instrument, zoom, far = true))
        }
        val base = instrument.lengthCm * pxPerCm / zoom
        val height = base * instrument.kind.setSquareRatio
        val c = cos(instrument.rotation)
        val s = sin(instrument.rotation)
        fun edge(x0: Float, y0: Float, x1: Float, y1: Float) = floatArrayOf(
            instrument.x + x0 * c - y0 * s, instrument.y + x0 * s + y0 * c,
            instrument.x + x1 * c - y1 * s, instrument.y + x1 * s + y1 * c,
        )
        return listOf(edgeOf(instrument, zoom), edge(0f, 0f, base, 0f), edge(0f, 0f, 0f, height))
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
     * Nearest point on the guide ink follows, in world units, or null when
     * this instrument has none.
     *
     * The ruler and set squares guide along their straight ruling edge; the
     * protractor along its curved rim, so an arc traced round it comes out
     * a true arc instead of a wobbly freehand one. The compass draws its own
     * arcs and guides nothing.
     */
    fun projectOntoGuide(instrument: Instrument, zoom: Float, px: Float, py: Float): FloatArray? {
        if (instrument.kind.hasRulingEdge) {
            return rulingEdges(instrument, zoom)
                .map { projectOntoEdge(it, px, py) }
                .minBy { hypot(px - it[0], py - it[1]) }
        }
        if (instrument.kind != InstrumentKind.PROTRACTOR) return null

        val radius = instrument.protractorRadiusPx / zoom
        val c = cos(instrument.rotation)
        val s = sin(instrument.rotation)
        val dx = px - instrument.x
        val dy = py - instrument.y
        // Into the protractor's own frame, where the rim is the half with y <= 0.
        var lx = dx * c + dy * s
        var ly = -dx * s + dy * c
        if (ly > 0f) {
            // Below the straight side: the nearest rim point is an end.
            lx = if (lx >= 0f) radius else -radius
            ly = 0f
        } else {
            val length = hypot(lx, ly).coerceAtLeast(1e-6f)
            lx = lx / length * radius
            ly = ly / length * radius
        }
        return floatArrayOf(instrument.x + lx * c - ly * s, instrument.y + lx * s + ly * c)
    }

    /** Distance from a world point to [projectOntoGuide], or null with no guide. */
    fun distanceToGuide(instrument: Instrument, zoom: Float, px: Float, py: Float): Float? =
        projectOntoGuide(instrument, zoom, px, py)?.let { hypot(px - it[0], py - it[1]) }

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

    /**
     * A dragged rotation, pulled onto the nearest 15 degrees when within 2.
     *
     * Lets a teacher land exactly on 0, 30, 45, 90... by hand, while any
     * other angle is still reachable.
     */
    fun snapRotation(radians: Float): Float {
        val step = Math.toRadians(ROTATION_STEP_DEGREES.toDouble()).toFloat()
        val nearest = Math.round(radians / step) * step
        val tolerance = Math.toRadians(ROTATION_SNAP_DEGREES.toDouble()).toFloat()
        return if (abs(radians - nearest) <= tolerance) nearest else radians
    }

    /** The angle shown to the class: 0..359, anticlockwise like a maths diagram. */
    fun displayDegrees(radians: Float): Int {
        val deg = Math.round(-Math.toDegrees(radians.toDouble())).toInt() % 360
        return if (deg < 0) deg + 360 else deg
    }

    /**
     * The protractor arm's angle for a finger at ([lx], [ly]) in the
     * protractor's own frame (centre at 0, base line along +x, up is -y):
     * 0..pi, on whole degrees, since a protractor reads whole degrees. Below
     * the base line it stays at whichever end the finger is nearer.
     */
    fun armAngle(lx: Float, ly: Float): Float {
        var a = atan2(-ly, lx)
        if (a < 0f) a = if (lx >= 0f) 0f else Math.PI.toFloat()
        val deg = Math.round(Math.toDegrees(a.toDouble())).toDouble()
        return Math.toRadians(deg).toFloat()
    }

    /** What the protractor's three draw buttons put on the board. */
    enum class ProtractorInk { ANGLE, ARC, SECTOR }

    /**
     * World x,y pairs for [part] of the angle the protractor's arm measures,
     * out to its rim: the two rays (through the centre), the arc between
     * them, or the slice (the centre, then the arc). Rays are sampled
     * densely: ink is smoothed as a curve, and three points would round the
     * vertex off.
     */
    fun protractorInk(p: Instrument, zoom: Float, part: ProtractorInk): FloatArray {
        val r = p.protractorRadiusPx / zoom
        val c = cos(p.rotation)
        val s = sin(p.rotation)
        val out = ArrayList<Float>()
        fun at(t: Float, dist: Float) {
            val lx = cos(-t) * dist
            val ly = sin(-t) * dist
            out += p.x + lx * c - ly * s
            out += p.y + lx * s + ly * c
        }
        fun arc() {
            val steps = (p.armRad / ARC_STEP).toInt().coerceAtLeast(1)
            for (i in 0..steps) at(p.armRad * i / steps, r)
        }
        val raySteps = RAY_STEPS
        when (part) {
            ProtractorInk.ANGLE -> {
                for (i in raySteps downTo 0) at(p.armRad, r * i / raySteps)
                for (i in 1..raySteps) at(0f, r * i / raySteps)
            }
            ProtractorInk.ARC -> arc()
            ProtractorInk.SECTOR -> {
                at(0f, 0f)
                arc()
            }
        }
        return out.toFloatArray()
    }

    private const val ARC_STEP = 0.035f
    private const val RAY_STEPS = 60

    private const val ROTATION_STEP_DEGREES = 15f
    private const val ROTATION_SNAP_DEGREES = 2f

    /** Tick spacing for a scale, in world units. */
    fun tickSpacingWorld(zoom: Float, perCm: Int): Float = pxPerCm / zoom / perCm

    private const val CM_PER_INCH = 2.54f

    /**
     * The ruler's width across, in screen px: 2.6cm, but never too narrow to
     * hold its grip and its three buttons stacked at the end, as on the
     * reference ruler. On a large low-density panel 2.6cm is under 100px.
     */
    fun rulerBodyPx(): Float = maxOf(RULER_BODY_CM * pxPerCm, MIN_RULER_BODY_PX)

    private const val RULER_BODY_CM = 2.6f
    private const val MIN_RULER_BODY_PX = 180f

    /** ~160dpi, the density baseline; only used when a panel misreports. */
    private const val DEFAULT_PX_PER_CM = 63f

    /** How near the edge ink is captured, in screen px. */
    const val SNAP_BAND_PX = 44f

    /** Millimetre ticks per centimetre on the metric scale. */
    const val MM_PER_CM = 10

    private const val ANGLE_SNAP_DEGREES = 1.5f

    private val COMMON_ANGLES = floatArrayOf(0f, 30f, 45f, 60f, 90f, 120f, 135f, 150f, 180f)
}
