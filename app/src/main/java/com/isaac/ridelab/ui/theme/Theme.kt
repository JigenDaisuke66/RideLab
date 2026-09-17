package com.isaac.ridelab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF76D6C3), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF175047), onPrimaryContainer = Color(0xFF9EF2DF),
    secondary = Color(0xFFB2CCC5), secondaryContainer = Color(0xFF304B44), onSecondaryContainer = Color(0xFFD1E8E1),
    background = Color(0xFF101514), surface = Color(0xFF141B19), surfaceVariant = Color(0xFF35423E),
    onBackground = Color(0xFFE0E5E2), onSurface = Color(0xFFE0E5E2), onSurfaceVariant = Color(0xFFBCC9C3))
private val LightColors = lightColorScheme(
    primary = Color(0xFF006B59), onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2DF), onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4C635C), secondaryContainer = Color(0xFFD0E8DE), onSecondaryContainer = Color(0xFF102D25),
    background = Color(0xFFF4F8F5), surface = Color(0xFFF4F8F5), surfaceVariant = Color(0xFFDBE5DF),
    onBackground = Color(0xFF17201C), onSurface = Color(0xFF17201C), onSurfaceVariant = Color(0xFF404943))

@Composable
fun RideLabTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(16.dp)),
        typography = Typography, content = content)
}
