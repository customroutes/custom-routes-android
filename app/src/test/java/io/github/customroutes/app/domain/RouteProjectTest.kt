package io.github.customroutes.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProjectTest {
    private val mask = MaskRegion(BinaryMask.empty(2, 2), SourceRect(0f, 0f, 100f, 80f))

    @Test
    fun routeRequiresAtLeastOneStartAndFinish() {
        var project = project()
        assertFalse(project.isComplete)

        project = project.withHold(RouteHold("start", HoldRole.START, mask), 2)
        assertFalse(project.isComplete)

        project = project.withHold(RouteHold("finish", HoldRole.FINISH, mask), 3)
        assertTrue(project.isComplete)
    }

    @Test
    fun changingRoleReplacesTheOnlyRole() {
        val project = project()
            .withHold(RouteHold("hold", HoldRole.REGULAR, mask), 2)
            .changeRole("hold", HoldRole.FEET_ONLY, 3)

        assertEquals(HoldRole.FEET_ONLY, project.holds.single().role)
    }

    @Test
    fun projectUpdatesRemainMonotonicWithinOneMillisecond() {
        val updated = project()
            .withHold(RouteHold("hold", HoldRole.REGULAR, mask), 1)
            .removeHold("hold", 1)

        assertEquals(3, updated.updatedAtEpochMillis)
    }

    @Test
    fun hitTestingReturnsTopmostOverlappingHold() {
        val painted = MaskRegion(
            BinaryMask.empty(2, 2).paintCircle(0f, 0f, 1f, true),
            SourceRect(0f, 0f, 100f, 80f),
        )
        val project = project()
            .withHold(RouteHold("first", HoldRole.REGULAR, painted), 2)
            .withHold(RouteHold("second", HoldRole.FINISH, painted), 3)

        assertEquals("second", project.holdAt(SourcePoint(10f, 10f))?.id)
        assertEquals(null, project.holdAt(SourcePoint(90f, 70f)))
    }

    private fun project() = RouteProject(
        id = "project",
        name = null,
        sourceFileName = "source.jpg",
        sourceWidth = 100,
        sourceHeight = 80,
        workingWidth = 10,
        workingHeight = 8,
        updatedAtEpochMillis = 1,
    )
}
