package io.github.customroutes.app.domain

import io.github.customroutes.app.ml.selectBestMask
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskSelectionTest {
    @Test
    fun highestIouMaskIsThresholdedAtZero() {
        val selected = selectBestMask(
            width = 2,
            height = 2,
            maskLogits = floatArrayOf(
                1f, 1f, 1f, 1f,
                -1f, 0.1f, -0.2f, 2f,
            ),
            predictedIous = floatArrayOf(0.3f, 0.9f),
        )

        assertFalse(selected[0, 0])
        assertTrue(selected[1, 0])
        assertFalse(selected[0, 1])
        assertTrue(selected[1, 1])
    }
}
