package io.github.customroutes.app.ui

import io.github.customroutes.app.domain.SourcePoint
import io.github.customroutes.app.domain.SourceRect
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportGeometryTest {
    @Test
    fun fitZoomCentersImageAndPreventsPanOnLetterboxedAxis() {
        val clamped = clampPan(SourcePoint(100f, 100f), 1000, 500, 1000, 1000, 1f)

        assertEquals(SourcePoint(0f, 0f), clamped)
        assertEquals(ImageTransform(250f, 0f, 500f, 500f), imageTransform(1000, 500, 1000, 1000, 1f, clamped))
    }

    @Test
    fun zoomedPanIsClampedToImageOverflow() {
        assertEquals(
            SourcePoint(500f, -250f),
            clampPan(SourcePoint(900f, -900f), 1000, 500, 1000, 500, 2f),
        )
    }

    @Test
    fun visibleBoundsMapBackToSourceCoordinates() {
        val transform = imageTransform(1000, 500, 1000, 500, 2f, SourcePoint(0f, 0f))

        assertEquals(SourceRect(250f, 125f, 750f, 375f), visibleSourceBounds(transform, 1000, 500, 1000, 500))
        assertEquals(SourcePoint(500f, 250f), screenToSource(SourcePoint(500f, 250f), transform, 1000, 500))
    }
}
