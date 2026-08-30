package io.github.customroutes.app.domain

import kotlin.math.abs
import kotlin.math.sqrt

data class BorderBands(
    val role: Float,
    val keyline: Float,
) {
    val selection: Float get() = keyline
    val normalTotal: Float get() = role + keyline
}

data class MaskRaster(
    val width: Int,
    val height: Int,
    val alpha: ByteArray,
    val sourceBounds: SourceRect,
)

fun adaptiveBorderBands(
    points: List<SourcePoint>,
    renderWidth: Float,
    renderHeight: Float,
    settings: AppearanceSettings = DEFAULT_APPEARANCE_SETTINGS,
    minimumBandWidth: Float = 1.5f,
): BorderBands {
    require(renderWidth > 0f && renderHeight > 0f && minimumBandWidth > 0f)
    val widthScale = settings.borderWidthPercent / 100f
    val defaultTotal = minOf(renderWidth, renderHeight) * DEFAULT_BORDER_FRACTION * widthScale
    val effectiveWidth = effectiveContourWidth(points)
    val adaptiveTotal = if (settings.adjustSmallHolds) {
        minOf(defaultTotal, effectiveWidth * SMALL_HOLD_BORDER_FRACTION)
    } else {
        defaultTotal
    }
    val scaledMinimum = minimumBandWidth * widthScale
    return BorderBands(
        role = (adaptiveTotal * ROLE_BAND_FRACTION).coerceAtLeast(scaledMinimum),
        keyline = (adaptiveTotal * KEYLINE_BAND_FRACTION).coerceAtLeast(scaledMinimum),
    )
}

fun MaskRegion.alphaRaster(checkCancelled: () -> Unit = {}): MaskRaster {
    val alpha = ByteArray(mask.width * mask.height)
    for (y in 0 until mask.height) {
        checkCancelled()
        for (x in 0 until mask.width) {
            if (mask[x, y]) alpha[y * mask.width + x] = 0xFF.toByte()
        }
    }
    return MaskRaster(mask.width, mask.height, alpha, sourceBounds)
}

private fun effectiveContourWidth(points: List<SourcePoint>): Float {
    if (points.size < 3) return 0f
    var twiceArea = 0f
    var perimeter = 0f
    points.indices.forEach { index ->
        val current = points[index]
        val next = points[(index + 1) % points.size]
        twiceArea += current.x * next.y - next.x * current.y
        val dx = next.x - current.x
        val dy = next.y - current.y
        perimeter += sqrt(dx * dx + dy * dy)
    }
    return if (perimeter == 0f) 0f else 2f * abs(twiceArea) / perimeter
}

private const val DEFAULT_BORDER_FRACTION = 0.0085f
private const val SMALL_HOLD_BORDER_FRACTION = 0.30f
private const val ROLE_BAND_FRACTION = 2f / 3f
private const val KEYLINE_BAND_FRACTION = 1f / 3f
