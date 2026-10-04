package com.isaac.ridelab.ui.theme

import androidx.compose.ui.graphics.Color
import com.isaac.ridelab.data.model.ThemePalette

val ThemePalette.accentColor: Color get() = Color(when (this) {
    ThemePalette.MINT -> 0xFF35D0BA
    ThemePalette.BLUE -> 0xFF75BDFF
    ThemePalette.VIOLET -> 0xFFBAA0FF
    ThemePalette.AMBER -> 0xFFFFBF66
    ThemePalette.ROSE -> 0xFFFF93B6
})

val ThemePalette.panelColor: Color get() = androidx.compose.ui.graphics.lerp(
    RideColors.surface, accentColor, .07f)

object RideColors {
    val background = Color(0xFF090D12)
    val surface = Color(0xFF10161D)
    val elevated = Color(0xFF171F28)
    val accent = Color(0xFF35D0BA)
    val text = Color(0xFFF1F5F7)
    val secondaryText = Color(0xFF8F9BA8)
    val outline = Color(0xFF26313C)
    val danger = Color(0xFFFF626D)
    val heartRate = Color(0xFFFF7A84)
    val cadence = Color(0xFFE6B85C)
}
