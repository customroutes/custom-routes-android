package io.github.customroutes.app.domain

enum class HoldRole {
    START,
    FINISH,
    REGULAR,
    FEET_ONLY;

    val argb: Int
        get() = when (this) {
            START -> 0xFFEF4444.toInt()
            FINISH -> 0xFF2563EB.toInt()
            REGULAR -> 0xFFF97316.toInt()
            FEET_ONLY -> 0xFFFACC15.toInt()
        }
}

data class RouteHold(
    val id: String,
    val role: HoldRole,
    val maskRegion: MaskRegion,
)

data class RouteProject(
    val id: String,
    val name: String?,
    val sourceFileName: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val workingWidth: Int,
    val workingHeight: Int,
    val holds: List<RouteHold> = emptyList(),
    val updatedAtEpochMillis: Long,
) {
    val isComplete: Boolean
        get() = holds.any { it.role == HoldRole.START } &&
            holds.any { it.role == HoldRole.FINISH }

    fun withHold(hold: RouteHold, updatedAt: Long): RouteProject = copy(
        holds = holds.filterNot { it.id == hold.id } + hold,
        updatedAtEpochMillis = nextUpdateTimestamp(updatedAt),
    )

    fun removeHold(id: String, updatedAt: Long): RouteProject = copy(
        holds = holds.filterNot { it.id == id },
        updatedAtEpochMillis = nextUpdateTimestamp(updatedAt),
    )

    fun changeRole(id: String, role: HoldRole, updatedAt: Long): RouteProject = copy(
        holds = holds.map { if (it.id == id) it.copy(role = role) else it },
        updatedAtEpochMillis = nextUpdateTimestamp(updatedAt),
    )

    fun rename(name: String?, updatedAt: Long): RouteProject = copy(
        name = name?.trim()?.takeIf { it.isNotEmpty() },
        updatedAtEpochMillis = nextUpdateTimestamp(updatedAt),
    )

    fun holdAt(point: SourcePoint): RouteHold? = holds.lastOrNull { it.maskRegion.contains(point) }

    private fun nextUpdateTimestamp(candidate: Long): Long =
        if (updatedAtEpochMillis == Long.MAX_VALUE) Long.MAX_VALUE
        else maxOf(candidate, updatedAtEpochMillis + 1L)
}
