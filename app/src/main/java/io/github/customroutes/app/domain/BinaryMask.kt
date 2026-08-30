package io.github.customroutes.app.domain

import kotlin.math.ceil
import kotlin.math.floor

class BinaryMask private constructor(
    val width: Int,
    val height: Int,
    private val pixels: ByteArray,
) {
    private val boundary by lazy {
        buildList {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (this@BinaryMask[x, y] &&
                        (!this@BinaryMask[x - 1, y] || !this@BinaryMask[x + 1, y] ||
                            !this@BinaryMask[x, y - 1] || !this@BinaryMask[x, y + 1])
                    ) {
                        add(x to y)
                    }
                }
            }
        }
    }

    init {
        require(width > 0 && height > 0)
        require(pixels.size == width * height)
    }

    operator fun get(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && pixels[y * width + x].toInt() != 0

    fun paintCircle(centerX: Float, centerY: Float, radius: Float, value: Boolean): BinaryMask {
        return paintCircles(listOf(centerX to centerY), radius, value)
    }

    fun paintCircles(centers: List<Pair<Float, Float>>, radius: Float, value: Boolean): BinaryMask {
        return paintEllipses(centers, radius, radius, value)
    }

    fun paintEllipses(
        centers: List<Pair<Float, Float>>,
        radiusX: Float,
        radiusY: Float,
        value: Boolean,
        checkCancelled: () -> Unit = {},
    ): BinaryMask {
        require(radiusX > 0f && radiusY > 0f)
        val result = pixels.copyOf()
        centers.forEach { (centerX, centerY) ->
            checkCancelled()
            val minX = floor(centerX - radiusX).toInt().coerceAtLeast(0)
            val maxX = ceil(centerX + radiusX).toInt().coerceAtMost(width - 1)
            val minY = floor(centerY - radiusY).toInt().coerceAtLeast(0)
            val maxY = ceil(centerY + radiusY).toInt().coerceAtMost(height - 1)
            for (y in minY..maxY) {
                checkCancelled()
                for (x in minX..maxX) {
                    val dx = (x - centerX) / radiusX
                    val dy = (y - centerY) / radiusY
                    if (dx * dx + dy * dy <= 1f) {
                        result[y * width + x] = if (value) 1 else 0
                    }
                }
            }
        }
        return BinaryMask(width, height, result)
    }

    fun translated(
        width: Int,
        height: Int,
        offsetX: Int,
        offsetY: Int,
        checkCancelled: () -> Unit = {},
    ): BinaryMask {
        require(width > 0 && height > 0)
        val result = ByteArray(width * height)
        for (y in 0 until this.height) {
            checkCancelled()
            val targetY = y + offsetY
            if (targetY !in 0 until height) continue
            for (x in 0 until this.width) {
                val targetX = x + offsetX
                if (targetX in 0 until width && this[x, y]) result[targetY * width + targetX] = 1
            }
        }
        return BinaryMask(width, height, result)
    }

    fun centroid(): Pair<Float, Float>? {
        var count = 0L
        var sumX = 0L
        var sumY = 0L
        pixels.forEachIndexed { index, value ->
            if (value.toInt() != 0) {
                count++
                sumX += index % width
                sumY += index / width
            }
        }
        return if (count == 0L) null else sumX.toFloat() / count to sumY.toFloat() / count
    }

    fun hasForeground(): Boolean = pixels.any { it.toInt() != 0 }

    fun boundaryPixels(): Sequence<Pair<Int, Int>> = boundary.asSequence()

    fun toRuns(checkCancelled: () -> Unit = {}): IntArray {
        val runs = ArrayList<Int>()
        var index = 0
        var nextCancellationCheck = 0
        while (index < pixels.size) {
            if (index >= nextCancellationCheck) {
                checkCancelled()
                nextCancellationCheck += width
            }
            if (pixels[index].toInt() == 0) {
                index++
                continue
            }
            val start = index
            while (index < pixels.size && pixels[index].toInt() != 0) {
                index++
                if (index < pixels.size && index >= nextCancellationCheck) {
                    checkCancelled()
                    nextCancellationCheck += width
                }
            }
            runs += start
            runs += index - start
        }
        return runs.toIntArray()
    }

    override fun equals(other: Any?): Boolean = other is BinaryMask &&
        width == other.width && height == other.height && pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = 31 * (31 * width + height) + pixels.contentHashCode()

    companion object {
        fun empty(width: Int, height: Int): BinaryMask =
            BinaryMask(width, height, ByteArray(width * height))

        fun fromBooleans(width: Int, height: Int, values: BooleanArray): BinaryMask {
            require(values.size == width * height)
            return BinaryMask(width, height, ByteArray(values.size) { if (values[it]) 1 else 0 })
        }

        fun fromRuns(width: Int, height: Int, runs: IntArray): BinaryMask {
            require(runs.size % 2 == 0)
            val pixels = ByteArray(width * height)
            for (runIndex in runs.indices step 2) {
                val start = runs[runIndex]
                val length = runs[runIndex + 1]
                require(start >= 0 && length > 0 && start + length <= pixels.size)
                pixels.fill(1, start, start + length)
            }
            return BinaryMask(width, height, pixels)
        }
    }
}
