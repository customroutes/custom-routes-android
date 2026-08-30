package io.github.customroutes.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorHistoryTest {
    @Test
    fun undoAndRedoTraverseEdits() {
        val history = EditorHistory("first")
        history.apply("second")
        history.apply("third")

        assertEquals("second", history.undo())
        assertEquals("first", history.undo())
        assertTrue(history.canRedo)
        assertEquals("second", history.redo())
    }

    @Test
    fun newEditClearsRedoHistory() {
        val history = EditorHistory(1)
        history.apply(2)
        history.undo()
        history.apply(3)

        assertFalse(history.canRedo)
        assertEquals(3, history.current)
    }

    @Test
    fun historyDepthIsBounded() {
        val history = EditorHistory(0, maxDepth = 2)
        history.apply(1)
        history.apply(2)
        history.apply(3)

        assertEquals(2, history.undo())
        assertEquals(1, history.undo())
        assertEquals(1, history.undo())
    }
}
