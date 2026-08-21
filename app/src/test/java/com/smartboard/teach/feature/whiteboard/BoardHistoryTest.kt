package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardHistoryTest {

    private fun stroke(id: String) = Stroke(
        id = id,
        tool = DrawTool.PEN,
        style = StrokeStyle(0xFF000000.toInt(), 4f),
        points = floatArrayOf(0f, 0f, 1f, 10f, 10f, 1f),
    )

    @Test
    fun `erase then undo restores the strokes`() {
        // Erasing used to remove strokes without ever recording the command,
        // so undo did nothing and the ink was gone for good.
        val history = BoardHistory()
        val erased = listOf(stroke("a"), stroke("b"))
        val live = mutableListOf<Stroke>().apply { addAll(erased) }

        live.removeAll(erased)
        history.record(BoardCommand.EraseStrokes(erased))
        assertTrue(history.canUndo)

        val command = history.undo() as BoardCommand.EraseStrokes
        live.addAll(command.strokes)
        assertEquals(2, live.size)
    }

    @Test
    fun `erase undo then redo erases again`() {
        val history = BoardHistory()
        val erased = listOf(stroke("a"))
        history.record(BoardCommand.EraseStrokes(erased))

        history.undo()
        assertTrue(history.canRedo)
        val redone = history.redo() as BoardCommand.EraseStrokes
        assertEquals("a", redone.strokes.first().id)
    }

    @Test
    fun `a new action clears the redo branch`() {
        val history = BoardHistory()
        history.record(BoardCommand.AddStroke(stroke("a")))
        history.undo()
        assertTrue(history.canRedo)

        history.record(BoardCommand.AddStroke(stroke("b")))
        assertFalse(history.canRedo)
    }

    @Test
    fun `history is bounded so a long lesson cannot grow without limit`() {
        val history = BoardHistory(maxDepth = 3)
        repeat(10) { history.record(BoardCommand.AddStroke(stroke("s$it"))) }

        var undone = 0
        while (history.undo() != null) undone++
        assertEquals(3, undone)
    }

    @Test
    fun `undo on an empty history is a no-op`() {
        val history = BoardHistory()
        assertFalse(history.canUndo)
        assertEquals(null, history.undo())
    }
}
