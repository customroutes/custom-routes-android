package io.github.customroutes.app.domain

import kotlin.math.ceil
import kotlin.math.floor

data class SourcePoint(val x: Float, val y: Float)

data class SourceRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(right > left && bottom > top)
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(point: SourcePoint): Boolean =
        point.x >= left && point.x < right && point.y >= top && point.y < bottom

    fun contains(other: SourceRect): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    companion object {
        fun full(width: Int, height: Int): SourceRect {
            require(width > 0 && height > 0)
            return SourceRect(0f, 0f, width.toFloat(), height.toFloat())
        }
    }
}

data class MaskRegion(
    val mask: BinaryMask,
    val sourceBounds: SourceRect,
) {
    val sourcePerMaskX: Float get() = sourceBounds.width / mask.width
    val sourcePerMaskY: Float get() = sourceBounds.height / mask.height

    fun contains(point: SourcePoint): Boolean {
        if (!sourceBounds.contains(point)) return false
        val local = sourceToMask(point)
        return mask[floor(local.x).toInt(), floor(local.y).toInt()]
    }

    fun sourceToMask(point: SourcePoint): SourcePoint = SourcePoint(
        x = (point.x - sourceBounds.left) / sourcePerMaskX,
        y = (point.y - sourceBounds.top) / sourcePerMaskY,
    )

    fun maskToSource(point: SourcePoint): SourcePoint = SourcePoint(
        x = sourceBounds.left + point.x * sourcePerMaskX,
        y = sourceBounds.top + point.y * sourcePerMaskY,
    )

    fun centroid(): SourcePoint? = mask.centroid()?.let { (x, y) ->
        maskToSource(SourcePoint(x + 0.5f, y + 0.5f))
    }

    fun foregroundPointInside(bounds: SourceRect): SourcePoint? {
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                if (!mask[x, y]) continue
                val point = maskToSource(SourcePoint(x + 0.5f, y + 0.5f))
                if (bounds.contains(point)) return point
            }
        }
        return null
    }

    fun replaceInside(replacement: MaskRegion): MaskRegion {
        if (replacement.sourceBounds.contains(sourceBounds)) return replacement.trimmed()
        val union = SourceRect(
            left = minOf(sourceBounds.left, replacement.sourceBounds.left),
            top = minOf(sourceBounds.top, replacement.sourceBounds.top),
            right = maxOf(sourceBounds.right, replacement.sourceBounds.right),
            bottom = maxOf(sourceBounds.bottom, replacement.sourceBounds.bottom),
        )
        val targetWidth = ceil(union.width / minOf(sourcePerMaskX, replacement.sourcePerMaskX)).toInt()
        val targetHeight = ceil(union.height / minOf(sourcePerMaskY, replacement.sourcePerMaskY)).toInt()
        val target = MaskRegion(BinaryMask.empty(targetWidth, targetHeight), union)
        val values = BooleanArray(targetWidth * targetHeight) { index ->
            val point = target.maskToSource(
                SourcePoint(index % targetWidth + 0.5f, index / targetWidth + 0.5f),
            )
            if (replacement.sourceBounds.contains(point)) replacement.contains(point) else contains(point)
        }
        return MaskRegion(BinaryMask.fromBooleans(targetWidth, targetHeight, values), union).trimmed()
    }

    fun trimmed(): MaskRegion {
        var minX = mask.width
        var minY = mask.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                if (!mask[x, y]) continue
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
        if (maxX < minX || maxY < minY) return this
        val width = maxX - minX + 1
        val height = maxY - minY + 1
        val values = BooleanArray(width * height) { index ->
            mask[minX + index % width, minY + index / width]
        }
        return MaskRegion(
            mask = BinaryMask.fromBooleans(width, height, values),
            sourceBounds = SourceRect(
                left = sourceBounds.left + minX * sourcePerMaskX,
                top = sourceBounds.top + minY * sourcePerMaskY,
                right = sourceBounds.left + (maxX + 1) * sourcePerMaskX,
                bottom = sourceBounds.top + (maxY + 1) * sourcePerMaskY,
            ),
        )
    }

    fun paintSourceCircles(
        centers: List<SourcePoint>,
        radius: Float,
        value: Boolean,
        sourceWidth: Int,
        sourceHeight: Int,
        checkCancelled: () -> Unit = {},
    ): MaskRegion {
        require(radius > 0f)
        if (centers.isEmpty()) return this
        val samples = interpolate(centers, radius / 2f)
        val minMaskX = if (value) {
            floor((samples.minOf { it.x } - radius - sourceBounds.left) / sourcePerMaskX).toInt().coerceAtMost(0)
        } else {
            0
        }.coerceAtLeast(ceil(-sourceBounds.left / sourcePerMaskX).toInt())
        val minMaskY = if (value) {
            floor((samples.minOf { it.y } - radius - sourceBounds.top) / sourcePerMaskY).toInt().coerceAtMost(0)
        } else {
            0
        }.coerceAtLeast(ceil(-sourceBounds.top / sourcePerMaskY).toInt())
        val maxMaskX = if (value) {
            ceil((samples.maxOf { it.x } + radius - sourceBounds.left) / sourcePerMaskX).toInt()
                .coerceAtLeast(mask.width)
        } else {
            mask.width
        }.coerceAtMost(floor((sourceWidth - sourceBounds.left) / sourcePerMaskX).toInt())
        val maxMaskY = if (value) {
            ceil((samples.maxOf { it.y } + radius - sourceBounds.top) / sourcePerMaskY).toInt()
                .coerceAtLeast(mask.height)
        } else {
            mask.height
        }.coerceAtMost(floor((sourceHeight - sourceBounds.top) / sourcePerMaskY).toInt())

        val expanded = mask.translated(
            width = maxMaskX - minMaskX,
            height = maxMaskY - minMaskY,
            offsetX = -minMaskX,
            offsetY = -minMaskY,
            checkCancelled = checkCancelled,
        )
        val bounds = SourceRect(
            left = sourceBounds.left + minMaskX * sourcePerMaskX,
            top = sourceBounds.top + minMaskY * sourcePerMaskY,
            right = sourceBounds.left + maxMaskX * sourcePerMaskX,
            bottom = sourceBounds.top + maxMaskY * sourcePerMaskY,
        )
        val localCenters = samples.map(boundsRegion(expanded, bounds)::sourceToMask).map { it.x to it.y }
        return MaskRegion(
            mask = expanded.paintEllipses(
                centers = localCenters,
                radiusX = (radius / sourcePerMaskX).coerceAtLeast(0.75f),
                radiusY = (radius / sourcePerMaskY).coerceAtLeast(0.75f),
                value = value,
                checkCancelled = checkCancelled,
            ),
            sourceBounds = bounds,
        )
    }

    private fun boundsRegion(mask: BinaryMask, bounds: SourceRect) = MaskRegion(mask, bounds)

    private fun interpolate(points: List<SourcePoint>, spacing: Float): List<SourcePoint> = buildList {
        add(points.first())
        points.zipWithNext().forEach { (start, end) ->
            val dx = end.x - start.x
            val dy = end.y - start.y
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            val steps = ceil(distance / spacing).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                val fraction = step.toFloat() / steps
                add(SourcePoint(start.x + dx * fraction, start.y + dy * fraction))
            }
        }
    }
}

fun segmentationBounds(
    zoom: Float,
    tap: SourcePoint,
    visibleBounds: SourceRect,
    sourceWidth: Int,
    sourceHeight: Int,
): SourceRect = if (zoom < 2f) {
    SourceRect.full(sourceWidth, sourceHeight)
} else {
    val cropWidth = ceil(visibleBounds.width.toDouble() * 1.2).toInt().coerceIn(1, sourceWidth)
    val cropHeight = ceil(visibleBounds.height.toDouble() * 1.2).toInt().coerceIn(1, sourceHeight)
    val left = floor(tap.x - cropWidth / 2f).toInt().coerceIn(0, sourceWidth - cropWidth)
    val top = floor(tap.y - cropHeight / 2f).toInt().coerceIn(0, sourceHeight - cropHeight)
    SourceRect(
        left = left.toFloat(),
        top = top.toFloat(),
        right = (left + cropWidth).toFloat(),
        bottom = (top + cropHeight).toFloat(),
    )
}

fun canReuseDetailCrop(
    preparedBounds: SourceRect,
    requestedBounds: SourceRect,
    tap: SourcePoint,
    sourceWidth: Int,
    sourceHeight: Int,
): Boolean {
    val widthRatio = requestedBounds.width / preparedBounds.width
    val heightRatio = requestedBounds.height / preparedBounds.height
    if (widthRatio !in 0.75f..1.25f || heightRatio !in 0.75f..1.25f) return false

    val horizontalMargin = preparedBounds.width * 0.15f
    val verticalMargin = preparedBounds.height * 0.15f
    val safeBounds = SourceRect(
        left = if (preparedBounds.left <= 0.5f) preparedBounds.left else preparedBounds.left + horizontalMargin,
        top = if (preparedBounds.top <= 0.5f) preparedBounds.top else preparedBounds.top + verticalMargin,
        right = if (preparedBounds.right >= sourceWidth - 0.5f) preparedBounds.right
        else preparedBounds.right - horizontalMargin,
        bottom = if (preparedBounds.bottom >= sourceHeight - 0.5f) preparedBounds.bottom
        else preparedBounds.bottom - verticalMargin,
    )
    return safeBounds.contains(tap)
}
