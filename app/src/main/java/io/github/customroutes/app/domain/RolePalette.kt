package io.github.customroutes.app.domain

val DEFAULT_ROLE_COLORS: Map<HoldRole, Int> = HoldRole.entries.associateWith(HoldRole::argb)

val ROLE_COLOR_CHOICES: List<Int> = listOf(
    0xFFEF4444.toInt(),
    0xFFEC4899.toInt(),
    0xFFA855F7.toInt(),
    0xFF6366F1.toInt(),
    0xFF2563EB.toInt(),
    0xFF0EA5E9.toInt(),
    0xFF06B6D4.toInt(),
    0xFF14B8A6.toInt(),
    0xFF22C55E.toInt(),
    0xFF84CC16.toInt(),
    0xFFFACC15.toInt(),
    0xFFEAB308.toInt(),
    0xFFF97316.toInt(),
    0xFFEA580C.toInt(),
    0xFFA16207.toInt(),
    0xFFF8FAFC.toInt(),
)
