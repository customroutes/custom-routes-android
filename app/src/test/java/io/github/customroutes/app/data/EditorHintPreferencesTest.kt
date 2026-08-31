package io.github.customroutes.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorHintPreferencesTest {
    @Test
    fun roleColorTipIsShownUntilRecorded() {
        assertTrue(shouldShowRoleColorTip { _, default -> default })
        assertFalse(shouldShowRoleColorTip { _, _ -> true })
    }
}
