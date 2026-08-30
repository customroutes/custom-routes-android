package io.github.customroutes.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryMaskTest {
    @Test
    fun paintAndEraseCircle() {
        val painted = BinaryMask.empty(7, 7).paintCircle(3f, 3f, 2f, true)

        assertTrue(painted[3, 3])
        assertTrue(painted[3, 1])
        assertFalse(painted[0, 0])

        val erased = painted.paintCircle(3f, 3f, 1f, false)
        assertFalse(erased[3, 3])
        assertTrue(erased[3, 1])
    }

    @Test
    fun runLengthRoundTrip() {
        val original = BinaryMask.empty(10, 4)
            .paintCircle(2f, 2f, 1.5f, true)
            .paintCircle(8f, 1f, 1f, true)

        assertEquals(original, BinaryMask.fromRuns(10, 4, original.toRuns()))
    }

    @Test
    fun runLengthEncodingChecksCancellationThroughoutScan() {
        var checks = 0

        BinaryMask.fromBooleans(4, 4, BooleanArray(16) { true }).toRuns { checks++ }

        assertEquals(4, checks)
    }

    @Test
    fun boundaryExcludesInteriorPixels() {
        val mask = BinaryMask.fromBooleans(3, 3, BooleanArray(9) { true })

        val boundary = mask.boundaryPixels().toSet()

        assertEquals(8, boundary.size)
        assertFalse((1 to 1) in boundary)
    }

    @Test
    fun batchedStrokeAndCentroid() {
        val mask = BinaryMask.empty(8, 4).paintCircles(
            centers = listOf(1f to 1f, 5f to 1f),
            radius = 1f,
            value = true,
        )

        assertTrue(mask[1, 1])
        assertTrue(mask[5, 1])
        val centroid = requireNotNull(mask.centroid())
        assertEquals(3f, centroid.first, 0.01f)
        assertEquals(1f, centroid.second, 0.01f)
    }
}
