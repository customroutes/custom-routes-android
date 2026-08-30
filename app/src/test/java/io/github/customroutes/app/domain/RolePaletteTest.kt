package io.github.customroutes.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RolePaletteTest {
    @Test
    fun paletteHasSixteenDistinctColorsAndIncludesDefaults() {
        assertEquals(16, ROLE_COLOR_CHOICES.distinct().size)
        assertTrue(DEFAULT_ROLE_COLORS.values.all(ROLE_COLOR_CHOICES::contains))
    }
}
