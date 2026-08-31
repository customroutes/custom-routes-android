package io.github.customroutes.app.data

import android.content.Context

private const val ROLE_COLOR_TIP_SHOWN = "role_color_tip_shown"

internal fun shouldShowRoleColorTip(readBoolean: (String, Boolean) -> Boolean): Boolean =
    !readBoolean(ROLE_COLOR_TIP_SHOWN, false)

class EditorHintPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("editor_hints", Context.MODE_PRIVATE)

    fun shouldShowRoleColorTip(): Boolean = shouldShowRoleColorTip(preferences::getBoolean)

    fun markRoleColorTipShown() {
        preferences.edit().putBoolean(ROLE_COLOR_TIP_SHOWN, true).apply()
    }
}
