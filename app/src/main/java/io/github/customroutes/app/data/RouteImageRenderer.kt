package io.github.customroutes.app.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Region
import io.github.customroutes.app.domain.AppearanceSettings
import io.github.customroutes.app.domain.HoldRole
import io.github.customroutes.app.domain.RouteProject
import io.github.customroutes.app.domain.SourcePoint
import io.github.customroutes.app.domain.adaptiveBorderBands
import io.github.customroutes.app.domain.outerContour
import kotlin.math.ceil
import kotlin.math.floor

internal object RouteImageRenderer {
    fun draw(
        bitmap: Bitmap,
        project: RouteProject,
        roleColors: Map<HoldRole, Int>,
        appearanceSettings: AppearanceSettings,
        dimmingAlpha: Int,
        checkCancelled: () -> Unit = {},
    ) {
        require(dimmingAlpha in 0..255)
        val canvas = Canvas(bitmap)
        val scaleX = bitmap.width.toFloat() / project.sourceWidth
        val scaleY = bitmap.height.toFloat() / project.sourceHeight
        val protectedMasks = protectedMasks(bitmap, project, scaleX, scaleY, checkCancelled)
        if (dimmingAlpha > 0 && project.holds.isNotEmpty()) {
            val dimSaveCount = canvas.save()
            canvas.clipOutPath(protectedMasks.boundaryPath)
            canvas.drawColor(dimmingAlpha shl 24)
            canvas.restoreToCount(dimSaveCount)
        }

        val saveCount = canvas.save()
        canvas.clipOutPath(protectedMasks.boundaryPath)
        try {
            project.holds.forEach { hold ->
                checkCancelled()
                val points = hold.maskRegion.outerContour(checkCancelled).map { point ->
                    SourcePoint(point.x * scaleX, point.y * scaleY)
                }
                if (points.size < 3) return@forEach
                val bands = adaptiveBorderBands(
                    points,
                    bitmap.width.toFloat(),
                    bitmap.height.toFloat(),
                    appearanceSettings,
                )
                val rolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = roleColors[hold.role] ?: hold.role.argb
                    strokeWidth = 2f * bands.role
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    style = Paint.Style.STROKE
                }
                val keylinePaint = Paint(rolePaint).apply {
                    color = 0xDD000000.toInt()
                    strokeWidth = 2f * bands.normalTotal
                }
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                canvas.drawPath(path, keylinePaint)
                canvas.drawPath(path, rolePaint)
            }
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    private fun protectedMasks(
        bitmap: Bitmap,
        project: RouteProject,
        scaleX: Float,
        scaleY: Float,
        checkCancelled: () -> Unit,
    ): Region {
        val protected = Region()
        project.holds.forEach { hold ->
            val region = hold.maskRegion
            for (y in 0 until region.mask.height) {
                checkCancelled()
                var x = 0
                while (x < region.mask.width) {
                    while (x < region.mask.width && !region.mask[x, y]) x++
                    val start = x
                    while (x < region.mask.width && region.mask[x, y]) x++
                    if (start == x) continue

                    val left = floor((region.sourceBounds.left + start * region.sourcePerMaskX) * scaleX)
                        .toInt()
                        .coerceIn(0, bitmap.width)
                    val top = floor((region.sourceBounds.top + y * region.sourcePerMaskY) * scaleY)
                        .toInt()
                        .coerceIn(0, bitmap.height)
                    val right = ceil((region.sourceBounds.left + x * region.sourcePerMaskX) * scaleX)
                        .toInt()
                        .coerceIn(0, bitmap.width)
                    val bottom = ceil((region.sourceBounds.top + (y + 1) * region.sourcePerMaskY) * scaleY)
                        .toInt()
                        .coerceIn(0, bitmap.height)
                    if (right > left && bottom > top) {
                        protected.op(Rect(left, top, right, bottom), Region.Op.UNION)
                    }
                }
            }
        }
        return protected
    }
}

internal fun thumbnailDimmingAlpha(): Int = THUMBNAIL_DIMMING_PERCENT * 255 / 100
