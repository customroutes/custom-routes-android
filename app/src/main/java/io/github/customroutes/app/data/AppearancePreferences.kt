package io.github.customroutes.app.data

import android.content.Context
import io.github.customroutes.app.domain.AppearanceSettings
import io.github.customroutes.app.domain.DEFAULT_APPEARANCE_SETTINGS
import io.github.customroutes.app.domain.normalizeBorderWidthPercent
import io.github.customroutes.app.domain.normalizeExportDimmingPercent

class AppearancePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("appearance_settings", Context.MODE_PRIVATE)

    fun load(): AppearanceSettings = AppearanceSettings(
        adjustSmallHolds = preferences.getBoolean(ADJUST_SMALL_HOLDS, DEFAULT_APPEARANCE_SETTINGS.adjustSmallHolds),
        borderWidthPercent = normalizeBorderWidthPercent(
            preferences.getInt(BORDER_WIDTH_PERCENT, DEFAULT_APPEARANCE_SETTINGS.borderWidthPercent),
        ),
        exportDimmingPercent = normalizeExportDimmingPercent(
            preferences.getInt(EXPORT_DIMMING_PERCENT, DEFAULT_APPEARANCE_SETTINGS.exportDimmingPercent),
        ),
    )

    fun setAdjustSmallHolds(enabled: Boolean) {
        preferences.edit().putBoolean(ADJUST_SMALL_HOLDS, enabled).apply()
    }

    fun setBorderWidthPercent(percent: Int) {
        preferences.edit().putInt(BORDER_WIDTH_PERCENT, normalizeBorderWidthPercent(percent)).apply()
    }

    fun setExportDimmingPercent(percent: Int) {
        preferences.edit().putInt(EXPORT_DIMMING_PERCENT, normalizeExportDimmingPercent(percent)).apply()
    }

    fun reset() {
        check(preferences.edit().clear().commit()) { "Appearance settings could not be reset" }
    }

    private companion object {
        const val ADJUST_SMALL_HOLDS = "adjust_small_holds"
        const val BORDER_WIDTH_PERCENT = "border_width_percent"
        const val EXPORT_DIMMING_PERCENT = "export_dimming_percent"
    }
}
