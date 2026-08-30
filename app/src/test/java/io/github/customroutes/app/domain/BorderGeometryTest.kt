package io.github.customroutes.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderGeometryTest {
    @Test
    fun regularHoldUsesDefaultWidthWithTwoToOneBands() {
        val bands = adaptiveBorderBands(square(100f), 1000f, 1000f)

        assertEquals(8.5f, bands.normalTotal, 0.001f)
        assertEquals(2f, bands.role / bands.keyline, 0.001f)
    }

    @Test
    fun smallAndSkinnyHoldsUseAdaptiveWidth() {
        val small = adaptiveBorderBands(square(20f), 1000f, 1000f)
        val skinny = adaptiveBorderBands(rectangle(100f, 10f), 1000f, 1000f)

        assertEquals(6f, small.normalTotal, 0.001f)
        assertTrue(skinny.normalTotal < small.normalTotal)
    }

    @Test
    fun tinyHoldRetainsMinimumBandWidth() {
        val bands = adaptiveBorderBands(square(4f), 1000f, 1000f)

        assertEquals(1.5f, bands.role, 0.001f)
        assertEquals(1.5f, bands.keyline, 0.001f)
        assertEquals(bands.keyline, bands.selection, 0.001f)
    }

    @Test
    fun widthSettingScalesDefaultAndMinimumWidths() {
        val thin = AppearanceSettings(borderWidthPercent = 50)
        val thick = AppearanceSettings(borderWidthPercent = 200)

        assertEquals(4.25f, adaptiveBorderBands(square(100f), 1000f, 1000f, thin).normalTotal, 0.001f)
        assertEquals(17f, adaptiveBorderBands(square(100f), 1000f, 1000f, thick).normalTotal, 0.001f)
        assertEquals(0.75f, adaptiveBorderBands(square(1f), 1000f, 1000f, thin).role, 0.001f)
        assertEquals(3f, adaptiveBorderBands(square(1f), 1000f, 1000f, thick).keyline, 0.001f)
    }

    @Test
    fun disabledSmallHoldAdjustmentUsesDefaultWidthForEveryHold() {
        val fixed = AppearanceSettings(adjustSmallHolds = false)

        val bands = adaptiveBorderBands(square(4f), 1000f, 1000f, fixed)

        assertEquals(8.5f, bands.normalTotal, 0.001f)
    }

    @Test
    fun alphaRasterPreservesForegroundAndHoles() {
        val mask = BinaryMask.fromBooleans(
            width = 3,
            height = 2,
            values = booleanArrayOf(false, false, true, true, false, true),
        )
        val region = MaskRegion(mask, SourceRect(10f, 20f, 40f, 40f))

        val raster = region.alphaRaster()

        assertEquals(SourceRect(10f, 20f, 40f, 40f), raster.sourceBounds)
        assertEquals(listOf(0, 0, 255, 255, 0, 255), raster.alpha.map { it.toInt() and 0xFF })
    }

    private fun square(size: Float) = rectangle(size, size)

    private fun rectangle(width: Float, height: Float) = listOf(
        SourcePoint(0f, 0f),
        SourcePoint(width, 0f),
        SourcePoint(width, height),
        SourcePoint(0f, height),
    )
}
