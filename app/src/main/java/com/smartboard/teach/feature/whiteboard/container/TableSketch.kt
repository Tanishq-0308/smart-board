package com.smartboard.teach.feature.whiteboard.container

import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.ContainerCell
import com.smartboard.teach.domain.model.ContainerKind
import com.smartboard.teach.domain.model.Stroke
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot

/**
 * A hand-drawn grid turned into a real Table: the ruled lines become the
 * table's cells, and writing inside the grid moves into the cell it sits in.
 *
 * Pure geometry, like ShapeRecognizer, and conservative in the same way: it
 * answers only when the ink is clearly a grid (at least two lines each way,
 * each spanning most of the grid), so a sketch with a few straight lines in
 * it is never turned into a table by mistake.
 */
object TableSketch {

    /** What was found: the ruled positions and which strokes were the rules. */
    data class Grid(val xs: List<Float>, val ys: List<Float>, val lineIds: Set<String>) {
        val rows: Int get() = ys.size - 1
        val cols: Int get() = xs.size - 1
    }

    fun recognise(strokes: List<Stroke>): Grid? {
        val horizontal = ArrayList<Line>()
        val vertical = ArrayList<Line>()
        for (stroke in strokes) {
            val line = lineOf(stroke) ?: continue
            if (line.horizontal) horizontal += line else vertical += line
        }
        val rows = cluster(horizontal)
        val cols = cluster(vertical)
        if (rows.size < 2 || cols.size < 2 || rows.size - 1 > MAX_CELLS || cols.size - 1 > MAX_CELLS) return null
        val ys = rows.map { it.first }
        val xs = cols.map { it.first }

        // Every rule (all its pieces together) must run across most of the
        // grid, or this is a sketch that happens to contain straight lines.
        val width = xs.last() - xs.first()
        val height = ys.last() - ys.first()
        if (rows.any { it.second < SPAN * width } || cols.any { it.second < SPAN * height }) return null
        if (xs.zipWithNext().any { (a, b) -> b - a < MIN_CELL } || ys.zipWithNext().any { (a, b) -> b - a < MIN_CELL }) {
            return null
        }
        return Grid(xs, ys, (horizontal + vertical).mapTo(HashSet()) { it.id })
    }

    /** The Table container for [grid], with cells between the ruled lines. */
    fun toTable(grid: Grid, id: String = UUID.randomUUID().toString()): Container {
        val cells = ArrayList<ContainerCell>(grid.rows * grid.cols)
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                cells += ContainerCell(
                    left = grid.xs[col], top = grid.ys[row],
                    right = grid.xs[col + 1], bottom = grid.ys[row + 1],
                    row = row, col = col,
                )
            }
        }
        return Container(
            id = id, kind = ContainerKind.TABLE,
            x = grid.xs.first(), y = grid.ys.first(),
            rows = grid.rows, cols = grid.cols, cells = cells,
        )
    }

    /** The cell whose rect holds the middle of [stroke], or -1 outside the table. */
    fun cellFor(table: Container, stroke: Stroke): Int {
        val b = stroke.bounds()
        val cx = (b[0] + b[2]) / 2f
        val cy = (b[1] + b[3]) / 2f
        return table.cells.indexOfFirst { cx >= it.left && cx < it.right && cy >= it.top && cy < it.bottom }
    }

    private data class Line(val id: String, val horizontal: Boolean, val at: Float, val length: Float)

    /** A long, nearly straight, nearly axis-aligned stroke; null otherwise. */
    private fun lineOf(stroke: Stroke): Line? {
        val n = stroke.pointCount
        if (n < 2) return null
        val dx = stroke.x(n - 1) - stroke.x(0)
        val dy = stroke.y(n - 1) - stroke.y(0)
        val chord = hypot(dx, dy)
        if (chord < MIN_LINE) return null
        var path = 0f
        for (i in 1 until n) path += hypot(stroke.x(i) - stroke.x(i - 1), stroke.y(i) - stroke.y(i - 1))
        if (path > chord * MAX_WOBBLE) return null
        val b = stroke.bounds()
        return when {
            abs(dy) <= abs(dx) * MAX_SLOPE -> Line(stroke.id, true, (b[1] + b[3]) / 2f, abs(dx))
            abs(dx) <= abs(dy) * MAX_SLOPE -> Line(stroke.id, false, (b[0] + b[2]) / 2f, abs(dy))
            else -> null
        }
    }

    /**
     * Lines closer than [MERGE] are one rule, possibly drawn in pieces.
     * @return (average position, total length) per rule, in order.
     */
    private fun cluster(lines: List<Line>): List<Pair<Float, Float>> {
        val out = ArrayList<MutableList<Line>>()
        for (line in lines.sortedBy { it.at }) {
            if (out.isNotEmpty() && line.at - out.last().last().at < MERGE) out.last() += line else out += mutableListOf(line)
        }
        return out.map { rule -> rule.map { it.at }.average().toFloat() to rule.sumOf { it.length.toDouble() }.toFloat() }
    }

    private const val MIN_LINE = 80f
    private const val MAX_WOBBLE = 1.15f
    private const val MAX_SLOPE = 0.15f
    private const val MERGE = 30f
    private const val MIN_CELL = 40f
    private const val SPAN = 0.6f
    private const val MAX_CELLS = 12
}
