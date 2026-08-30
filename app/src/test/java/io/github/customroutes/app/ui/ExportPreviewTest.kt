package io.github.customroutes.app.ui

import io.github.customroutes.app.domain.AppearanceSettings
import io.github.customroutes.app.domain.BinaryMask
import io.github.customroutes.app.domain.DEFAULT_ROLE_COLORS
import io.github.customroutes.app.domain.HoldRole
import io.github.customroutes.app.domain.MaskRegion
import io.github.customroutes.app.domain.RouteHold
import io.github.customroutes.app.domain.RouteProject
import io.github.customroutes.app.domain.SourceRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportPreviewTest {
    @Test
    fun incompleteRouteMessageNamesMissingRoles() {
        assertEquals(
            "This route is missing a Start hold and a Finish hold. You can save the draft anyway.",
            incompleteExportMessage(project()),
        )
        assertEquals(
            "This route is missing a Finish hold. You can save the draft anyway.",
            incompleteExportMessage(project(HoldRole.START)),
        )
        assertEquals(
            "This route is missing a Start hold. You can save the draft anyway.",
            incompleteExportMessage(project(HoldRole.FINISH)),
        )
        assertNull(incompleteExportMessage(project(HoldRole.START, HoldRole.FINISH)))
    }

    @Test
    fun exportSizeUsesReadableUnits() {
        assertEquals("512 B", formatExportSize(512))
        assertEquals("1.5 KB", formatExportSize(1536))
        assertEquals("2.0 MB", formatExportSize(2L * 1024L * 1024L))
    }

    @Test
    fun candidateKeyChangesWithEveryExportInput() {
        val project = project(HoldRole.START, HoldRole.FINISH)
        val colors = DEFAULT_ROLE_COLORS.toMutableMap()
        val settings = AppearanceSettings(adjustSmallHolds = true, borderWidthPercent = 100, exportDimmingPercent = 60)
        val original = exportCandidateKey(project, colors, settings)

        colors[HoldRole.START] = 0xFF000000.toInt()

        assertEquals(DEFAULT_ROLE_COLORS, original.roleColors)
        assertNotEquals(original, exportCandidateKey(project.copy(updatedAtEpochMillis = 2), original.roleColors, settings))
        assertNotEquals(original, exportCandidateKey(project, colors, settings))
        assertNotEquals(original, exportCandidateKey(project, original.roleColors, settings.copy(exportDimmingPercent = 55)))
    }

    private fun project(vararg roles: HoldRole): RouteProject = RouteProject(
        id = "project",
        name = "Route",
        sourceFileName = "source.jpg",
        sourceWidth = 10,
        sourceHeight = 10,
        workingWidth = 10,
        workingHeight = 10,
        holds = roles.mapIndexed { index, role ->
            RouteHold(
                id = "hold-$index",
                role = role,
                maskRegion = MaskRegion(
                    mask = BinaryMask.fromBooleans(1, 1, booleanArrayOf(true)),
                    sourceBounds = SourceRect(0f, 0f, 1f, 1f),
                ),
            )
        },
        updatedAtEpochMillis = 1,
    )
}
