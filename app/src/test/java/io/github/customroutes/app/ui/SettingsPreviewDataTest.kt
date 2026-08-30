package io.github.customroutes.app.ui

import io.github.customroutes.app.domain.HoldRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPreviewDataTest {
    @Test
    fun previewContainsOneInBoundsHoldForEveryRole() {
        assertEquals(HoldRole.entries.toSet(), SETTINGS_PREVIEW_HOLDS.map { it.role }.toSet())
        assertEquals(HoldRole.entries.size, SETTINGS_PREVIEW_HOLDS.size)

        SETTINGS_PREVIEW_HOLDS.forEach { hold ->
            assertTrue(hold.points.size >= 3)
            assertTrue(hold.points.all { it.x in 0f..SETTINGS_PREVIEW_WIDTH })
            assertTrue(hold.points.all { it.y in 0f..SETTINGS_PREVIEW_HEIGHT })
        }
    }
}
