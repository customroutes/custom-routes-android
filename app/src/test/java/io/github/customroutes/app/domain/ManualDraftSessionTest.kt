package io.github.customroutes.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualDraftSessionTest {
    @Test
    fun firstStrokeCanBeUndoneAndRedone() {
        val session = session()

        session.applyStroke(listOf(SourcePoint(50f, 40f)), radius = 5f, paint = true)
        assertTrue(session.draft.canCommit)
        assertTrue(session.canUndo)

        session.undo()
        assertFalse(session.draft.canCommit)
        assertTrue(session.canRedo)

        session.redo()
        assertTrue(session.draft.canCommit)
    }

    @Test
    fun roleChangesDoNotAlterStrokeHistory() {
        val session = session()
        session.applyStroke(listOf(SourcePoint(50f, 40f)), radius = 5f, paint = true)

        session.setRole(HoldRole.FINISH)
        session.undo()

        assertEquals(HoldRole.FINISH, session.draft.role)
        assertFalse(session.draft.canCommit)
    }

    @Test
    fun committedHoldIsTrimmedAndKeepsDraftRole() {
        val session = session()
        session.applyStroke(listOf(SourcePoint(50f, 40f), SourcePoint(70f, 40f)), radius = 5f, paint = true)
        session.setRole(HoldRole.START)

        val hold = session.toHold("manual")

        assertEquals("manual", hold.id)
        assertEquals(HoldRole.START, hold.role)
        assertTrue(hold.maskRegion.mask.hasForeground())
        assertTrue(hold.maskRegion.sourceBounds.width < 100f)
    }

    @Test
    fun doneIsOneProjectHistoryAction() {
        val session = session()
        session.applyStroke(listOf(SourcePoint(50f, 40f)), radius = 5f, paint = true)
        val initial = project()
        val history = EditorHistory(initial)

        history.apply(initial.withHold(session.toHold("manual"), updatedAt = 2))

        assertEquals(1, history.current.holds.size)
        assertTrue(history.undo().holds.isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun emptyDraftCannotBeCommitted() {
        session().toHold("empty")
    }

    private fun session() = ManualDraftSession.start(
        role = HoldRole.REGULAR,
        firstPoint = SourcePoint(50f, 40f),
        sourceWidth = 200,
        sourceHeight = 100,
    )

    private fun project() = RouteProject(
        id = "project",
        name = null,
        sourceFileName = "source",
        sourceWidth = 200,
        sourceHeight = 100,
        workingWidth = 200,
        workingHeight = 100,
        updatedAtEpochMillis = 1,
    )
}
