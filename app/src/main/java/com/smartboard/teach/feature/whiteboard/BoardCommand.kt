package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.BoardBackground
import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.TextBox

/**
 * Undo/redo commands.
 *
 * Every command carries enough data to invert itself — an erase holds the
 * strokes it removed, an edit holds both before and after. No snapshots of the
 * whole page are taken, so the stacks stay cheap even on a dense board.
 */
sealed interface BoardCommand {
    data class AddStroke(val stroke: Stroke) : BoardCommand
    data class EraseStrokes(val strokes: List<Stroke>) : BoardCommand
    data class AddTextBox(val box: TextBox) : BoardCommand
    data class EditTextBox(val before: TextBox, val after: TextBox) : BoardCommand
    data class DeleteTextBox(val box: TextBox) : BoardCommand
    data class SetBackground(
        val before: BoardBackground?,
        val after: BoardBackground?,
    ) : BoardCommand

    data class ClearPage(
        val strokes: List<Stroke>,
        val boxes: List<TextBox>,
        val background: BoardBackground?,
        val containers: List<Container> = emptyList(),
    ) : BoardCommand

    /**
     * One completed move / resize / rotate gesture.
     *
     * Holds both states so undo is a straight swap. Recorded once on pointer
     * release rather than per frame — otherwise a single drag would fill the
     * entire undo stack and a teacher pressing undo would watch the shape
     * crawl back rather than jump back.
     */
    data class TransformSelection(
        val strokesBefore: List<Stroke>,
        val strokesAfter: List<Stroke>,
        val boxesBefore: List<TextBox>,
        val boxesAfter: List<TextBox>,
    ) : BoardCommand

    /** Deleting the current selection. */
    data class DeleteSelection(
        val strokes: List<Stroke>,
        val boxes: List<TextBox>,
    ) : BoardCommand

    /** Duplicating the current selection. */
    data class DuplicateSelection(
        val strokes: List<Stroke>,
        val boxes: List<TextBox>,
    ) : BoardCommand

    /**
     * Handwriting replaced by recognised text.
     *
     * One command rather than a delete plus an add, so undo restores the ink
     * in a single press — a teacher who did not want the conversion should not
     * have to press undo twice and see a half-converted board in between.
     */
    data class ConvertInkToText(
        val strokes: List<Stroke>,
        val box: TextBox,
        /**
         * The box this one grew out of, when the writing continued an earlier
         * conversion. Undo puts it back, so continuing a word and undoing it
         * is still a single press.
         */
        val replaced: TextBox? = null,
    ) : BoardCommand

    /** Inserting a table or mindmap. */
    data class AddContainer(val container: Container) : BoardCommand

    /** Removing one, along with every stroke written inside it. */
    data class DeleteContainer(
        val container: Container,
        val strokes: List<Stroke>,
    ) : BoardCommand

    /**
     * Any structural edit to a container: insert or delete a row or column,
     * add a sibling or child node, delete a node or subtree, reflow.
     *
     * One variant rather than nine, because every one of them has the same
     * inverse — the cell rects change, contained ink moves to follow, and some
     * ink may die. Nine variants would be nine near-identical arms in both
     * performUndo and performRedo.
     */
    data class EditContainer(
        val before: Container,
        val after: Container,
        val strokesBefore: List<Stroke>,
        val strokesAfter: List<Stroke>,
        /** Ink deleted with a removed row, column or subtree. */
        val removedStrokes: List<Stroke> = emptyList(),
    ) : BoardCommand
}

/**
 * Per-page undo/redo history.
 *
 * The stacks are deliberately not persisted across sessions: restoring a
 * lesson's ink is valuable, restoring the ability to undo into a previous
 * lesson is not, and it would cost a lot of storage to keep.
 */
class BoardHistory(private val maxDepth: Int = MAX_DEPTH) {

    private val undoStack = ArrayDeque<BoardCommand>()
    private val redoStack = ArrayDeque<BoardCommand>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun record(command: BoardCommand) {
        undoStack.addLast(command)
        // Any new action invalidates the redo branch.
        redoStack.clear()
        while (undoStack.size > maxDepth) undoStack.removeFirst()
    }

    fun undo(): BoardCommand? {
        val command = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(command)
        return command
    }

    fun redo(): BoardCommand? {
        val command = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(command)
        return command
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    private companion object {
        /**
         * Deep enough that a teacher never hits the wall in a lesson, shallow
         * enough that held stroke data cannot grow without bound.
         */
        const val MAX_DEPTH = 100
    }
}
