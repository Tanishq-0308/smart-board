package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Outlines for every bounds-derived shape.
 *
 * Pure geometry over a drag box: no Android types, no Paint, no Canvas, so the
 * vertex maths that decides whether a pentagon looks like a pentagon can be
 * unit-tested. The renderer only strokes what comes back.
 *
 * Every shape is described as one or more polylines in world coordinates —
 * [visible] outlines plus, for solids, [hidden] edges the renderer dashes to
 * show what lies behind. Ellipses are the exception and are flagged so the
 * renderer can use drawOval rather than a many-segment approximation.
 */
object ShapeGeometry {

    /** A closed or open run of points: [x0, y0, x1, y1, ...]. */
    data class Polyline(val points: FloatArray, val closed: Boolean) {
        // FloatArray needs structural equality spelled out.
        override fun equals(other: Any?): Boolean =
            other is Polyline && closed == other.closed && points.contentEquals(other.points)

        override fun hashCode(): Int = 31 * points.contentHashCode() + closed.hashCode()
    }

    /**
     * An ellipse the renderer should draw with drawOval.
     *
     * Approximating one as a polyline is visibly faceted at board scale and
     * costs far more path segments than the primitive.
     */
    data class Oval(val left: Float, val top: Float, val right: Float, val bottom: Float)

    data class Outline(
        val visible: List<Polyline> = emptyList(),
        /** Edges behind the solid; drawn dashed. */
        val hidden: List<Polyline> = emptyList(),
        val ovals: List<Oval> = emptyList(),
        val hiddenOvals: List<Oval> = emptyList(),
        /** Half-ovals for cylinder and cone lids, as start/sweep in degrees. */
        val arcs: List<Arc> = emptyList(),
    )

    data class Arc(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val startDegrees: Float,
        val sweepDegrees: Float,
    )

    /**
     * Builds the outline for [tool] inside the box the teacher dragged.
     *
     * The box is normalised first, so dragging right-to-left or bottom-to-top
     * produces the same shape as dragging the other way.
     */
    fun outlineFor(tool: DrawTool, x0: Float, y0: Float, x1: Float, y1: Float): Outline {
        val left = min(x0, x1)
        val top = min(y0, y1)
        val right = maxOf(x0, x1)
        val bottom = maxOf(y0, y1)
        val w = right - left
        val h = bottom - top
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f

        return when (tool) {
            // Lines keep their true direction: normalising would flip an
            // arrow to point the wrong way.
            DrawTool.LINE, DrawTool.DASHED_LINE,
            DrawTool.ARROW, DrawTool.DASHED_ARROW,
            -> Outline(visible = listOf(open(x0, y0, x1, y1)))

            DrawTool.TRIANGLE -> Outline(
                visible = listOf(closed(cx, top, right, bottom, left, bottom)),
            )

            // Narrower apex than the equilateral form, so the two read as
            // different shapes rather than the same one twice.
            DrawTool.ISOSCELES_TRIANGLE -> Outline(
                visible = listOf(
                    closed(cx, top, left + w * 0.88f, bottom, left + w * 0.12f, bottom),
                ),
            )

            DrawTool.RIGHT_TRIANGLE -> Outline(
                visible = listOf(closed(left, top, right, bottom, left, bottom)),
            )

            DrawTool.DIAMOND -> Outline(
                visible = listOf(closed(cx, top, right, cy, cx, bottom, left, cy)),
            )

            DrawTool.PARALLELOGRAM -> {
                val slant = w * SLANT
                Outline(
                    visible = listOf(
                        closed(
                            left + slant, top,
                            right, top,
                            right - slant, bottom,
                            left, bottom,
                        ),
                    ),
                )
            }

            DrawTool.TRAPEZOID -> {
                val inset = w * SLANT
                Outline(
                    visible = listOf(
                        closed(
                            left + inset, top,
                            right - inset, top,
                            right, bottom,
                            left, bottom,
                        ),
                    ),
                )
            }

            DrawTool.RECT -> Outline(
                visible = listOf(closed(left, top, right, top, right, bottom, left, bottom)),
            )

            // Corner radius scales with the smaller side, so a long thin box
            // does not turn into a lozenge.
            DrawTool.ROUNDED_RECT -> Outline(
                visible = listOf(roundedRect(left, top, right, bottom)),
            )

            DrawTool.CIRCLE, DrawTool.ELLIPSE ->
                Outline(ovals = listOf(Oval(left, top, right, bottom)))

            DrawTool.SEMICIRCLE -> Outline(
                visible = listOf(open(left, bottom, right, bottom)),
                arcs = listOf(Arc(left, top, right, bottom + h, 180f, 180f)),
            )

            DrawTool.PENTAGON -> Outline(visible = listOf(regular(cx, cy, w, h, 5)))
            DrawTool.HEXAGON -> Outline(visible = listOf(regular(cx, cy, w, h, 6)))
            DrawTool.STAR -> Outline(visible = listOf(star(cx, cy, w, h)))

            DrawTool.CUBE -> cube(left, top, right, bottom)
            DrawTool.PYRAMID -> pyramid(left, top, right, bottom, cx)
            DrawTool.PRISM -> prism(left, top, right, bottom)
            DrawTool.TETRAHEDRON -> tetrahedron(left, top, right, bottom, cx)
            DrawTool.CYLINDER -> cylinder(left, top, right, bottom, w, h)
            DrawTool.CONE -> cone(left, top, right, bottom, cx, w, h)
            DrawTool.SPHERE -> sphere(left, top, right, bottom, cx, cy, w, h)

            else -> Outline()
        }
    }

    // --- 3-D solids -----------------------------------------------------
    //
    // All are flat projections built from the drag box: a front face, a back
    // face offset by a fixed depth, and the edges joining them. Edges that
    // would be behind the solid go in `hidden` so the renderer dashes them,
    // which is what makes a wireframe read as a solid rather than a tangle.

    private fun cube(left: Float, top: Float, right: Float, bottom: Float): Outline {
        val d = (right - left) * DEPTH
        val fTop = top + d
        val bRight = right
        val fRight = right - d
        return Outline(
            visible = listOf(
                // Front face.
                closed(left, fTop, fRight, fTop, fRight, bottom, left, bottom),
                // Top face.
                closed(left, fTop, left + d, top, bRight, top, fRight, fTop),
                // Right face.
                closed(fRight, fTop, bRight, top, bRight, bottom - d, fRight, bottom),
            ),
            hidden = listOf(
                // The back corner and the two edges running to it.
                open(left + d, top, left + d, bottom - d),
                open(left + d, bottom - d, bRight, bottom - d),
                open(left + d, bottom - d, left, bottom),
            ),
        )
    }

    private fun pyramid(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        cx: Float,
    ): Outline {
        val d = (right - left) * DEPTH
        val baseTop = bottom - d
        return Outline(
            visible = listOf(
                // Apex to the three visible base corners.
                closed(cx, top, right - d, baseTop, left, bottom),
                open(cx, top, right, bottom),
                open(left, bottom, right, bottom),
                open(right - d, baseTop, right, bottom),
            ),
            hidden = listOf(
                // The far base corner.
                open(left, bottom, left + d, baseTop),
                open(left + d, baseTop, right - d, baseTop),
                open(cx, top, left + d, baseTop),
            ),
        )
    }

    private fun prism(left: Float, top: Float, right: Float, bottom: Float): Outline {
        val d = (right - left) * DEPTH
        val cxFront = (left + right - d) / 2f
        return Outline(
            visible = listOf(
                // Front triangular face.
                closed(cxFront, top + d, right - d, bottom, left, bottom),
                // Top edge running back.
                open(cxFront, top + d, cxFront + d, top),
                // Back-right edge.
                open(right - d, bottom, right, bottom - d),
                closed(cxFront + d, top, right, bottom - d, right - d, bottom),
            ),
            hidden = listOf(
                open(left, bottom, left + d, bottom - d),
                open(left + d, bottom - d, right, bottom - d),
                open(left + d, bottom - d, cxFront + d, top),
            ),
        )
    }

    private fun tetrahedron(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        cx: Float,
    ): Outline {
        val d = (right - left) * DEPTH
        return Outline(
            visible = listOf(
                closed(cx, top, right, bottom, left, bottom),
                open(cx, top, cx + d * 0.4f, bottom - d),
                open(left, bottom, cx + d * 0.4f, bottom - d),
            ),
            hidden = listOf(open(right, bottom, cx + d * 0.4f, bottom - d)),
        )
    }

    private fun cylinder(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        w: Float,
        h: Float,
    ): Outline {
        val lid = (h * LID).coerceAtMost(w * 0.35f)
        return Outline(
            visible = listOf(
                open(left, top + lid, left, bottom - lid),
                open(right, top + lid, right, bottom - lid),
            ),
            // Top lid is a full ellipse; the bottom shows only its front half.
            ovals = listOf(Oval(left, top, right, top + lid * 2f)),
            arcs = listOf(Arc(left, bottom - lid * 2f, right, bottom, 0f, 180f)),
            hiddenOvals = listOf(Oval(left, bottom - lid * 2f, right, bottom)),
        )
    }

    private fun cone(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        cx: Float,
        w: Float,
        h: Float,
    ): Outline {
        val lid = (h * LID).coerceAtMost(w * 0.35f)
        return Outline(
            visible = listOf(
                open(cx, top, left, bottom - lid),
                open(cx, top, right, bottom - lid),
            ),
            arcs = listOf(Arc(left, bottom - lid * 2f, right, bottom, 0f, 180f)),
            hiddenOvals = listOf(Oval(left, bottom - lid * 2f, right, bottom)),
        )
    }

    private fun sphere(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
    ): Outline {
        val lid = h * 0.18f
        return Outline(
            // Outline plus a front equator arc: a bare circle would not read
            // as a sphere at all.
            ovals = listOf(Oval(left, top, right, bottom)),
            arcs = listOf(Arc(left, cy - lid, right, cy + lid, 0f, 180f)),
            hiddenOvals = listOf(Oval(left, cy - lid, right, cy + lid)),
        )
    }

    // --- primitives -----------------------------------------------------

    private fun open(vararg coords: Float) = Polyline(coords, closed = false)

    private fun closed(vararg coords: Float) = Polyline(coords, closed = true)

    /** An N-sided regular polygon inscribed in the box, first vertex at top. */
    internal fun regular(cx: Float, cy: Float, w: Float, h: Float, sides: Int): Polyline {
        val out = FloatArray(sides * 2)
        val rx = w / 2f
        val ry = h / 2f
        for (i in 0 until sides) {
            // -90 degrees puts a vertex at the top, which is how these shapes
            // are conventionally drawn.
            val angle = (2.0 * PI * i / sides) - PI / 2.0
            out[i * 2] = cx + rx * cos(angle).toFloat()
            out[i * 2 + 1] = cy + ry * sin(angle).toFloat()
        }
        return Polyline(out, closed = true)
    }

    /** Five-pointed star: alternating outer and inner vertices. */
    internal fun star(cx: Float, cy: Float, w: Float, h: Float): Polyline {
        val points = 5
        val out = FloatArray(points * 4)
        val rx = w / 2f
        val ry = h / 2f
        for (i in 0 until points * 2) {
            val outer = i % 2 == 0
            val fx = if (outer) 1f else STAR_INNER
            val angle = (PI * i / points) - PI / 2.0
            out[i * 2] = cx + rx * fx * cos(angle).toFloat()
            out[i * 2 + 1] = cy + ry * fx * sin(angle).toFloat()
        }
        return Polyline(out, closed = true)
    }

    /** Corners approximated with short chords; cheap and visually identical. */
    private fun roundedRect(left: Float, top: Float, right: Float, bottom: Float): Polyline {
        val r = (min(right - left, bottom - top) * 0.18f)
        val steps = 4
        val out = ArrayList<Float>(64)

        fun corner(ccx: Float, ccy: Float, from: Double) {
            for (s in 0..steps) {
                val a = from + (PI / 2.0) * s / steps
                out += ccx + r * cos(a).toFloat()
                out += ccy + r * sin(a).toFloat()
            }
        }
        corner(right - r, top + r, -PI / 2)
        corner(right - r, bottom - r, 0.0)
        corner(left + r, bottom - r, PI / 2)
        corner(left + r, top + r, PI)
        return Polyline(out.toFloatArray(), closed = true)
    }

    /** How far a solid's back face is offset from its front, as a fraction of width. */
    private const val DEPTH = 0.26f

    /** Lid height for cylinders and cones, as a fraction of the box height. */
    private const val LID = 0.13f

    /** Slant of a parallelogram or trapezoid, as a fraction of width. */
    private const val SLANT = 0.22f

    /** Star waist: smaller is spikier. 0.42 reads as a classic five-point star. */
    private const val STAR_INNER = 0.42f
}
