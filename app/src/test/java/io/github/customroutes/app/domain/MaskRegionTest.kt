package io.github.customroutes.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskRegionTest {
    @Test
    fun mapsBetweenSourceAndLocalMaskCoordinates() {
        val region = MaskRegion(BinaryMask.empty(20, 10), SourceRect(100f, 50f, 300f, 150f))

        assertEquals(SourcePoint(10f, 5f), region.sourceToMask(SourcePoint(200f, 100f)))
        assertEquals(SourcePoint(200f, 100f), region.maskToSource(SourcePoint(10f, 5f)))
    }

    @Test
    fun sourceHitTestingUsesLocalBounds() {
        val region = MaskRegion(
            BinaryMask.empty(4, 4).paintCircle(1f, 1f, 0.6f, true),
            SourceRect(100f, 200f, 140f, 240f),
        )

        assertTrue(region.contains(SourcePoint(115f, 215f)))
        assertFalse(region.contains(SourcePoint(15f, 15f)))
    }

    @Test
    fun addingBrushOutsideMaskExpandsWithoutMovingExistingPixels() {
        val original = MaskRegion(
            BinaryMask.empty(4, 4).paintCircle(1f, 1f, 0.6f, true),
            SourceRect(100f, 100f, 140f, 140f),
        )

        val painted = original.paintSourceCircles(
            centers = listOf(SourcePoint(155f, 115f)),
            radius = 6f,
            value = true,
            sourceWidth = 300,
            sourceHeight = 300,
        )

        assertTrue(painted.contains(SourcePoint(115f, 115f)))
        assertTrue(painted.contains(SourcePoint(155f, 115f)))
        assertTrue(painted.mask.width > original.mask.width)
    }

    @Test
    fun cropInferenceStartsAtTwoTimesZoomAndCentersOnTap() {
        val visible = SourceRect(10f, 20f, 60f, 70f)
        val tap = SourcePoint(40f, 50f)

        assertEquals(SourceRect.full(100, 100), segmentationBounds(1.99f, tap, visible, 100, 100))
        assertEquals(SourceRect(10f, 20f, 70f, 80f), segmentationBounds(2f, tap, visible, 100, 100))
    }

    @Test
    fun tapCenteredCropKeepsItsSizeAtPhotoEdges() {
        val bounds = segmentationBounds(
            zoom = 4f,
            tap = SourcePoint(3f, 4f),
            visibleBounds = SourceRect(0f, 0f, 20f, 20f),
            sourceWidth = 100,
            sourceHeight = 100,
        )

        assertEquals(SourceRect(0f, 0f, 24f, 24f), bounds)
    }

    @Test
    fun detailCropReusesNearbyTapsButRefreshesNearAnEdge() {
        val prepared = SourceRect(20f, 20f, 80f, 80f)
        val sameSizeElsewhere = SourceRect(30f, 20f, 90f, 80f)

        assertTrue(canReuseDetailCrop(prepared, sameSizeElsewhere, SourcePoint(50f, 50f), 100, 100))
        assertFalse(canReuseDetailCrop(prepared, sameSizeElsewhere, SourcePoint(25f, 50f), 100, 100))
    }

    @Test
    fun detailCropRefreshesAfterAConsiderableZoomChange() {
        val prepared = SourceRect(20f, 20f, 80f, 80f)
        val muchSmaller = SourceRect(35f, 35f, 65f, 65f)

        assertFalse(canReuseDetailCrop(prepared, muchSmaller, SourcePoint(50f, 50f), 100, 100))
    }

    @Test
    fun detailCropAllowsAQuarterSizeChangeButNotMore() {
        val prepared = SourceRect(20f, 20f, 100f, 100f)
        val exactlyQuarterLarger = SourceRect(10f, 10f, 110f, 110f)
        val moreThanQuarterWider = SourceRect(9f, 10f, 110f, 110f)

        assertTrue(canReuseDetailCrop(prepared, exactlyQuarterLarger, SourcePoint(60f, 60f), 200, 200))
        assertFalse(canReuseDetailCrop(prepared, moreThanQuarterWider, SourcePoint(60f, 60f), 200, 200))
    }

    @Test
    fun detailCropReusesTapsAlongPhotoEdges() {
        val prepared = SourceRect(0f, 0f, 60f, 60f)
        val sameSize = SourceRect(0f, 0f, 60f, 60f)

        assertTrue(canReuseDetailCrop(prepared, sameSize, SourcePoint(2f, 2f), 100, 100))

        val bottomRight = SourceRect(40f, 40f, 100f, 100f)
        assertTrue(canReuseDetailCrop(bottomRight, bottomRight, SourcePoint(98f, 98f), 100, 100))
    }

    @Test
    fun cropReplacementPreservesExistingMaskOutsideCrop() {
        val existing = MaskRegion(
            BinaryMask.fromBooleans(10, 1, BooleanArray(10) { it == 1 || it == 8 }),
            SourceRect(0f, 0f, 10f, 1f),
        )
        val replacement = MaskRegion(
            BinaryMask.fromBooleans(5, 1, BooleanArray(5) { it == 2 }),
            SourceRect(0f, 0f, 5f, 1f),
        )

        val merged = existing.replaceInside(replacement)

        assertFalse(merged.contains(SourcePoint(1.5f, 0.5f)))
        assertTrue(merged.contains(SourcePoint(2.5f, 0.5f)))
        assertTrue(merged.contains(SourcePoint(8.5f, 0.5f)))
    }
}
