package com.isaac.ridelab.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.ai.*
import com.isaac.ridelab.ui.theme.RideLabCard
import com.isaac.ridelab.ui.theme.SettingRow

@Composable
internal fun AiSettingsRoute(model: AiSettingsViewModel) {
    val ui by model.state.collectAsState()
    AiSettingsCard(ui, model::edit, model::save, model::test, model::forget)
}

/** Presentation only. Draft secrets stay in ViewModel memory, never rememberSaveable/Bundle. */
@Composable
internal fun AiSettingsCard(ui: AiSettingsUiState, edit: (AiSettingsAction) -> Unit,
    save: () -> Unit, test: (String) -> Unit, forget: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    val prompt = stringResource(R.string.ai_probe_prompt)
    RideLabCard(Modifier.fillMaxWidth()) {
        SettingRow(stringResource(R.string.ai_title), stringResource(providerTitle(ui.configuration.provider)), { open = true })
        Text(stringResource(R.string.ai_scope), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) AlertDialog(onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.ai_title)) },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val enabled = !ui.busy && !ui.storageFailed
            AiProviderPicker(ui.configuration.provider, enabled) { edit(AiSettingsAction.Provider(it)) }
            OutlinedTextField(ui.configuration.baseUrl, { edit(AiSettingsAction.Endpoint(it)) },
                label = { Text(stringResource(R.string.ai_endpoint)) }, enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.ai_endpoint_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(ui.configuration.model, { edit(AiSettingsAction.Model(it)) },
                label = { Text(stringResource(R.string.ai_model)) }, enabled = enabled,
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.ai_model_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(ui.configuration.apiKey, { edit(AiSettingsAction.Key(it)) },
                label = { Text(stringResource(if (ui.configuration.provider.requiresKey) R.string.ai_key else R.string.ai_key_optional)) },
                enabled = enabled, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            if (ui.configuration.provider.permitsLocalHttp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ai_local_http), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(ui.configuration.allowLocalHttp, { edit(AiSettingsAction.LocalHttp(it)) }, enabled = enabled)
                }
                Text(stringResource(R.string.ai_local_hint), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.ai_privacy), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(if (ui.saved) R.string.ai_saved else R.string.ai_unsaved),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (!ui.busy && !ui.storageFailed) ui.configuration.problem()?.let {
                Text(stringResource(problemTitle(it)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ui.busy) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(when (ui.operation) {
                    AiSettingsOperation.TESTING -> R.string.ai_testing
                    AiSettingsOperation.SAVING -> R.string.ai_saving
                    else -> R.string.history_loading
                }))
            }
            ui.result?.let { result ->
                Text(stringResource(problemTitle(result.problem)),
                    color = if (result.problem == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                if (result.problem != null) result.httpStatus?.let {
                    Text(stringResource(R.string.ai_http_status, it), style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = { test(prompt) }, enabled = enabled && ui.configuration.problem() == null,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ai_test)) }
            TextButton(onClick = { confirmForget = true }, enabled = !ui.busy) {
                Text(stringResource(if (ui.storageFailed) R.string.ai_reset else R.string.ai_forget),
                    color = MaterialTheme.colorScheme.error)
            }
        } },
        confirmButton = { TextButton(onClick = save,
            enabled = !ui.busy && !ui.storageFailed && ui.configuration.problem() == null) {
            Text(stringResource(R.string.ai_save))
        } },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.ai_close)) } })
    if (confirmForget) AlertDialog(onDismissRequest = { confirmForget = false },
        title = { Text(stringResource(if (ui.storageFailed) R.string.ai_reset else R.string.ai_forget)) },
        text = { Text(stringResource(if (ui.storageFailed) R.string.ai_reset_hint else R.string.ai_forget_hint)) },
        confirmButton = { TextButton(onClick = { confirmForget = false; forget() }) { Text(stringResource(R.string.erg_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmForget = false }) { Text(stringResource(R.string.erg_cancel)) } })
}

@Composable
private fun AiProviderPicker(provider: AiProvider, enabled: Boolean, select: (AiProvider) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(providerTitle(provider)))
        }
        DropdownMenu(expanded && enabled, { expanded = false }, modifier = Modifier.heightIn(max = 280.dp)) {
            AiProvider.entries.forEach { item ->
                DropdownMenuItem(text = { Text(stringResource(providerTitle(item))) },
                    onClick = { expanded = false; select(item) })
            }
        }
    }
}

internal fun providerTitle(provider: AiProvider): Int = when (provider) {
    AiProvider.OPENAI -> R.string.ai_provider_openai
    AiProvider.ANTHROPIC -> R.string.ai_provider_anthropic
    AiProvider.GEMINI -> R.string.ai_provider_gemini
    AiProvider.DEEPSEEK -> R.string.ai_provider_deepseek
    AiProvider.QWEN -> R.string.ai_provider_qwen
    AiProvider.KIMI -> R.string.ai_provider_kimi
    AiProvider.GLM -> R.string.ai_provider_glm
    AiProvider.SILICONFLOW -> R.string.ai_provider_siliconflow
    AiProvider.OPENROUTER -> R.string.ai_provider_openrouter
    AiProvider.OLLAMA -> R.string.ai_provider_ollama
    AiProvider.LM_STUDIO -> R.string.ai_provider_lmstudio
    AiProvider.CUSTOM -> R.string.ai_provider_custom
}

internal fun problemTitle(problem: AiProblem?): Int = when (problem) {
    null -> R.string.ai_test_success
    AiProblem.URL -> R.string.ai_error_url
    AiProblem.LOCAL_HTTP -> R.string.ai_error_local
    AiProblem.MODEL -> R.string.ai_error_model
    AiProblem.KEY -> R.string.ai_error_key
    AiProblem.STORAGE -> R.string.ai_error_storage
    AiProblem.NETWORK -> R.string.ai_error_network
    AiProblem.TIMEOUT -> R.string.ai_error_timeout
    AiProblem.AUTH -> R.string.ai_error_auth
    AiProblem.RATE_LIMIT -> R.string.ai_error_rate
    AiProblem.HTTP -> R.string.ai_error_http
    AiProblem.RESPONSE -> R.string.ai_error_response
}
