package com.isaac.ridelab.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.isaac.ridelab.R
import com.isaac.ridelab.service.*
import kotlinx.coroutines.delay

@Composable
internal fun RideControls(state: RideState, ready: Boolean, start: () -> Unit, end: () -> Unit, settings: () -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.startedAtMs) {
        while (state.phase == RidePhase.ACTIVE) { now = SystemClock.elapsedRealtime(); delay(1_000) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val seconds = state.elapsedMs(now) / 1_000
        Text(stringResource(when (state.phase) {
            RidePhase.IDLE -> R.string.ride_idle
            RidePhase.STARTING -> R.string.ride_starting
            RidePhase.ACTIVE -> R.string.ride_active
            RidePhase.STOPPING -> R.string.ride_stopping
            RidePhase.ERROR -> R.string.ride_error
        }))
        if (state.phase == RidePhase.ACTIVE) Text(stringResource(R.string.ride_elapsed, seconds / 3600, seconds / 60 % 60, seconds % 60))
        Button(onClick = if (state.phase == RidePhase.ACTIVE) end else start,
            enabled = ready && state.phase !in setOf(RidePhase.STARTING, RidePhase.STOPPING)) {
            Text(stringResource(if (state.phase == RidePhase.ACTIVE) R.string.ride_end else R.string.ride_start))
        }
    }
    state.error?.let {
        Text(stringResource(when (it) {
            RideError.START_FAILED -> R.string.ride_start_failed
            RideError.BLUETOOTH_PERMISSION -> R.string.gatt_permission_error
            RideError.NOTIFICATIONS_DISABLED -> R.string.ride_notifications_required
            RideError.SERVICE_LOST -> R.string.ride_service_lost
            RideError.RECORDING_FAILED -> R.string.ride_recording_failed
        }))
        if (it == RideError.NOTIFICATIONS_DISABLED || it == RideError.BLUETOOTH_PERMISSION)
            TextButton(onClick = settings) { Text(stringResource(R.string.open_app_settings)) }
    }
}
