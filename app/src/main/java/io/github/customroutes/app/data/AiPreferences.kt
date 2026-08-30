package io.github.customroutes.app.data

import android.content.Context

private const val IMPROVE_AI_DETAIL_WHEN_ZOOMED = "improve_ai_detail_when_zoomed"

internal fun loadImproveAiDetailWhenZoomed(readBoolean: (String, Boolean) -> Boolean): Boolean =
    readBoolean(IMPROVE_AI_DETAIL_WHEN_ZOOMED, true)

class AiPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    fun loadImproveAiDetailWhenZoomed(): Boolean =
        loadImproveAiDetailWhenZoomed(preferences::getBoolean)

    fun setImproveAiDetailWhenZoomed(enabled: Boolean) {
        preferences.edit().putBoolean(IMPROVE_AI_DETAIL_WHEN_ZOOMED, enabled).apply()
    }
}
