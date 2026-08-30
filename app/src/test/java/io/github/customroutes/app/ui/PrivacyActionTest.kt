package io.github.customroutes.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyActionTest {
    @Test
    fun bulkDeletionRequiresTwoConfirmations() {
        assertEquals(2, PrivacyAction.DELETE_PROJECTS.confirmationSteps)
        assertEquals(2, PrivacyAction.DELETE_ALL_DATA.confirmationSteps)
    }

    @Test
    fun modelDeletionRequiresOneConfirmation() {
        assertEquals(1, PrivacyAction.DELETE_MODEL.confirmationSteps)
    }
}
