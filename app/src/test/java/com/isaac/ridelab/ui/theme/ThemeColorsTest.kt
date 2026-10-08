package com.isaac.ridelab.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.isaac.ridelab.data.model.ThemePalette
import org.junit.Assert.*
import org.junit.Test

class ThemeColorsTest {
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)

    @Test fun allPalettesChangeBackgroundAndHaveReadableTextInBothModes() {
        assertEquals(5, ThemePalette.entries.map { rideColorScheme(it, false).background }.distinct().size)
        assertEquals(5, ThemePalette.entries.map { rideColorScheme(it, true).background }.distinct().size)
        for (palette in ThemePalette.entries) {
            val light = rideColorScheme(palette, false)
            val dark = rideColorScheme(palette, true)
            assertTrue(light.background.luminance() > .8f)
            assertTrue(dark.background.luminance() < .1f)
            for (colors in listOf(light, dark)) {
                assertTrue(contrast(colors.onBackground, colors.background) >= 4.5f)
                assertTrue(contrast(colors.onSurface, colors.surface) >= 4.5f)
                assertTrue(contrast(colors.onSurfaceVariant, colors.surface) >= 4.5f)
                assertTrue(contrast(colors.onPrimary, colors.primary) >= 4.5f)
                assertTrue(contrast(colors.primary, colors.surface) >= 4.5f)
            }
        }
    }
}
