package com.isaac.ridelab.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    MaterialTheme(colorScheme = DarkColors,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        typography = Typography, content = content)
}
