package io.github.customroutes.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceSettingsTest {
    @Test
    fun exportDimmingDefaultsToSixtyPercent() {
        assertEquals(60, AppearanceSettings().exportDimmingPercent)
    }

    @Test
    fun borderWidthIsClampedAndSnapped() {
        assertEquals(50, normalizeBorderWidthPercent(1))
        assertEquals(100, normalizeBorderWidthPercent(104))
        assertEquals(110, normalizeBorderWidthPercent(105))
        assertEquals(200, normalizeBorderWidthPercent(999))
    }

    @Test
    fun exportDimmingIsClampedAndSnapped() {
        assertEquals(0, normalizeExportDimmingPercent(-1))
        assertEquals(25, normalizeExportDimmingPercent(24))
        assertEquals(60, normalizeExportDimmingPercent(99))
    }

    @Test
    fun exportDimmingRequiresAtLeastOneRouteHold() {
        val settings = AppearanceSettings()

        assertEquals(153, settings.exportDimmingAlpha(hasRouteHolds = true))
        assertEquals(0, settings.exportDimmingAlpha(hasRouteHolds = false))
    }
}
