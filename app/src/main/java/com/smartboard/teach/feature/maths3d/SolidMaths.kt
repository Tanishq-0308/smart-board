package com.smartboard.teach.feature.maths3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/** One worked step: what, the formula, the substitution, the answer. */
data class Card(val title: String, val formula: String, val steps: String, val answer: String)

/** Faces, vertices, edges for Euler's F + V − E = 2. */
data class Euler(val f: Int, val v: Int, val e: Int)

data class Sheet(
    val name: String,
    val given: List<String>,
    val cards: List<Card>,
    val euler: Euler? = null,
    val note: String? = null,
    /** Shows the π = 3.14 / 22⁄7 toggle. */
    val usesPi: Boolean = false,
) {
    /** The headline answers for a snapshot caption: volume and total surface. */
    val summary: String
        get() = cards.filter { it.title.startsWith("Volume") || it.title.startsWith("Total") || it.title.startsWith("Surface") }
            .joinToString("   ") { "${it.title.substringBefore(" —").substringBefore(" (")}: ${it.answer}" }
}

enum class PiValue(val value: Double, val text: String) {
    DECIMAL(3.14, "3.14"), FRACTION(22.0 / 7, "22⁄7"),
}

private fun cm(n: Double, p: Int = 1) = "${fmt(n)} cm" + when (p) { 2 -> "²"; 3 -> "³"; else -> "" }

private fun baseArea(shape: Shape2D): Pair<Double, Card> {
    val pts = outline(shape)
    val b = polyArea(pts)
    if (pts.size == 3) {
        val base = sides(pts).max()
        return b to Card("Base area (B) — triangle", "B = ½ × base × height", "= ½ × ${fmt(base)} × ${fmt(2 * b / base)}", cm(b, 2))
    }
    if (shape is Shape2D.Rect) {
        return b to if (shape.square) Card("Base area (B)", "B = a²", "= ${fmt(shape.w)}²", cm(b, 2))
        else Card("Base area (B)", "B = l × b", "= ${fmt(shape.w)} × ${fmt(shape.h)}", cm(b, 2))
    }
    if (isRegular(shape)) {
        val apothem = dist(centroid(pts), P((pts[0].x + pts[1].x) / 2, (pts[0].y + pts[1].y) / 2))
        return b to Card("Base area (B) — regular polygon", "B = ½ × Perimeter × apothem", "= ½ × ${fmt(perimeter(pts))} × ${fmt(apothem)}", cm(b, 2))
    }
    return b to Card("Base area (B)", "B = shoelace formula", "from the grid coordinates of the corners", cm(b, 2))
}

/** The worked maths for a solid. Port of the prototype's renderMath. */
fun mathsFor(shape: Shape2D, mode: Mode, h: Double, pi: PiValue): Sheet {
    val π = pi.value
    val pt = pi.text
    return when (mode) {
        Mode.CYLINDER -> {
            val r = (shape as Shape2D.Circle).r
            Sheet(
                "Cylinder", listOf("r = ${cm(r)}", "h = ${cm(h)}"),
                listOf(
                    Card("Volume", "V = πr²h", "= $pt × ${fmt(r)}² × ${fmt(h)}", cm(π * r * r * h, 3)),
                    Card("Curved surface area", "CSA = 2πrh", "= 2 × $pt × ${fmt(r)} × ${fmt(h)}", cm(2 * π * r * h, 2)),
                    Card("Total surface area", "TSA = 2πr(r + h)", "= 2 × $pt × ${fmt(r)} × (${fmt(r)} + ${fmt(h)})", cm(2 * π * r * (r + h), 2)),
                ),
                note = "Euler's formula counts flat faces, so it applies to polyhedra — not to a cylinder.",
                usesPi = true,
            )
        }
        Mode.CONE -> {
            val r = (shape as Shape2D.Circle).r
            val l = hypot(r, h)
            Sheet(
                "Cone", listOf("r = ${cm(r)}", "h = ${cm(h)}"),
                listOf(
                    Card("Slant height", "l = √(r² + h²)", "= √(${fmt(r)}² + ${fmt(h)}²) = √${fmt(r * r + h * h)}", cm(l)),
                    Card("Volume", "V = ⅓ πr²h", "= ⅓ × $pt × ${fmt(r)}² × ${fmt(h)}", cm(π * r * r * h / 3, 3)),
                    Card("Curved surface area", "CSA = πrl", "= $pt × ${fmt(r)} × ${fmt(l)}", cm(π * r * l, 2)),
                    Card("Total surface area", "TSA = πr(l + r)", "= $pt × ${fmt(r)} × (${fmt(l)} + ${fmt(r)})", cm(π * r * (l + r), 2)),
                ),
                note = "One third of the cylinder with the same r and h — three cones of water fill one cylinder.",
                usesPi = true,
            )
        }
        Mode.SPHERE -> {
            val r = (shape as Shape2D.Circle).r
            Sheet(
                "Sphere", listOf("r = ${cm(r)}"),
                listOf(
                    Card("Volume", "V = ⁴⁄₃ πr³", "= ⁴⁄₃ × $pt × ${fmt(r)}³", cm(4.0 / 3 * π * r.pow(3), 3)),
                    Card("Surface area", "SA = 4πr²", "= 4 × $pt × ${fmt(r)}²", cm(4 * π * r * r, 2)),
                    Card("Hemisphere", "V = ⅔ πr³ ,  TSA = 3πr²", "", "${cm(2.0 / 3 * π * r.pow(3), 3)} , ${cm(3 * π * r * r, 2)}"),
                ),
                note = "Archimedes: a sphere is ⅔ of the cylinder (h = 2r) that wraps it.",
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
            "Cube", listOf("a = ${cm(l)}"),
            listOf(
                Card("Volume", "V = a³", "= ${fmt(l)}³", cm(l.pow(3), 3)),
                Card("Lateral surface area", "LSA = 4a²", "= 4 × ${fmt(l)}²", cm(4 * l * l, 2)),
                Card("Total surface area", "TSA = 6a²", "= 6 × ${fmt(l)}²", cm(6 * l * l, 2)),
                Card("Diagonal", "d = a√3", "= ${fmt(l)} × 1.732", cm(l * sqrt(3.0))),
            ),
            Euler(6, 8, 12),
        )
    }
    return Sheet(
        "Cuboid", listOf("l = ${cm(l)}", "b = ${cm(b)}", "h = ${cm(h)}"),
        listOf(
            Card("Volume", "V = l × b × h", "= ${fmt(l)} × ${fmt(b)} × ${fmt(h)}", cm(l * b * h, 3)),
            Card("Lateral surface area", "LSA = 2h(l + b)", "= 2 × ${fmt(h)} × (${fmt(l)} + ${fmt(b)})", cm(2 * h * (l + b), 2)),
            Card("Total surface area", "TSA = 2(lb + bh + hl)", "= 2(${fmt(l * b)} + ${fmt(b * h)} + ${fmt(h * l)})", cm(2 * (l * b + b * h + h * l), 2)),
            Card("Diagonal", "d = √(l² + b² + h²)", "= √${fmt(l * l + b * b + h * h)}", cm(sqrt(l * l + b * b + h * h))),
        ),
        Euler(6, 8, 12),
        note = if (shape.square) "Set the height to ${fmt(l)} and it becomes a cube!" else null,
    )
}

private fun prism(shape: Shape2D, h: Double): Sheet {
    val pts = outline(shape)
    val n = pts.size
    val p = perimeter(pts)
    val (b, baseCard) = baseArea(shape)
    return Sheet(
        "${shape.name} Prism", listOf("$n sides", "h = ${cm(h)}", "P = ${cm(p)}"),
        listOf(
            baseCard,
            Card("Volume", "V = B × h", "= ${fmt(b)} × ${fmt(h)}", cm(b * h, 3)),
            Card("Lateral surface area", "LSA = Perimeter × h", "= ${fmt(p)} × ${fmt(h)}", cm(p * h, 2)),
            Card("Total surface area", "TSA = 2B + LSA", "= 2 × ${fmt(b)} + ${fmt(p * h)}", cm(2 * b + p * h, 2)),
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
    val cards = mutableListOf(baseCard, Card("Volume", "V = ⅓ × B × h", "= ⅓ × ${fmt(b)} × ${fmt(h)}", cm(b * h / 3, 3)))
    if (isRegular(shape)) {
        val a = hypot((poly[0].x + poly[1].x) / 2, (poly[0].y + poly[1].y) / 2)
        val l = hypot(h, a)
        cards += Card("Slant height", "l = √(h² + a²)", "a = centre to side = ${fmt(a)} → √(${fmt(h)}² + ${fmt(a)}²)", cm(l))
        cards += Card("Lateral surface area", "LSA = ½ × P × l", "= ½ × ${fmt(p)} × ${fmt(l)}", cm(lsa, 2))
    } else {
        cards += Card("Lateral surface area", "LSA = sum of the triangular faces", "$n triangles", cm(lsa, 2))
    }
    cards += Card("Total surface area", "TSA = B + LSA", "= ${fmt(b)} + ${fmt(lsa)}", cm(b + lsa, 2))
    val name = if (shape is Shape2D.Rect && shape.square) "Square Pyramid" else "${shape.name} Pyramid"
    return Sheet(
        name, listOf("$n sides", "h = ${cm(h)}", "P = ${cm(p)}"), cards,
        Euler(n + 1, n + 1, 2 * n),
        note = "One third of the prism with the same base and height.",
    )
}

private fun revolve(shape: Shape2D, π: Double, pt: String): Sheet {
    val st = revolveStats(revolveLoop(shape))
    val cards = mutableListOf<Card>()
    val sheetName: String
    val given: List<String>
    var usesPi = false
    if (shape is Shape2D.Circle && abs(shape.cx) >= shape.r) {
        val bigR = abs(shape.cx); val r = shape.r
        sheetName = "Torus"
        given = listOf("R = ${cm(bigR)} (axis to centre)", "r = ${cm(r)}")
        usesPi = true
        cards += Card("Volume", "V = 2π²Rr²", "= 2 × $pt² × ${fmt(bigR)} × ${fmt(r)}²", cm(2 * π * π * bigR * r * r, 3))
        cards += Card("Surface area", "SA = 4π²Rr", "= 4 × $pt² × ${fmt(bigR)} × ${fmt(r)}", cm(4 * π * π * bigR * r, 2))
    } else {
        sheetName = "Solid of revolution"
        given = listOf("height = ${cm(st.height)}", "max r = ${cm(st.rMax)}")
        cards += Card("Volume — disk method", "V = ∫ πr² dy", "Slice the shape into thin discs and add up every πr²·Δy", cm(st.volume, 3))
        cards += Card("Surface area", "S = ∫ 2πr ds", "Each thin band's circumference × its width", cm(st.surface, 2))
    }
    if (shape !is Shape2D.Profile) {
        val pts = outline(shape, 72)
        val area = polyArea(pts)
        val xBar = abs(centroid(pts).x)
        cards += Card("Pappus's theorem", "V = 2π × x̄ × A", "A = ${fmt(area)}, centroid distance from the axis x̄ = ${fmt(xBar)}", cm(2 * PI * xBar * area, 3))
    }
    return Sheet(
        sheetName, given, cards,
        note = "Turning the shape a full 360° around the axis makes this solid. Tap Grow to watch it form.",
        usesPi = usesPi,
    )
}
