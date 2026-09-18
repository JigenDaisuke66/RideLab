package com.isaac.ridelab.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.isaac.ridelab.R
import com.isaac.ridelab.analytics.BodyWeight

@Composable
internal fun WeightDialog(state: FtpState, save: (Double?) -> Unit, close: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(state.weightKg?.toString() ?: "") }
    val parsed = BodyWeight.parse(text)
    val valid = text.isBlank() || parsed != null
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.body_weight)) },
        text = { Column {
            Text(stringResource(R.string.weight_help))
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = !valid,
                label = { Text(stringResource(R.string.body_weight)) })
            if (!valid) Text(stringResource(R.string.weight_invalid))
            if (state.failed) Text(stringResource(R.string.weight_error))
        } },
        confirmButton = { TextButton(enabled = state.loaded && valid, onClick = { save(parsed) }) { Text(stringResource(R.string.ftp_save)) } },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.erg_cancel)) } })
}
