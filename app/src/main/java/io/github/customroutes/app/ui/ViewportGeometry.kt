package io.github.customroutes.app.ui

import io.github.customroutes.app.domain.SourcePoint
import io.github.customroutes.app.domain.SourceRect
import kotlin.math.min

internal data class ImageTransform(val left: Float, val top: Float, val width: Float, val height: Float)

internal fun imageTransform(
    viewportWidth: Int,
    viewportHeight: Int,
    imageWidth: Int,
    imageHeight: Int,
    zoom: Float,
    pan: SourcePoint,
): ImageTransform {
    val fitScale = min(viewportWidth.toFloat() / imageWidth, viewportHeight.toFloat() / imageHeight)
    val width = imageWidth * fitScale * zoom
    val height = imageHeight * fitScale * zoom
    return ImageTransform(
        left = (viewportWidth - width) / 2f + pan.x,
        top = (viewportHeight - height) / 2f + pan.y,
        width = width,
        height = height,
    )
}

internal fun clampPan(
    pan: SourcePoint,
    viewportWidth: Int,
    viewportHeight: Int,
    imageWidth: Int,
    imageHeight: Int,
    zoom: Float,
): SourcePoint {
    val centered = imageTransform(viewportWidth, viewportHeight, imageWidth, imageHeight, zoom, SourcePoint(0f, 0f))
    val maxX = ((centered.width - viewportWidth) / 2f).coerceAtLeast(0f)
    val maxY = ((centered.height - viewportHeight) / 2f).coerceAtLeast(0f)
    return SourcePoint(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

internal fun screenToSource(
    point: SourcePoint,
    transform: ImageTransform,
    sourceWidth: Int,
    sourceHeight: Int,
): SourcePoint? {
    if (point.x < transform.left || point.x >= transform.left + transform.width ||
        point.y < transform.top || point.y >= transform.top + transform.height
    ) return null
    return SourcePoint(
        x = (point.x - transform.left) / transform.width * sourceWidth,
        y = (point.y - transform.top) / transform.height * sourceHeight,
    )
}

internal fun visibleSourceBounds(
    transform: ImageTransform,
    viewportWidth: Int,
    viewportHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
): SourceRect = SourceRect(
    left = ((-transform.left) / transform.width * sourceWidth).coerceIn(0f, sourceWidth.toFloat()),
    top = ((-transform.top) / transform.height * sourceHeight).coerceIn(0f, sourceHeight.toFloat()),
    right = ((viewportWidth - transform.left) / transform.width * sourceWidth).coerceIn(0f, sourceWidth.toFloat()),
    bottom = ((viewportHeight - transform.top) / transform.height * sourceHeight).coerceIn(0f, sourceHeight.toFloat()),
)
