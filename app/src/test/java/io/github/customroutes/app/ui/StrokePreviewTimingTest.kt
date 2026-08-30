package io.github.customroutes.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StrokePreviewTimingTest {
    @Test
    fun firstPreviewPublishesImmediately() {
        assertEquals(0L, strokePreviewDelayNanos(lastPublishNanos = 0L, nowNanos = 1L))
    }

    @Test
    fun consecutivePreviewsAreRateLimited() {
        assertEquals(
            20_000_000L,
            strokePreviewDelayNanos(lastPublishNanos = 100_000_000L, nowNanos = 105_000_000L),
        )
        assertEquals(
            0L,
            strokePreviewDelayNanos(lastPublishNanos = 100_000_000L, nowNanos = 125_000_000L),
        )
    }
}
