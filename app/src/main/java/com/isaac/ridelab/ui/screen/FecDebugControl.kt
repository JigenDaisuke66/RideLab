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
import com.isaac.ridelab.ble.fec.TrainerErgProfile
import com.isaac.ridelab.ble.fec.FecState
import com.isaac.ridelab.ble.fec.FecStatus

@Composable
internal fun FecDebugControl(state: FecState, active: Boolean, profile: TrainerErgProfile?, send: (Int) -> Unit) {
    var watts by remember(active) { mutableIntStateOf(100) }
    Text(stringResource(if (state.capability.supported) R.string.fec_detected else R.string.fec_unavailable))
    if (!state.capability.supported || profile == null) return
    Text(stringResource(R.string.fec_experiment_note, profile.minErgWatts, profile.maxErgWatts))
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
        TextButton(onClick = { watts = adjustedErgTarget(watts, -5, profile) }, enabled = active && state.canSend && watts > profile.minErgWatts) {
            Text(stringResource(R.string.fec_minus))
        }
        Text(stringResource(R.string.fec_target, watts))
        TextButton(onClick = { watts = adjustedErgTarget(watts, 5, profile) }, enabled = active && state.canSend && watts < profile.maxErgWatts) {
            Text(stringResource(R.string.fec_plus))
        }
    }
    Button(onClick = { send(watts) }, enabled = active && state.canSend && profile.accepts(watts)) {
        Text(stringResource(R.string.fec_send, watts))
    }
}
