package io.github.customroutes.app.data

import io.github.customroutes.app.domain.SourceRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropDecodePlanTest {
    @Test
    fun offsetSourceCropIsContainedByScaledImageBounds() {
        val plan = cropDecodePlan(
            sourceWidth = 4000,
            sourceHeight = 3000,
            bounds = SourceRect(1800f, 1200f, 3000f, 2100f),
        )

        assertTrue(plan.cropLeft >= 0 && plan.cropTop >= 0)
        assertTrue(plan.cropRight <= plan.targetWidth)
        assertTrue(plan.cropBottom <= plan.targetHeight)
        assertEquals(1024, plan.cropRight - plan.cropLeft)
        assertEquals(768, plan.cropBottom - plan.cropTop)
        assertEquals(1800f, plan.effectiveSourceBounds.left, 2f)
        assertEquals(1200f, plan.effectiveSourceBounds.top, 2f)
        assertEquals(3000f, plan.effectiveSourceBounds.right, 2f)
        assertEquals(2100f, plan.effectiveSourceBounds.bottom, 2f)
    }
}
