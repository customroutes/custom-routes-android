package io.github.customroutes.app.ui

import io.github.customroutes.app.domain.HoldRole
import io.github.customroutes.app.domain.SourcePoint

internal const val SETTINGS_PREVIEW_WIDTH = 800f
internal const val SETTINGS_PREVIEW_HEIGHT = 866f

internal data class SettingsPreviewHold(
    val role: HoldRole,
    val points: List<SourcePoint>,
)

internal val SETTINGS_PREVIEW_HOLDS = listOf(
    SettingsPreviewHold(
        HoldRole.START,
        points(207, 419, 163, 446, 138, 481, 132, 504, 139, 562, 156, 573, 157, 580, 183, 602,
            200, 612, 220, 615, 226, 622, 261, 622, 311, 605, 342, 580, 361, 549, 363, 510,
            351, 475, 318, 440, 281, 420, 247, 416),
    ),
    SettingsPreviewHold(
        HoldRole.FINISH,
        points(579, 140, 556, 169, 545, 177, 532, 208, 542, 244, 545, 278, 551, 288, 597, 322,
            630, 327, 655, 326, 708, 300, 719, 278, 723, 256, 712, 218, 686, 194, 670, 188,
            654, 167, 627, 156, 604, 140, 592, 137),
    ),
    SettingsPreviewHold(
        HoldRole.FEET_ONLY,
        points(275, 772, 267, 779, 260, 790, 256, 802, 256, 817, 265, 827, 274, 831, 291, 831,
            302, 827, 309, 820, 312, 814, 314, 806, 313, 800, 307, 792, 290, 777, 281, 772),
    ),
    SettingsPreviewHold(
        HoldRole.REGULAR,
        points(41, 389, 29, 397, 23, 412, 23, 425, 31, 453, 24, 525, 27, 534, 38, 544,
            61, 552, 75, 552, 83, 546, 83, 534, 76, 515, 68, 475, 66, 419, 57, 394, 51, 389),
    ),
)

private fun points(vararg coordinates: Int): List<SourcePoint> {
    require(coordinates.size % 2 == 0)
    return coordinates.asList().chunked(2) { (x, y) -> SourcePoint(x.toFloat(), y.toFloat()) }
}
