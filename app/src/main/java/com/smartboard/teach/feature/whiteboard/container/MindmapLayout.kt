package com.smartboard.teach.feature.whiteboard.container

import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.ContainerCell
import com.smartboard.teach.domain.model.ContainerKind
import java.util.UUID

/**
 * Tree geometry for mindmap containers.
 *
 * Pure functions over immutable data, matching [TableGrid]. A node's parent is
 * held in [ContainerCell.col] (-1 for the root) and its depth is derived by
 * walking up — no second structure to keep in sync with the cell list.
 *
 * Layout is left-to-right, as in the reference panel: the root sits on the
 * left and each generation is a column to its right, children stacked
 * vertically and centred on their parent.
 */
object MindmapLayout {

    /** A lone root node, ready to be written in. */
    fun create(
        x: Float,
        y: Float,
        id: String = UUID.randomUUID().toString(),
    ): Container = Container(
        id = id,
        kind = ContainerKind.MINDMAP,
        x = x,
        y = y,
        cells = listOf(
            ContainerCell(
                left = x,
                top = y,
                right = x + NODE_WIDTH,
                bottom = y + NODE_HEIGHT,
                col = ROOT_PARENT,
            ),
        ),
    )

    /** Direct children of [index], in cell order. */
    fun childrenOf(container: Container, index: Int): List<Int> =
        container.cells.indices.filter { container.cells[it].col == index }

    /**
     * Depth of a node, root being 0.
     *
     * Bounded by the cell count so a corrupt parent chain — a cycle from a bad
     * migration or a hand-edited database — cannot hang the render loop.
     */
    fun depthOf(container: Container, index: Int): Int {
        var depth = 0
        var cursor = container.cells.getOrNull(index)?.col ?: ROOT_PARENT
        while (cursor >= 0 && depth <= container.cells.size) {
            depth++
            cursor = container.cells.getOrNull(cursor)?.col ?: ROOT_PARENT
        }
        return depth
    }

    /** [index] and every node beneath it, deepest last. */
    fun subtreeOf(container: Container, index: Int): List<Int> {
        val collected = ArrayList<Int>()
        val queue = ArrayDeque<Int>()
        queue += index
        // Guarded the same way as depthOf: a cycle must not spin forever.
        while (queue.isNotEmpty() && collected.size <= container.cells.size) {
            val current = queue.removeFirst()
            if (current in collected) continue
            collected += current
            queue += childrenOf(container, current)
        }
        return collected
    }

    /**
     * Adds a child of [parentIndex]. The new node is appended LAST.
     *
     * Appending rather than inserting keeps every existing cell index stable,
     * so contained ink needs no retagging — the single most error-prone part
     * of a structural edit, and here it is avoided outright.
     */
    fun addChild(container: Container, parentIndex: Int): Container {
        if (container.cells.getOrNull(parentIndex) == null) return container
        return container.copy(
            cells = container.cells + ContainerCell(
                // Placeholder rect; reflow positions it. Sized correctly so a
                // caller that skips reflow still gets a usable node.
                left = 0f,
                top = 0f,
                right = NODE_WIDTH,
                bottom = NODE_HEIGHT,
                col = parentIndex,
            ),
        ).let(::reflow)
    }

    /**
     * Adds a sibling of [index] — a child of the same parent.
     *
     * The root has no parent, so a sibling of it would be a second root the
     * layout cannot place. Returns the container unchanged in that case
     * rather than silently producing a detached node.
     */
    fun addSibling(container: Container, index: Int): Container {
        val parent = container.cells.getOrNull(index)?.col ?: return container
        if (parent == ROOT_PARENT) return container
        return addChild(container, parent)
    }

    /**
     * Deletes [index] and its whole subtree.
     *
     * Returns the new container plus the OLD cell indices that were removed,
     * so the caller can delete their ink and retag what survived. Deleting the
     * root deletes the mindmap; that is the caller's decision, signalled by an
     * empty cell list.
     */
    data class Removal(
        val container: Container,
        val removedIndices: List<Int>,
        /** oldIndex -> newIndex for surviving cells. */
        val reindex: Map<Int, Int>,
    )

    fun deleteSubtree(container: Container, index: Int): Removal {
        val removed = subtreeOf(container, index).toSet()
        if (removed.isEmpty()) {
            return Removal(container, emptyList(), emptyMap())
        }

        val survivors = container.cells.indices.filter { it !in removed }
        val reindex = survivors.withIndex().associate { (new, old) -> old to new }

        val cells = survivors.map { old ->
            val cell = container.cells[old]
            // Parents are indices, so they must be rewritten to the new
            // numbering or the tree silently re-parents itself.
            cell.copy(col = if (cell.col == ROOT_PARENT) ROOT_PARENT else reindex.getValue(cell.col))
        }

        val trimmed = container.copy(cells = cells)
        return Removal(
            container = if (cells.isEmpty()) trimmed else reflow(trimmed),
            removedIndices = removed.sorted(),
            reindex = reindex,
        )
    }

    /**
     * Repositions every node from the tree structure, keeping the ROOT fixed.
     *
     * Anchoring on the root means adding a node never yanks the part of the
     * diagram the teacher is looking at out from under them — new nodes appear
     * on the right and siblings spread around their parent instead.
     *
     * Sizes are preserved: a node resized by hand keeps its size.
     */
    fun reflow(container: Container): Container {
        if (container.cells.isEmpty()) return container
        val rootIndex = container.cells.indexOfFirst { it.col == ROOT_PARENT }
        if (rootIndex < 0) return container

        val heights = FloatArray(container.cells.size)
        computeSubtreeHeight(container, rootIndex, heights)

        val root = container.cells[rootIndex]
        val cells = container.cells.toMutableList()
        place(container, cells, rootIndex, root.left, root.centerY, heights)

        val placed = container.copy(cells = cells)
        val bounds = placed.bounds()
        return placed.copy(x = bounds[0], y = bounds[1])
    }

    /**
     * Total vertical space a node's subtree needs, filled into [out].
     *
     * A leaf takes its own height; a parent takes the sum of its children plus
     * the gaps between them, so sibling subtrees never overlap however deep
     * they grow. That is the property [reflow] exists to guarantee.
     */
    private fun computeSubtreeHeight(
        container: Container,
        index: Int,
        out: FloatArray,
        depth: Int = 0,
    ): Float {
        val cell = container.cells[index]
        val children = childrenOf(container, index)
        if (children.isEmpty() || depth > container.cells.size) {
            out[index] = cell.height
            return cell.height
        }
        var total = 0f
        children.forEachIndexed { i, child ->
            if (i > 0) total += SIBLING_GAP
            total += computeSubtreeHeight(container, child, out, depth + 1)
        }
        // Never smaller than the node itself, or a wide parent with one small
        // child would have its own box overlap the sibling below.
        out[index] = maxOf(total, cell.height)
        return out[index]
    }

    /** Places [index] with its left edge at [left], centred on [centerY]. */
    private fun place(
        container: Container,
        cells: MutableList<ContainerCell>,
        index: Int,
        left: Float,
        centerY: Float,
        heights: FloatArray,
        depth: Int = 0,
    ) {
        val cell = cells[index]
        val top = centerY - cell.height / 2f
        cells[index] = cell.copy(
            left = left,
            top = top,
            right = left + cell.width,
            bottom = top + cell.height,
        )

        val children = childrenOf(container, index)
        if (children.isEmpty() || depth > container.cells.size) return

        // Children are stacked as one block centred on the parent, so the
        // parent's connector meets the middle of its children's spine.
        var total = 0f
        children.forEachIndexed { i, child ->
            if (i > 0) total += SIBLING_GAP
            total += heights[child]
        }

        val childLeft = left + cell.width + LEVEL_GAP
        var cursor = centerY - total / 2f
        children.forEach { child ->
            val span = heights[child]
            place(container, cells, child, childLeft, cursor + span / 2f, heights, depth + 1)
            cursor += span + SIBLING_GAP
        }
    }

    /**
     * Elbow connector from a parent to one child, as world points.
     *
     * Three segments — out of the parent, along a shared vertical spine, into
     * the child — which is what the reference draws and why children of one
     * parent visibly branch off a single trunk. Returns an empty array when
     * either end is missing.
     */
    fun connectorPath(container: Container, parentIndex: Int, childIndex: Int): FloatArray {
        val parent = container.cells.getOrNull(parentIndex) ?: return FloatArray(0)
        val child = container.cells.getOrNull(childIndex) ?: return FloatArray(0)
        val spineX = (parent.right + child.left) / 2f
        return floatArrayOf(
            parent.right, parent.centerY,
            spineX, parent.centerY,
            spineX, child.centerY,
            child.left, child.centerY,
        )
    }

    /** [ContainerCell.col] value marking the root, which has no parent. */
    const val ROOT_PARENT = -1

    /** Room for a couple of handwritten words, matching the reference boxes. */
    const val NODE_WIDTH = 300f
    const val NODE_HEIGHT = 130f

    /** Horizontal gap between generations, and vertical gap between siblings. */
    const val LEVEL_GAP = 150f
    const val SIBLING_GAP = 60f
}
