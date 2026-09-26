package com.smartboard.teach.feature.maths3d

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.smartboard.teach.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Display text built by pure code and resolved in the UI, so the maths stays
 * testable on the JVM. Words live in resources; maths notation is [Raw].
 */
sealed interface Txt {
    /** Language-neutral notation, shown as is. */
    data class Raw(val text: String) : Txt

    /** A string resource; [args] that are themselves [Txt] resolve too. */
    class Res(@StringRes val id: Int, vararg val args: Any) : Txt

    /** "[count] things" from a plurals resource. */
    class Plural(@PluralsRes val id: Int, val count: Int) : Txt
}

val String.raw get() = Txt.Raw(this)

/** One worked step: what, the formula, the substitution, the answer. */
data class Card(@StringRes val title: Int, val formula: Txt, val steps: Txt, val answer: String) {
    constructor(@StringRes title: Int, formula: String, steps: String, answer: String) : this(title, formula.raw, steps.raw, answer)
}

/** Faces, vertices, edges for Euler's F + V − E = 2. */
data class Euler(val f: Int, val v: Int, val e: Int)

data class Sheet(
    val name: Txt,
    val given: List<Txt>,
    val cards: List<Card>,
    val euler: Euler? = null,
    val note: Txt? = null,
    /** Shows the π = 3.14 / 22⁄7 toggle. */
    val usesPi: Boolean = false,
) {
    /** The headline answers for a snapshot caption, as (label, answer): volume and total surface. */
    val summary: List<Pair<Int, String>>
        get() = cards.mapNotNull { c -> SUMMARY_LABELS[c.title]?.let { it to c.answer } }
}

/** Card title → its caption label (the disk-method volume is just "Volume"). */
private val SUMMARY_LABELS = mapOf(
    R.string.m3d_card_volume to R.string.m3d_card_volume,
    R.string.m3d_card_volume_disk to R.string.m3d_card_volume,
    R.string.m3d_card_tsa to R.string.m3d_card_tsa,
    R.string.m3d_card_surface_area to R.string.m3d_card_surface_area,
)

enum class PiValue(val value: Double, val text: String) {
    DECIMAL(3.14, "3.14"), FRACTION(22.0 / 7, "22⁄7"),
}

private fun cm(n: Double, p: Int = 1) = "${fmt(n)} cm" + when (p) { 2 -> "²"; 3 -> "³"; else -> "" }

private fun baseArea(shape: Shape2D): Pair<Double, Card> {
    val pts = outline(shape)
    val b = polyArea(pts)
    if (pts.size == 3) {
        val base = sides(pts).max()
        return b to Card(R.string.m3d_card_base_area_triangle, Txt.Res(R.string.m3d_formula_base_triangle), "= ½ × ${fmt(base)} × ${fmt(2 * b / base)}".raw, cm(b, 2))
    }
    if (shape is Shape2D.Rect) {
        return b to if (shape.square) Card(R.string.m3d_card_base_area, "B = a²", "= ${fmt(shape.w)}²", cm(b, 2))
        else Card(R.string.m3d_card_base_area, "B = l × b", "= ${fmt(shape.w)} × ${fmt(shape.h)}", cm(b, 2))
    }
    if (isRegular(shape)) {
        val apothem = dist(centroid(pts), P((pts[0].x + pts[1].x) / 2, (pts[0].y + pts[1].y) / 2))
        return b to Card(R.string.m3d_card_base_area_regular, Txt.Res(R.string.m3d_formula_base_regular), "= ½ × ${fmt(perimeter(pts))} × ${fmt(apothem)}".raw, cm(b, 2))
    }
    return b to Card(R.string.m3d_card_base_area, Txt.Res(R.string.m3d_formula_shoelace), Txt.Res(R.string.m3d_steps_shoelace), cm(b, 2))
}

/** The worked maths for a solid. Port of the prototype's renderMath. */
fun mathsFor(shape: Shape2D, mode: Mode, h: Double, pi: PiValue): Sheet {
    val π = pi.value
    val pt = pi.text
    return when (mode) {
        Mode.CYLINDER -> {
            val r = (shape as Shape2D.Circle).r
            Sheet(
                Txt.Res(R.string.m3d_solid_cylinder), listOf("r = ${cm(r)}".raw, "h = ${cm(h)}".raw),
                listOf(
                    Card(R.string.m3d_card_volume, "V = πr²h", "= $pt × ${fmt(r)}² × ${fmt(h)}", cm(π * r * r * h, 3)),
                    Card(R.string.m3d_card_csa, "CSA = 2πrh", "= 2 × $pt × ${fmt(r)} × ${fmt(h)}", cm(2 * π * r * h, 2)),
                    Card(R.string.m3d_card_tsa, "TSA = 2πr(r + h)", "= 2 × $pt × ${fmt(r)} × (${fmt(r)} + ${fmt(h)})", cm(2 * π * r * (r + h), 2)),
                ),
                note = Txt.Res(R.string.m3d_note_cylinder),
                usesPi = true,
            )
        }
        Mode.CONE -> {
            val r = (shape as Shape2D.Circle).r
            val l = hypot(r, h)
            Sheet(
                Txt.Res(R.string.m3d_solid_cone), listOf("r = ${cm(r)}".raw, "h = ${cm(h)}".raw),
                listOf(
                    Card(R.string.m3d_card_slant_height, "l = √(r² + h²)", "= √(${fmt(r)}² + ${fmt(h)}²) = √${fmt(r * r + h * h)}", cm(l)),
                    Card(R.string.m3d_card_volume, "V = ⅓ πr²h", "= ⅓ × $pt × ${fmt(r)}² × ${fmt(h)}", cm(π * r * r * h / 3, 3)),
                    Card(R.string.m3d_card_csa, "CSA = πrl", "= $pt × ${fmt(r)} × ${fmt(l)}", cm(π * r * l, 2)),
                    Card(R.string.m3d_card_tsa, "TSA = πr(l + r)", "= $pt × ${fmt(r)} × (${fmt(l)} + ${fmt(r)})", cm(π * r * (l + r), 2)),
                ),
                note = Txt.Res(R.string.m3d_note_cone),
                usesPi = true,
            )
        }
        Mode.SPHERE -> {
            val r = (shape as Shape2D.Circle).r
            Sheet(
                Txt.Res(R.string.m3d_solid_sphere), listOf("r = ${cm(r)}".raw),
                listOf(
                    Card(R.string.m3d_card_volume, "V = ⁴⁄₃ πr³", "= ⁴⁄₃ × $pt × ${fmt(r)}³", cm(4.0 / 3 * π * r.pow(3), 3)),
                    Card(R.string.m3d_card_surface_area, "SA = 4πr²", "= 4 × $pt × ${fmt(r)}²", cm(4 * π * r * r, 2)),
                    Card(R.string.m3d_card_hemisphere, "V = ⅔ πr³ ,  TSA = 3πr²", "", "${cm(2.0 / 3 * π * r.pow(3), 3)} , ${cm(3 * π * r * r, 2)}"),
                ),
                note = Txt.Res(R.string.m3d_note_sphere),
                usesPi = true,
            )
        }
        Mode.PRISM -> if (shape is Shape2D.Rect) cuboid(shape, h) else prism(shape, h)
        Mode.PYRAMID -> pyramid(shape, h)
        Mode.REVOLVE -> revolve(shape, π, pt)
    }
}

private fun cuboid(shape: Shape2D.Rect, h: Double): Sheet {
    val l = shape.w; val b = shape.h
    if (l == b && b == h) {
        return Sheet(
            Txt.Res(R.string.m3d_solid_cube), listOf("a = ${cm(l)}".raw),
            listOf(
                Card(R.string.m3d_card_volume, "V = a³", "= ${fmt(l)}³", cm(l.pow(3), 3)),
                Card(R.string.m3d_card_lsa, "LSA = 4a²", "= 4 × ${fmt(l)}²", cm(4 * l * l, 2)),
                Card(R.string.m3d_card_tsa, "TSA = 6a²", "= 6 × ${fmt(l)}²", cm(6 * l * l, 2)),
                Card(R.string.m3d_card_diagonal, "d = a√3", "= ${fmt(l)} × 1.732", cm(l * sqrt(3.0))),
            ),
            Euler(6, 8, 12),
        )
    }
    return Sheet(
        Txt.Res(R.string.m3d_solid_cuboid), listOf("l = ${cm(l)}".raw, "b = ${cm(b)}".raw, "h = ${cm(h)}".raw),
        listOf(
            Card(R.string.m3d_card_volume, "V = l × b × h", "= ${fmt(l)} × ${fmt(b)} × ${fmt(h)}", cm(l * b * h, 3)),
            Card(R.string.m3d_card_lsa, "LSA = 2h(l + b)", "= 2 × ${fmt(h)} × (${fmt(l)} + ${fmt(b)})", cm(2 * h * (l + b), 2)),
            Card(R.string.m3d_card_tsa, "TSA = 2(lb + bh + hl)", "= 2(${fmt(l * b)} + ${fmt(b * h)} + ${fmt(h * l)})", cm(2 * (l * b + b * h + h * l), 2)),
            Card(R.string.m3d_card_diagonal, "d = √(l² + b² + h²)", "= √${fmt(l * l + b * b + h * h)}", cm(sqrt(l * l + b * b + h * h))),
        ),
        Euler(6, 8, 12),
        note = if (shape.square) Txt.Res(R.string.m3d_note_cube, fmt(l)) else null,
    )
}

/** Sides, height and perimeter, shown above a prism or pyramid's cards. */
private fun given(n: Int, h: Double, p: Double) = listOf(Txt.Plural(R.plurals.m3d_sides, n), "h = ${cm(h)}".raw, "P = ${cm(p)}".raw)

private fun prism(shape: Shape2D, h: Double): Sheet {
    val pts = outline(shape)
    val n = pts.size
    val p = perimeter(pts)
    val (b, baseCard) = baseArea(shape)
    return Sheet(
        Txt.Res(R.string.m3d_solid_named_prism, Txt.Res(shape.name)), given(n, h, p),
        listOf(
            baseCard,
            Card(R.string.m3d_card_volume, "V = B × h", "= ${fmt(b)} × ${fmt(h)}", cm(b * h, 3)),
            Card(R.string.m3d_card_lsa, Txt.Res(R.string.m3d_formula_lsa_prism), "= ${fmt(p)} × ${fmt(h)}".raw, cm(p * h, 2)),
            Card(R.string.m3d_card_tsa, "TSA = 2B + LSA", "= 2 × ${fmt(b)} + ${fmt(p * h)}", cm(2 * b + p * h, 2)),
        ),
        Euler(n + 2, 2 * n, 3 * n),
    )
}

private fun pyramid(shape: Shape2D, h: Double): Sheet {
    val pts = outline(shape)
    val n = pts.size
    val p = perimeter(pts)
    val (b, baseCard) = baseArea(shape)
    val poly = basePoly(shape)
    // Each side face is the triangle (edge, apex); half the cross product is its area.
    var lsa = 0.0
    for (i in poly.indices) {
        val a = poly[i]; val c = poly[(i + 1) % n]
        val u = V((c.x - a.x).toFloat(), 0f, (c.y - a.y).toFloat())
        val w = V((-a.x).toFloat(), h.toFloat(), (-a.y).toFloat())
        lsa += (u cross w).length() / 2.0
    }
    val cards = mutableListOf(baseCard, Card(R.string.m3d_card_volume, "V = ⅓ × B × h", "= ⅓ × ${fmt(b)} × ${fmt(h)}", cm(b * h / 3, 3)))
    if (isRegular(shape)) {
        val a = hypot((poly[0].x + poly[1].x) / 2, (poly[0].y + poly[1].y) / 2)
        val l = hypot(h, a)
        cards += Card(R.string.m3d_card_slant_height, "l = √(h² + a²)".raw, Txt.Res(R.string.m3d_steps_slant, fmt(a), fmt(h)), cm(l))
        cards += Card(R.string.m3d_card_lsa, "LSA = ½ × P × l", "= ½ × ${fmt(p)} × ${fmt(l)}", cm(lsa, 2))
    } else {
        cards += Card(R.string.m3d_card_lsa, Txt.Res(R.string.m3d_formula_lsa_faces), Txt.Plural(R.plurals.m3d_triangles, n), cm(lsa, 2))
    }
    cards += Card(R.string.m3d_card_tsa, "TSA = B + LSA", "= ${fmt(b)} + ${fmt(lsa)}", cm(b + lsa, 2))
    val name = if (shape is Shape2D.Rect && shape.square) Txt.Res(R.string.m3d_solid_square_pyramid) else Txt.Res(R.string.m3d_solid_named_pyramid, Txt.Res(shape.name))
    return Sheet(
        name, given(n, h, p), cards,
        Euler(n + 1, n + 1, 2 * n),
        note = Txt.Res(R.string.m3d_note_pyramid),
    )
}

private fun revolve(shape: Shape2D, π: Double, pt: String): Sheet {
    val st = revolveStats(revolveLoop(shape))
    val cards = mutableListOf<Card>()
    val sheetName: Txt
    val given: List<Txt>
    var usesPi = false
    if (shape is Shape2D.Circle && abs(shape.cx) >= shape.r) {
        val bigR = abs(shape.cx); val r = shape.r
        sheetName = Txt.Res(R.string.m3d_solid_torus)
        given = listOf(Txt.Res(R.string.m3d_given_axis_r, cm(bigR)), "r = ${cm(r)}".raw)
        usesPi = true
        cards += Card(R.string.m3d_card_volume, "V = 2π²Rr²", "= 2 × $pt² × ${fmt(bigR)} × ${fmt(r)}²", cm(2 * π * π * bigR * r * r, 3))
        cards += Card(R.string.m3d_card_surface_area, "SA = 4π²Rr", "= 4 × $pt² × ${fmt(bigR)} × ${fmt(r)}", cm(4 * π * π * bigR * r, 2))
    } else {
        sheetName = Txt.Res(R.string.m3d_solid_revolution)
        given = listOf(Txt.Res(R.string.m3d_given_height, cm(st.height)), Txt.Res(R.string.m3d_given_max_r, cm(st.rMax)))
        cards += Card(R.string.m3d_card_volume_disk, "V = ∫ πr² dy".raw, Txt.Res(R.string.m3d_steps_disk), cm(st.volume, 3))
        cards += Card(R.string.m3d_card_surface_area, "S = ∫ 2πr ds".raw, Txt.Res(R.string.m3d_steps_band), cm(st.surface, 2))
    }
    if (shape !is Shape2D.Profile) {
        val pts = outline(shape, 72)
        val area = polyArea(pts)
        val xBar = abs(centroid(pts).x)
        cards += Card(R.string.m3d_card_pappus, "V = 2π × x̄ × A".raw, Txt.Res(R.string.m3d_steps_pappus, fmt(area), fmt(xBar)), cm(2 * PI * xBar * area, 3))
    }
    return Sheet(
        sheetName, given, cards,
        note = Txt.Res(R.string.m3d_note_revolve),
        usesPi = usesPi,
    )
}
