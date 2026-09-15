package com.isaac.ridelab.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.isaac.ridelab.R
import com.isaac.ridelab.ble.fec.FecProtocol
import com.isaac.ridelab.ble.fec.FecState
import com.isaac.ridelab.ble.fec.FecStatus

@Composable
internal fun FecDebugControl(state: FecState, active: Boolean, send: (Int) -> Unit) {
    var watts by remember(active) { mutableIntStateOf(100) }
    Text(stringResource(if (state.capability.supported) R.string.fec_detected else R.string.fec_unavailable))
    if (!state.capability.supported) return
    Text(stringResource(R.string.fec_experiment_note))
    Text(stringResource(when (state.status) {
        FecStatus.UNAVAILABLE -> R.string.fec_unavailable
        FecStatus.WAITING, FecStatus.SUBSCRIBING -> R.string.fec_subscribing
        FecStatus.READY -> R.string.fec_ready
        FecStatus.WRITING -> R.string.fec_writing
        FecStatus.SENT -> R.string.fec_sent
        FecStatus.FAILED -> R.string.fec_failed
        FecStatus.DISCONNECTED -> R.string.gatt_disconnected
    }))
    state.statusCode?.let { Text(stringResource(R.string.gatt_status_code, it)) }
    state.lastTargetWatts?.let { Text(stringResource(R.string.fec_last_target, it)) }
    Row {
        TextButton(onClick = { watts -= 5 }, enabled = active && state.canSend && watts > FecProtocol.MIN_WATTS) {
            Text(stringResource(R.string.fec_minus))
        }
        Text(stringResource(R.string.fec_target, watts))
        TextButton(onClick = { watts += 5 }, enabled = active && state.canSend && watts < FecProtocol.MAX_WATTS) {
            Text(stringResource(R.string.fec_plus))
        }
    }
    Button(onClick = { send(watts) }, enabled = active && state.canSend) {
        Text(stringResource(R.string.fec_send, watts))
    }
}
