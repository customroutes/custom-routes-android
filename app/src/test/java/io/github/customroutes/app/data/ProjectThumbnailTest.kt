package io.github.customroutes.app.data

import io.github.customroutes.app.domain.AppearanceSettings
import io.github.customroutes.app.domain.HoldRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProjectThumbnailTest {
    private val project = ProjectSummary(
        id = "project",
        name = null,
        updatedAtEpochMillis = 42L,
        holdCount = 2,
        isComplete = true,
    )

    @Test
    fun exportDimmingDoesNotInvalidateThumbnailSignature() {
        val signature = ThumbnailSignature.forProject(
            project,
            roleColors = mapOf(HoldRole.START to 0xFF00FF00.toInt()),
            appearanceSettings = AppearanceSettings(exportDimmingPercent = 0),
        )
        val changedExportDimming = ThumbnailSignature.forProject(
            project,
            roleColors = mapOf(HoldRole.START to 0xFF00FF00.toInt()),
            appearanceSettings = AppearanceSettings(exportDimmingPercent = 60),
        )

        assertEquals(signature, changedExportDimming)
    }

    @Test
    fun routeAppearanceChangesInvalidateThumbnailSignature() {
        val signature = ThumbnailSignature.forProject(
            project,
            roleColors = emptyMap(),
            appearanceSettings = AppearanceSettings(),
        )

        assertNotEquals(
            signature,
            ThumbnailSignature.forProject(
                project.copy(updatedAtEpochMillis = 43L),
                emptyMap(),
                AppearanceSettings(),
            ),
        )
        assertNotEquals(
            signature,
            ThumbnailSignature.forProject(project, mapOf(HoldRole.FINISH to 0xFF00FF00.toInt()), AppearanceSettings()),
        )
        assertNotEquals(
            signature,
            ThumbnailSignature.forProject(project, emptyMap(), AppearanceSettings(borderWidthPercent = 50)),
        )
        assertNotEquals(
            signature,
            ThumbnailSignature.forProject(project, emptyMap(), AppearanceSettings(adjustSmallHolds = false)),
        )
    }

    @Test
    fun decodeSizeFitsTheLongEdgeWithoutCropping() {
        assertEquals(ThumbnailDecodeSize(256, 192), thumbnailDecodeSize(4000, 3000))
        assertEquals(ThumbnailDecodeSize(144, 256), thumbnailDecodeSize(900, 1600))
        assertEquals(ThumbnailDecodeSize(100, 80), thumbnailDecodeSize(100, 80))
    }

    @Test
    fun thumbnailDimmingIsFixedAtSixtyPercent() {
        assertEquals(153, thumbnailDimmingAlpha())
    }
}
