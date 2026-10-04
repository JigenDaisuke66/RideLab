package com.isaac.ridelab.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.isaac.ridelab.data.repository.AppearanceSettings
import com.isaac.ridelab.data.model.Appearance
import com.isaac.ridelab.data.model.ThemePalette
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = RideColors.accent, onPrimary = Color(0xFF002F29),
    primaryContainer = Color(0xFF175047), onPrimaryContainer = Color(0xFF9EF2DF),
    secondary = Color(0xFFAFBCC7), secondaryContainer = Color(0xFF171F28), onSecondaryContainer = Color(0xFFF1F5F7),
    background = RideColors.background, surface = RideColors.surface, surfaceVariant = RideColors.elevated,
    surfaceContainer = Color(0xFF10161D), surfaceContainerHigh = Color(0xFF171F28),
    surfaceContainerHighest = Color(0xFF171F28), surfaceContainerLow = Color(0xFF10161D),
    onBackground = RideColors.text, onSurface = RideColors.text, onSurfaceVariant = RideColors.secondaryText,
    outline = RideColors.outline, outlineVariant = RideColors.outline,
    error = RideColors.danger, errorContainer = Color(0xFF46151C), onErrorContainer = Color(0xFFFFDADC))

@Composable
fun RideLabTheme(content: @Composable () -> Unit) {
    val (appearance, _) = rememberAppearance()
    val palette = appearance.theme
    val colors = remember(palette) { if (palette == ThemePalette.MINT) DarkColors else DarkColors.copy(
        primary = palette.accentColor, onPrimary = RideColors.background,
        primaryContainer = lerp(RideColors.surface, palette.accentColor, .25f),
        onPrimaryContainer = palette.accentColor,
        secondary = palette.accentColor,
        surface = palette.panelColor,
        secondaryContainer = lerp(RideColors.elevated, palette.accentColor, .08f),
        surfaceContainer = palette.panelColor, surfaceContainerLow = palette.panelColor,
        surfaceVariant = lerp(RideColors.elevated, palette.accentColor, .06f),
        surfaceContainerHigh = lerp(RideColors.elevated, palette.accentColor, .06f),
        surfaceContainerHighest = lerp(RideColors.elevated, palette.accentColor, .1f)) }
    MaterialTheme(colorScheme = colors,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        typography = Typography, content = content)
}

@Composable
fun rememberAppearance(): Pair<Appearance, AppearanceSettings> {
    val context = LocalContext.current.applicationContext
    val settings = remember(context) { AppearanceSettings(context) }
    var appearance by remember(settings) { mutableStateOf(settings.read()) }
    DisposableEffect(settings) {
        val unsubscribe = settings.observe { appearance = settings.read() }
        // Re-read after registering, including changes made while this UI was absent.
        appearance = settings.read()
        onDispose { unsubscribe() }
    }
    return appearance to settings
}
