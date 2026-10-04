package com.isaac.ridelab.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.ui.theme.*

private val ThemePalette.title: Int get() = when (this) {
    ThemePalette.MINT -> R.string.theme_mint
    ThemePalette.BLUE -> R.string.theme_blue
    ThemePalette.VIOLET -> R.string.theme_violet
    ThemePalette.AMBER -> R.string.theme_amber
    ThemePalette.ROSE -> R.string.theme_rose
}
private val OverlayStyle.title: Int get() = when (this) {
    OverlayStyle.PANEL -> R.string.overlay_style_panel
    OverlayStyle.STRIP -> R.string.overlay_style_strip
    OverlayStyle.DASHBOARD -> R.string.overlay_style_dashboard
}

@Composable
internal fun AppearanceControls() {
    val (appearance, settings) = rememberAppearance()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.appearance_theme), style = MaterialTheme.typography.labelLarge)
        PalettePicker(appearance.theme) { settings.save(appearance.copy(theme = it)) }
        HorizontalDivider()
        Text(stringResource(R.string.appearance_overlay_palette), style = MaterialTheme.typography.labelLarge)
        FilterChip(selected = appearance.overlayPalette == null,
            onClick = { settings.save(appearance.copy(overlayPalette = null)) },
            label = { Text(stringResource(R.string.appearance_follow_theme)) })
        PalettePicker(appearance.overlayPalette) { settings.save(appearance.copy(overlayPalette = it)) }
        Text(stringResource(R.string.appearance_overlay_style), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OverlayStyle.entries.forEach { style ->
                FilterChip(selected = appearance.overlayStyle == style,
                    onClick = { settings.save(appearance.copy(overlayStyle = style)) },
                    label = { Text(stringResource(style.title)) }, modifier = Modifier.weight(1f))
            }
        }
        Text(stringResource(R.string.appearance_preview), style = MaterialTheme.typography.labelSmall)
        OverlayAppearancePreview(appearance)
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

@Composable
private fun OverlayAppearancePreview(appearance: Appearance) {
    val palette = appearance.effectiveOverlayPalette
    Card(colors = CardDefaults.cardColors(containerColor = palette.panelColor),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.overlay_title), color = palette.accentColor,
                style = MaterialTheme.typography.labelLarge)
            when (appearance.overlayStyle) {
                OverlayStyle.PANEL -> listOf(R.string.ui_power, R.string.ui_heart_rate, R.string.cadence).forEach {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(it), color = RideColors.secondaryText)
                        Text(stringResource(R.string.history_no_data), color = if (it == R.string.ui_power) palette.accentColor else RideColors.text)
                    }
                }
                OverlayStyle.STRIP -> Row(Modifier.fillMaxWidth()) {
                    listOf(R.string.ui_power, R.string.ui_heart_rate, R.string.cadence).forEach {
                        PreviewMetric(it, palette, Modifier.weight(1f))
                    }
                }
                OverlayStyle.DASHBOARD -> {
                    PreviewMetric(R.string.ui_power, palette, Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth()) {
                        PreviewMetric(R.string.ui_heart_rate, palette, Modifier.weight(1f))
                        PreviewMetric(R.string.cadence, palette, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewMetric(title: Int, palette: ThemePalette, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title), color = RideColors.secondaryText, style = MaterialTheme.typography.labelMedium)
        Text(stringResource(R.string.history_no_data), color = if (title == R.string.ui_power) palette.accentColor else RideColors.text,
            style = MaterialTheme.typography.titleMedium)
    }
}
