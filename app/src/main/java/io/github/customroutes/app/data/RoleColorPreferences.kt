package io.github.customroutes.app.data

import android.content.Context
import io.github.customroutes.app.domain.DEFAULT_ROLE_COLORS
import io.github.customroutes.app.domain.HoldRole

class RoleColorPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("role_colors", Context.MODE_PRIVATE)

    fun load(): Map<HoldRole, Int> = HoldRole.entries.associateWith { role ->
        preferences.getInt(role.name, checkNotNull(DEFAULT_ROLE_COLORS[role]))
    }

    fun set(role: HoldRole, argb: Int) {
        preferences.edit().putInt(role.name, argb).apply()
    }

    fun reset() {
        check(preferences.edit().clear().commit()) { "Role colors could not be reset" }
    }
}
