package com.isaac.ridelab.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.isaac.ridelab.R

@Composable
fun SettingsScreen(state: FtpState, onSave: (Int?) -> Unit, onClose: () -> Unit) {
    var text by rememberSaveable(state.loaded, state.ftp) { mutableStateOf(state.ftp?.toString() ?: "") }
    val value = text.toIntOrNull()
    val valid = text.isBlank() || value != null && value in 50..500
    AlertDialog(onDismissRequest = onClose,
        title = { Text(stringResource(R.string.ftp_settings)) },
        text = { Column {
            Text(stringResource(R.string.ftp_help))
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text(stringResource(R.string.ftp_label)) }, isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (!valid) Text(stringResource(R.string.ftp_invalid))
            if (state.failed) Text(stringResource(R.string.ftp_error))
        } },
        confirmButton = { TextButton(enabled = state.loaded && valid, onClick = { onSave(value) }) { Text(stringResource(R.string.ftp_save)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.history_back)) } })
}
