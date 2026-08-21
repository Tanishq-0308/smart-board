package com.smartboard.teach.feature.whiteboard.container

import com.smartboard.teach.domain.model.Container
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MindmapLayoutTest {

    private fun rootWithChildren(count: Int): Container {
        var map = MindmapLayout.create(x = 100f, y = 500f)
        repeat(count) { map = MindmapLayout.addChild(map, 0) }
        return map
    }

    // --- structure ---

    @Test
    fun `a new mindmap is a single root node`() {
        val map = MindmapLayout.create(x = 0f, y = 0f)
        assertEquals(1, map.cells.size)
        assertEquals(MindmapLayout.ROOT_PARENT, map.cells[0].col)
    }

    @Test
    fun `a child records its parent and is appended last`() {
        // Appending rather than inserting is what keeps every existing cell
        // index stable, so contained ink never needs retagging on an add.
        val map = MindmapLayout.addChild(MindmapLayout.create(0f, 0f), 0)
        assertEquals(2, map.cells.size)
        assertEquals(0, map.cells[1].col)
    }

    @Test
    fun `a sibling becomes a child of the same parent`() {
        var map = MindmapLayout.addChild(MindmapLayout.create(0f, 0f), 0)
        map = MindmapLayout.addSibling(map, 1)
        assertEquals(3, map.cells.size)
        assertEquals("both hang off the root", 0, map.cells[2].col)
    }

    @Test
    fun `the root gets no sibling because there is nowhere to put it`() {
        val map = MindmapLayout.create(0f, 0f)
        assertEquals(1, MindmapLayout.addSibling(map, 0).cells.size)
    }

    @Test
    fun `a subtree collects the node and everything under it`() {
        var map = rootWithChildren(2)          // 0 root, 1 and 2 children
        map = MindmapLayout.addChild(map, 1)   // 3, under child 1
        val subtree = MindmapLayout.subtreeOf(map, 1).toSet()
        assertEquals(setOf(1, 3), subtree)
    }

    // --- layout ---

    @Test
    fun `children sit to the right of their parent`() {
        val map = rootWithChildren(1)
        assertTrue(
            "a child must clear its parent's right edge",
            map.cells[1].left >= map.cells[0].right,
        )
    }

    @Test
    fun `sibling nodes never overlap`() {
        // The whole point of reflow: subtree heights are summed so branches
        // are allotted their own vertical space however deep they grow.
        var map = rootWithChildren(3)
        map = MindmapLayout.addChild(map, 1)
        map = MindmapLayout.addChild(map, 1)

        map.cells.indices.forEach { a ->
            map.cells.indices.forEach { b ->
                if (a < b) {
                    val x = map.cells[a]
                    val y = map.cells[b]
                    val apart = x.right <= y.left || y.right <= x.left ||
                        x.bottom <= y.top || y.bottom <= x.top
                    assertTrue("cells $a and $b overlap", apart)
                }
            }
        }
    }

    @Test
    fun `children are centred on their parent`() {
        val map = rootWithChildren(3)
        val children = MindmapLayout.childrenOf(map, 0)
        val top = map.cells[children.first()].centerY
        val bottom = map.cells[children.last()].centerY
        assertEquals(map.cells[0].centerY, (top + bottom) / 2f, 0.5f)
    }

    @Test
    fun `the root stays put when a node is added`() {
        // Adding a node must not yank the part of the diagram the teacher is
        // looking at out from under them.
        val one = rootWithChildren(1)
        val two = MindmapLayout.addChild(one, 0)
        assertEquals(one.cells[0].left, two.cells[0].left, 0.01f)
        assertEquals(one.cells[0].centerY, two.cells[0].centerY, 0.01f)
    }

    @Test
    fun `container bounds track the laid-out cells`() {
        val map = rootWithChildren(2)
        val bounds = map.bounds()
        assertEquals(bounds[0], map.x, 0.01f)
        assertEquals(bounds[1], map.y, 0.01f)
    }

    @Test
    fun `a hand-resized node keeps its size through a reflow`() {
        var map = rootWithChildren(1)
        val wider = map.cells[1].let { it.copy(right = it.left + 500f) }
        map = MindmapLayout.reflow(map.copy(cells = listOf(map.cells[0], wider)))
        assertEquals(500f, map.cells[1].width, 0.01f)
    }

    // --- deletion ---

    @Test
    fun `deleting a branch removes it and renumbers what is left`() {
        // The renumbering is the dangerous half: a stroke keeping its old
        // index would silently jump into a different node after a reload.
        var map = rootWithChildren(2)          // 0 root, 1, 2
        map = MindmapLayout.addChild(map, 1)   // 3, under 1

        val removal = MindmapLayout.deleteSubtree(map, 1)
        assertEquals(listOf(1, 3), removal.removedIndices)
        assertEquals(2, removal.container.cells.size)
        // Old cell 2 is the only survivor besides the root.
        assertEquals(1, removal.reindex[2])
        assertEquals(0, removal.reindex[0])
    }

    @Test
    fun `survivors keep pointing at the right parent after renumbering`() {
        var map = rootWithChildren(2)          // 0 root, 1, 2
        map = MindmapLayout.addChild(map, 2)   // 3, under 2

        val removal = MindmapLayout.deleteSubtree(map, 1)
        val newParentOfThree = removal.reindex.getValue(3)
        assertEquals(
            "child 3 must still hang off old node 2",
            removal.reindex.getValue(2),
            removal.container.cells[newParentOfThree].col,
        )
    }

    @Test
    fun `deleting the root empties the map`() {
        val removal = MindmapLayout.deleteSubtree(rootWithChildren(2), 0)
        assertTrue(removal.container.cells.isEmpty())
        assertEquals(3, removal.removedIndices.size)
    }

    // --- connectors ---

    @Test
    fun `a connector leaves the parent and arrives at the child`() {
        val map = rootWithChildren(1)
        val path = MindmapLayout.connectorPath(map, 0, 1)
        assertEquals(8, path.size)
        assertEquals(map.cells[0].right, path[0], 0.01f)
        assertEquals(map.cells[0].centerY, path[1], 0.01f)
        assertEquals(map.cells[1].left, path[6], 0.01f)
        assertEquals(map.cells[1].centerY, path[7], 0.01f)
    }

    @Test
    fun `siblings branch off one shared spine`() {
        // What makes the reference diagram read as a tree rather than a fan.
        val map = rootWithChildren(3)
        val spines = MindmapLayout.childrenOf(map, 0)
            .map { MindmapLayout.connectorPath(map, 0, it)[2] }
        assertEquals(spines[0], spines[1], 0.01f)
        assertEquals(spines[1], spines[2], 0.01f)
    }

    @Test
    fun `a connector to a missing node is empty rather than a crash`() {
        assertEquals(0, MindmapLayout.connectorPath(MindmapLayout.create(0f, 0f), 0, 9).size)
    }
}
