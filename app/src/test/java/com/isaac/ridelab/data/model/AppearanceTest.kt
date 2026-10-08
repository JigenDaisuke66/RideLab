package com.isaac.ridelab.data.model

import org.junit.Assert.*
import org.junit.Test

class AppearanceTest {
    @Test fun oldPreferencesDefaultToLightAndExplicitModesRestore() {
        assertEquals(ThemeMode.LIGHT, Appearance.restore("BLUE", null, "STRIP").mode)
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, Appearance.restore("BLUE", null, "STRIP", mode.name).mode)
        }
        assertEquals(ThemeMode.LIGHT, Appearance.restore(null, null, null, "invalid").mode)
    }
    @Test fun freshInstallKeepsMintPanelAndFollowsTheme() {
        assertEquals(Appearance(), Appearance.restore(null, null, null))
        assertEquals(ThemePalette.MINT, Appearance().effectiveOverlayPalette)
    }
    @Test fun unknownPreferenceNamesFallBackSafely() {
        assertEquals(Appearance(), Appearance.restore("removed", "invalid", "future"))
    }
    @Test fun followThemeTracksNewSelection() {
        val preference = Appearance(theme = ThemePalette.BLUE)
        assertEquals(ThemePalette.BLUE, preference.effectiveOverlayPalette)
        assertEquals(ThemePalette.ROSE, preference.copy(theme = ThemePalette.ROSE).effectiveOverlayPalette)
    }
    @Test fun independentOverlayColorSurvivesThemeChanges() {
        val preference = Appearance(theme = ThemePalette.BLUE, overlayPalette = ThemePalette.AMBER)
        assertEquals(ThemePalette.AMBER, preference.copy(theme = ThemePalette.VIOLET).effectiveOverlayPalette)
    }
    @Test fun allSavedColorsAndStylesRoundTripUsingStableNames() {
        for (theme in ThemePalette.entries) for (overlay in ThemePalette.entries + listOf(null))
            for (style in OverlayStyle.entries) {
                val preference = Appearance(theme, overlay, style)
                assertEquals(preference, Appearance.restore(theme.name, overlay?.name, style.name))
            }
    }
}
