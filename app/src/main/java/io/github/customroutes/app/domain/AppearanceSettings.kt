package io.github.customroutes.app.domain

data class AppearanceSettings(
    val adjustSmallHolds: Boolean = true,
    val borderWidthPercent: Int = DEFAULT_BORDER_WIDTH_PERCENT,
    val exportDimmingPercent: Int = DEFAULT_EXPORT_DIMMING_PERCENT,
) {
    init {
        require(borderWidthPercent in MIN_BORDER_WIDTH_PERCENT..MAX_BORDER_WIDTH_PERCENT)
        require(borderWidthPercent % BORDER_WIDTH_STEP_PERCENT == 0)
        require(exportDimmingPercent in MIN_EXPORT_DIMMING_PERCENT..MAX_EXPORT_DIMMING_PERCENT)
        require(exportDimmingPercent % EXPORT_DIMMING_STEP_PERCENT == 0)
    }
}

fun normalizeBorderWidthPercent(value: Int): Int =
    value.coerceIn(MIN_BORDER_WIDTH_PERCENT, MAX_BORDER_WIDTH_PERCENT)
        .let { ((it + BORDER_WIDTH_STEP_PERCENT / 2) / BORDER_WIDTH_STEP_PERCENT) * BORDER_WIDTH_STEP_PERCENT }

fun normalizeExportDimmingPercent(value: Int): Int =
    value.coerceIn(MIN_EXPORT_DIMMING_PERCENT, MAX_EXPORT_DIMMING_PERCENT)
        .let { ((it + EXPORT_DIMMING_STEP_PERCENT / 2) / EXPORT_DIMMING_STEP_PERCENT) * EXPORT_DIMMING_STEP_PERCENT }

fun AppearanceSettings.exportDimmingAlpha(hasRouteHolds: Boolean): Int =
    if (hasRouteHolds) (exportDimmingPercent * 255f / 100f).toInt() else 0

const val MIN_BORDER_WIDTH_PERCENT = 50
const val MAX_BORDER_WIDTH_PERCENT = 200
const val BORDER_WIDTH_STEP_PERCENT = 10
const val DEFAULT_BORDER_WIDTH_PERCENT = 100
const val MIN_EXPORT_DIMMING_PERCENT = 0
const val MAX_EXPORT_DIMMING_PERCENT = 60
const val EXPORT_DIMMING_STEP_PERCENT = 5
const val DEFAULT_EXPORT_DIMMING_PERCENT = 60
val DEFAULT_APPEARANCE_SETTINGS = AppearanceSettings()
