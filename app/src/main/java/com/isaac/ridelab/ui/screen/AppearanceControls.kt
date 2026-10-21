package com.isaac.ridelab.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.ui.theme.*
import com.isaac.ridelab.ui.settings.AppearanceAction

private val ThemePalette.title: Int get() = when (this) {
    ThemePalette.MINT -> R.string.theme_mint
    ThemePalette.BLUE -> R.string.theme_blue
    ThemePalette.VIOLET -> R.string.theme_violet
    ThemePalette.AMBER -> R.string.theme_amber
    ThemePalette.ROSE -> R.string.theme_rose
}
@Composable
internal fun AppearanceControls(appearance: Appearance, onAction: (AppearanceAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(selected = appearance.mode == mode,
                    onClick = { onAction(AppearanceAction.Mode(mode)) }, modifier = Modifier.weight(1f),
                    label = { Text(stringResource(when (mode) {
                        ThemeMode.LIGHT -> R.string.theme_mode_light
                        ThemeMode.DARK -> R.string.theme_mode_dark
                        ThemeMode.SYSTEM -> R.string.theme_mode_system
                    })) })
            }
        }
        Text(stringResource(R.string.appearance_theme), style = MaterialTheme.typography.labelLarge)
        PalettePicker(appearance.theme) { onAction(AppearanceAction.Palette(it)) }
        Text(stringResource(R.string.appearance_saved_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PalettePicker(selected: ThemePalette?, choose: (ThemePalette) -> Unit) {
    ThemePalette.entries.chunked(3).forEach { palettes ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            palettes.forEach { palette ->
                FilterChip(selected = selected == palette, onClick = { choose(palette) },
                    label = { Text(stringResource(palette.title)) },
                    leadingIcon = { Box(Modifier.size(12.dp).background(palette.accentColor, CircleShape)) },
                    modifier = Modifier.weight(1f))
            }
            repeat(3 - palettes.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
