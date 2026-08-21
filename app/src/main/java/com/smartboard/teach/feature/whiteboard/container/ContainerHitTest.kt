package com.smartboard.teach.feature.whiteboard.container

import com.smartboard.teach.domain.model.Container

/** Which cell of which container a world point falls in. */
data class CellHit(val containerId: String, val cellIndex: Int)

/**
 * Resolves the cell under the pen.
 *
 * A linear scan, deliberately: a page holds a handful of containers, and this
 * runs once per pen-DOWN rather than per sample. Bounds reject a container in
 * four comparisons before its cells are considered, so even an unusually busy
 * page stays trivial. A spatial index would be a second structure to keep in
 * sync with a SnapshotStateList mutated from six call sites, for no measurable
 * gain.
 */
object ContainerHitTest {

    /**
     * Topmost container whose cell contains the point, or null for free ink.
     *
     * Iterated in reverse so the most recently added container wins where two
     * overlap — the same "last drawn is on top" rule the stroke hit-test uses.
     */
    fun cellAt(containers: List<Container>, worldX: Float, worldY: Float): CellHit? {
        for (i in containers.indices.reversed()) {
            val container = containers[i]
            val bounds = container.bounds()
            if (worldX < bounds[0] || worldX > bounds[2] ||
                worldY < bounds[1] || worldY > bounds[3]
            ) {
                continue
            }
            val cellIndex = container.cellIndexAt(worldX, worldY)
            if (cellIndex >= 0) return CellHit(container.id, cellIndex)
        }
        return null
    }

    /** Topmost container whose BOUNDS contain the point, cell or not. */
    fun containerAt(containers: List<Container>, worldX: Float, worldY: Float): Container? {
        for (i in containers.indices.reversed()) {
            val container = containers[i]
            val bounds = container.bounds()
            if (worldX >= bounds[0] && worldX <= bounds[2] &&
                worldY >= bounds[1] && worldY <= bounds[3]
            ) {
                return container
            }
        }
        return null
    }

    /** True when [rect] (left, top, right, bottom) fully encloses the container. */
    fun isFullyInside(container: Container, rect: FloatArray): Boolean {
        val b = container.bounds()
        return b[0] >= rect[0] && b[1] >= rect[1] && b[2] <= rect[2] && b[3] <= rect[3]
    }
}
