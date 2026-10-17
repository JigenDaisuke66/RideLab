package com.isaac.ridelab.ui.settings

import androidx.compose.foundation.layout.*
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
import com.isaac.ridelab.ui.theme.SettingRow

@Composable
internal fun SettingsDestination(ui: SettingsUiState, actions: SettingsActions, modifier: Modifier) {
    val state = ui.profile
    val save = actions.saveFtp
    val saveWeight = actions.saveWeight
    val language = actions.language
    var editWeight by rememberSaveable { mutableStateOf(false) }
    if (editWeight) WeightDialog(state, { value -> saveWeight(value) { editWeight = false } }, { editWeight = false })
    var edit by rememberSaveable { mutableStateOf(false) }
    if (edit) SettingsScreen(state, { save(it) { edit = false } }, { edit = false })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.ftp_settings), style = MaterialTheme.typography.headlineMedium) }
        item { Text(stringResource(R.string.analytics_title), style = MaterialTheme.typography.titleMedium) }
        item {
            Column {
                SettingRow(stringResource(R.string.ftp_label), state.ftp?.let { stringResource(R.string.power_value, it) }
                    ?: stringResource(R.string.history_no_data), { edit = true })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SettingRow(stringResource(R.string.body_weight), state.weightKg?.let { stringResource(R.string.weight_value, it) }
                    ?: stringResource(R.string.history_no_data), { editWeight = true })
                if (state.failed) Text(stringResource(R.string.ftp_error), color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Text(stringResource(R.string.ui_display), style = MaterialTheme.typography.titleMedium)
            OverlayControl(ui.overlay, ui.overlayAvailable, actions.enableOverlay, actions.disableOverlay, ui.overlayPreferences, actions.saveOverlay)
            Text(stringResource(R.string.ui_overlay_opacity_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { AppearanceControls(ui.appearance, actions.appearance) }
        item {
            Text(stringResource(R.string.ui_language), style = MaterialTheme.typography.titleMedium)
            AppLanguage.entries.forEach { option ->
                val label = when (option) {
                    AppLanguage.SYSTEM -> R.string.language_system
                    AppLanguage.CHINESE -> R.string.language_chinese
                    AppLanguage.ENGLISH -> R.string.language_english
                    AppLanguage.JAPANESE -> R.string.language_japanese
                    AppLanguage.TRADITIONAL_CHINESE -> R.string.language_traditional
                    AppLanguage.SPANISH -> R.string.language_spanish
                    AppLanguage.PORTUGUESE -> R.string.language_portuguese
                    AppLanguage.FRENCH -> R.string.language_french
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(ui.language == option, role = Role.RadioButton) { language(option.tag) }
                    .padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(label), modifier = Modifier.weight(1f))
                    RadioButton(selected = ui.language == option, onClick = null)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}


