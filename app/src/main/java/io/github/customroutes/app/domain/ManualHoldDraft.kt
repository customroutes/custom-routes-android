package io.github.customroutes.app.domain

import kotlin.math.floor
import kotlin.math.max

data class ManualHoldDraft(
    val role: HoldRole,
    val maskRegion: MaskRegion,
) {
    val canCommit: Boolean get() = maskRegion.mask.hasForeground()
}

class ManualDraftSession private constructor(
    initialRole: HoldRole,
    initialMask: MaskRegion,
    private val sourceWidth: Int,
    private val sourceHeight: Int,
) {
    private val history = EditorHistory(initialMask)
    private var role = initialRole

    val draft: ManualHoldDraft get() = ManualHoldDraft(role, history.current)
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    fun setRole(role: HoldRole) {
        this.role = role
    }

    fun applyStroke(points: List<SourcePoint>, radius: Float, paint: Boolean) {
        if (points.isEmpty()) return
        history.apply(
            history.current.paintSourceCircles(
                centers = points,
                radius = radius,
                value = paint,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
            ),
        )
    }

    fun applyMask(maskRegion: MaskRegion) {
        history.apply(maskRegion)
    }

    fun undo() {
        history.undo()
    }

    fun redo() {
        history.redo()
    }

    fun toHold(id: String): RouteHold {
        check(draft.canCommit) { "A manual hold must contain painted pixels" }
        return RouteHold(id, role, history.current.trimmed())
    }

    companion object {
        private const val MAX_MANUAL_LONG_EDGE = 2048f

        fun start(
            role: HoldRole,
            firstPoint: SourcePoint,
            sourceWidth: Int,
            sourceHeight: Int,
        ): ManualDraftSession {
            require(SourceRect.full(sourceWidth, sourceHeight).contains(firstPoint))
            val sourcePerMaskPixel = max(1f, max(sourceWidth, sourceHeight) / MAX_MANUAL_LONG_EDGE)
            val left = floor(firstPoint.x / sourcePerMaskPixel) * sourcePerMaskPixel
            val top = floor(firstPoint.y / sourcePerMaskPixel) * sourcePerMaskPixel
            val right = minOf(sourceWidth.toFloat(), left + sourcePerMaskPixel)
            val bottom = minOf(sourceHeight.toFloat(), top + sourcePerMaskPixel)
            return ManualDraftSession(
                initialRole = role,
                initialMask = MaskRegion(BinaryMask.empty(1, 1), SourceRect(left, top, right, bottom)),
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
            )
        }
    }
}
