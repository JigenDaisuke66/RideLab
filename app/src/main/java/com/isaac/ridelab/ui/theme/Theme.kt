package com.isaac.ridelab.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.isaac.ridelab.data.repository.AppearanceSettings
import com.isaac.ridelab.data.model.Appearance
import com.isaac.ridelab.data.model.ThemePalette
import com.isaac.ridelab.data.model.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalView
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
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

fun rideColorScheme(palette: ThemePalette, dark: Boolean): ColorScheme {
    if (!dark) {
        val primary = Color(when (palette) {
            ThemePalette.MINT -> 0xFF006B5C
            ThemePalette.BLUE -> 0xFF175B9A
            ThemePalette.VIOLET -> 0xFF6943A4
            ThemePalette.AMBER -> 0xFF855000
            ThemePalette.ROSE -> 0xFF9F3155
        })
        val background = lerp(Color.White, palette.accentColor, .16f)
        val surface = lerp(Color.White, palette.accentColor, .06f)
        val container = lerp(Color.White, palette.accentColor, .27f)
        return lightColorScheme(primary = primary, onPrimary = Color.White,
            primaryContainer = container, onPrimaryContainer = primary,
            secondary = primary, onSecondary = Color.White,
            secondaryContainer = container, onSecondaryContainer = Color(0xFF192C30),
            background = background, onBackground = Color(0xFF192C30),
            surface = surface, onSurface = Color(0xFF192C30), onSurfaceVariant = Color(0xFF4A5C60),
            surfaceVariant = container, surfaceContainer = surface,
            surfaceContainerLowest = Color.White, surfaceContainerLow = surface,
            surfaceContainerHigh = lerp(Color.White, palette.accentColor, .13f),
            surfaceContainerHighest = container,
            outline = Color(0xFF7B8C8E), outlineVariant = lerp(Color(0xFFD0DCDD), primary, .08f))
    }
    return DarkColors.copy(
        primary = palette.accentColor, onPrimary = RideColors.background,
        primaryContainer = lerp(RideColors.surface, palette.accentColor, .25f),
        onPrimaryContainer = palette.accentColor,
        secondary = palette.accentColor,
        background = lerp(RideColors.background, palette.accentColor, .10f),
        surface = palette.panelColor,
        secondaryContainer = lerp(RideColors.elevated, palette.accentColor, .08f),
        surfaceContainer = palette.panelColor, surfaceContainerLow = palette.panelColor,
        surfaceVariant = lerp(RideColors.elevated, palette.accentColor, .06f),
        surfaceContainerHigh = lerp(RideColors.elevated, palette.accentColor, .06f),
        surfaceContainerHighest = lerp(RideColors.elevated, palette.accentColor, .1f))
}

@Composable
fun RideLabTheme(content: @Composable () -> Unit) {
    val (appearance, _) = rememberAppearance()
    val dark = when (appearance.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = remember(appearance.theme, dark) { rideColorScheme(appearance.theme, dark) }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        var host = view.context
        while (host is ContextWrapper && host !is ComponentActivity) host = host.baseContext
        (host as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
    }
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
