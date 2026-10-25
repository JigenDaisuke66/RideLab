package com.isaac.ridelab.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.animateColorAsState
import com.isaac.ridelab.ui.theme.RideLabCard
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.locale.AppLocale
import com.isaac.ridelab.locale.AppLanguage
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import com.isaac.ridelab.ui.screen.*
import com.isaac.ridelab.ui.shared.OverlayControl
import com.isaac.ridelab.data.model.ThemeMode
import com.isaac.ridelab.data.model.ThemePalette
import com.isaac.ridelab.ui.theme.SettingRow

@Composable
internal fun SettingsDestination(ui: SettingsUiState, actions: SettingsActions, modifier: Modifier,
    extras: @Composable () -> Unit = {}) {
    val state = ui.profile
    val save = actions.saveFtp
    val saveWeight = actions.saveWeight
    val language = actions.language
    var editLanguage by rememberSaveable { mutableStateOf(false) }
    var editAppearance by rememberSaveable { mutableStateOf(false) }
    if (editLanguage) AlertDialog(onDismissRequest = { editLanguage = false },
        title = { Text(stringResource(R.string.ui_language)) },
        text = { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            AppLanguage.entries.forEach { option ->
                LanguageOption(languageTitle(option), ui.language == option) {
                    language(option.tag); editLanguage = false
                }
            }
        } },
        confirmButton = { TextButton(onClick = { editLanguage = false }) { Text(stringResource(R.string.erg_confirm)) } })
    if (editAppearance) AlertDialog(onDismissRequest = { editAppearance = false },
        title = { Text(stringResource(R.string.appearance_title)) },
        text = { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            AppearanceControls(ui.appearance, actions.appearance)
        } },
        confirmButton = { TextButton(onClick = { editAppearance = false }) { Text(stringResource(R.string.erg_confirm)) } })

    var editWeight by rememberSaveable { mutableStateOf(false) }
    if (editWeight) WeightDialog(state, { value -> saveWeight(value) { editWeight = false } }, { editWeight = false })
    var edit by rememberSaveable { mutableStateOf(false) }
    if (edit) SettingsScreen(state, { save(it) { edit = false } }, { edit = false })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { Text(stringResource(R.string.ftp_settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            SettingsSection(R.string.ui_profile) {
                SettingRow(stringResource(R.string.ftp_label), state.ftp?.let { stringResource(R.string.power_value, it) }
                    ?: stringResource(R.string.history_no_data), { edit = true })
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                SettingRow(stringResource(R.string.body_weight), state.weightKg?.let { stringResource(R.string.weight_value, it) }
                    ?: stringResource(R.string.history_no_data), { editWeight = true })
                if (state.failed) Text(stringResource(R.string.ftp_error), color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            SettingsSection(R.string.ui_display) {
                OverlayControl(ui.overlay, ui.overlayAvailable, actions.enableOverlay, actions.disableOverlay, ui.overlayPreferences, actions.saveOverlay)
                Text(stringResource(R.string.ui_overlay_opacity_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsSection(R.string.appearance_title) {
                SettingRow(stringResource(R.string.appearance_theme),
                    stringResource(when (ui.appearance.theme) {
                        ThemePalette.MINT -> R.string.theme_mint
                        ThemePalette.BLUE -> R.string.theme_blue
                        ThemePalette.VIOLET -> R.string.theme_violet
                        ThemePalette.AMBER -> R.string.theme_amber
                        ThemePalette.ROSE -> R.string.theme_rose
                    }), { editAppearance = true })
                Text(stringResource(when (ui.appearance.mode) {
                    ThemeMode.LIGHT -> R.string.theme_mode_light
                    ThemeMode.DARK -> R.string.theme_mode_dark
                    ThemeMode.SYSTEM -> R.string.theme_mode_system
                }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            RideLabCard(Modifier.fillMaxWidth()) {
                SettingRow(stringResource(R.string.ui_language), stringResource(languageTitle(ui.language)),
                    { editLanguage = true })
            }
        }
        item {
            extras()
        }
        item {
            SettingsSection(R.string.ui_data_management) {
                Text(stringResource(R.string.ui_data_location), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun languageTitle(language: AppLanguage): Int = when (language) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.CHINESE -> R.string.language_chinese
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.JAPANESE -> R.string.language_japanese
    AppLanguage.TRADITIONAL_CHINESE -> R.string.language_traditional
    AppLanguage.SPANISH -> R.string.language_spanish
    AppLanguage.PORTUGUESE -> R.string.language_portuguese
    AppLanguage.FRENCH -> R.string.language_french
}

@Composable
private fun SettingsSection(title: Int, content: @Composable ColumnScope.() -> Unit) {
    RideLabCard(Modifier.fillMaxWidth()) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun LanguageOption(label: Int, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .08f)
        else MaterialTheme.colorScheme.surfaceContainerLow, label = "LanguageSelection")
    Surface(shape = RoundedCornerShape(12.dp), color = color) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(label), Modifier.weight(1f),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            RadioButton(selected, onClick = null)
        }
    }
}
