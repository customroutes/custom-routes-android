package io.github.customroutes.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskContourTest {
    @Test
    fun contourUsesLargestComponentAndSourceCoordinates() {
        val values = BooleanArray(8 * 6)
        for (y in 1..4) for (x in 1..4) values[y * 8 + x] = true
        values[5 * 8 + 7] = true
        val region = MaskRegion(BinaryMask.fromBooleans(8, 6, values), SourceRect(100f, 200f, 180f, 260f))

        val contour = region.outerContour()

        assertEquals(8, contour.size)
        assertTrue(contour.all { it.x in 110f..150f && it.y in 210f..250f })
    }

    @Test
    fun contourIgnoresInternalHole() {
        val values = BooleanArray(5 * 5) { true }
        values[2 * 5 + 2] = false

        val contour = MaskRegion(
            BinaryMask.fromBooleans(5, 5, values),
            SourceRect(0f, 0f, 5f, 5f),
        ).outerContour()

        assertEquals(8, contour.size)
        assertTrue(contour.none { it.x in 1.5f..3.5f && it.y in 1.5f..3.5f })
    }

    @Test
    fun conservativeSmoothingPreservesConcaveCorners() {
        val values = BooleanArray(3 * 3) { index -> index / 3 == 0 || index % 3 == 0 }

        val contour = MaskRegion(
            BinaryMask.fromBooleans(3, 3, values),
            SourceRect(0f, 0f, 3f, 3f),
        ).outerContour()

        assertTrue(SourcePoint(1f, 1f) in contour)
    }

    @Test
    fun diagonallyTouchingPixelsRemainSeparateComponents() {
        val values = BooleanArray(3 * 3)
        values[0] = true
        values[1 * 3 + 1] = true

        val contour = MaskRegion(
            BinaryMask.fromBooleans(3, 3, values),
            SourceRect(0f, 0f, 3f, 3f),
        ).outerContour()

        assertTrue(contour.isNotEmpty())
        assertTrue(contour.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test(timeout = 5_000)
    fun longSerpentineComponentTracesWithinInteractiveBudget() {
        val size = 256
        val values = BooleanArray(size * size)
        for (x in 0 until size step 2) {
            for (y in 0 until size) values[y * size + x] = true
            val connectorY = if ((x / 2) % 2 == 0) size - 1 else 0
            if (x + 1 < size) values[connectorY * size + x + 1] = true
        }

        val contour = MaskRegion(
            BinaryMask.fromBooleans(size, size, values),
            SourceRect(0f, 0f, size.toFloat(), size.toFloat()),
        ).outerContour()

        assertTrue(contour.size > size)
    }
}
