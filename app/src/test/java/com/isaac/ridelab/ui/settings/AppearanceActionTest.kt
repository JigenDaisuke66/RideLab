package com.isaac.ridelab.ui.settings

import com.isaac.ridelab.data.model.*
import org.junit.Assert.*
import org.junit.Test

class AppearanceActionTest {
    @Test fun independentSelectionsDoNotOverwriteOtherPreferences() {
        val initial = Appearance(theme = ThemePalette.BLUE, overlayPalette = ThemePalette.ROSE,
            overlayStyle = OverlayStyle.STRIP, mode = ThemeMode.DARK)
        val changed = initial.changedBy(AppearanceAction.Palette(ThemePalette.AMBER))
            .changedBy(AppearanceAction.Style(OverlayStyle.DASHBOARD))
        assertEquals(ThemePalette.AMBER, changed.theme)
        assertEquals(ThemePalette.ROSE, changed.overlayPalette)
        assertEquals(ThemeMode.DARK, changed.mode)
        assertEquals(OverlayStyle.DASHBOARD, changed.overlayStyle)
        assertEquals(ThemePalette.BLUE, initial.theme)
    }
    @Test fun followThemeClearsOnlyOverlayOverride() {
        val changed = Appearance(theme = ThemePalette.VIOLET, overlayPalette = ThemePalette.BLUE)
            .changedBy(AppearanceAction.OverlayPalette(null)).changedBy(AppearanceAction.Mode(ThemeMode.SYSTEM))
        assertEquals(ThemePalette.VIOLET, changed.effectiveOverlayPalette)
        assertNull(changed.overlayPalette)
        assertEquals(ThemeMode.SYSTEM, changed.mode)
    }
}
