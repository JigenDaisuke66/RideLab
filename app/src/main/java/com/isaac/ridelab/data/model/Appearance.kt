package com.isaac.ridelab.data.model

enum class ThemePalette { MINT, BLUE, VIOLET, AMBER, ROSE }
enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class OverlayStyle { PANEL, STRIP, DASHBOARD }

/** Stable preference names; no translated text or sensor/control state. */
data class Appearance(
    val theme: ThemePalette = ThemePalette.MINT,
    val overlayPalette: ThemePalette? = null,
    val overlayStyle: OverlayStyle = OverlayStyle.PANEL,
    val mode: ThemeMode = ThemeMode.LIGHT,
) {
    val effectiveOverlayPalette get() = overlayPalette ?: theme

    companion object {
        fun restore(theme: String?, overlayPalette: String?, style: String?, mode: String? = null) = Appearance(
            ThemePalette.entries.firstOrNull { it.name == theme } ?: ThemePalette.MINT,
            ThemePalette.entries.firstOrNull { it.name == overlayPalette },
            OverlayStyle.entries.firstOrNull { it.name == style } ?: OverlayStyle.PANEL,
            ThemeMode.entries.firstOrNull { it.name == mode } ?: ThemeMode.LIGHT)
    }
}
