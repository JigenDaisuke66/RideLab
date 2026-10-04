package com.isaac.ridelab.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.service.*
import kotlinx.coroutines.delay


@Composable
internal fun RideControls(
    state: RideState,
    ready: Boolean,
    start: () -> Unit,
    end: () -> Unit,
    settings: () -> Unit,
    showElapsed: Boolean = true,
    pause: () -> Unit = {}, resume: () -> Unit = {}
) {
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    if (confirmEnd && state.ongoing) {
        AlertDialog(onDismissRequest = { confirmEnd = false },
            title = { Text(stringResource(R.string.ride_end)) },
            text = { Text(stringResource(R.string.ui_end_confirm)) },
            confirmButton = { TextButton(onClick = {
                confirmEnd = false
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                end()
            }) { Text(stringResource(R.string.ride_end), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.erg_cancel)) } })
    }
    var now by remember {
        mutableLongStateOf(SystemClock.elapsedRealtime())
    }

    LaunchedEffect(
        showElapsed,
        state.startedAtMs,
        state.phase
    ) {
        if (showElapsed && state.phase == RidePhase.ACTIVE) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(1_000)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showElapsed) {
            Text(
                text = stringResource(
                    when (state.phase) {
                        RidePhase.IDLE -> R.string.ride_idle
                        RidePhase.STARTING -> R.string.ride_starting
                        RidePhase.ACTIVE -> R.string.ride_active
                        RidePhase.PAUSED -> R.string.ride_paused
                        RidePhase.STOPPING -> R.string.ride_stopping
                        RidePhase.ERROR -> R.string.ride_error
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.ongoing) {
                val seconds = state.elapsedMs(now) / 1_000
                Text(
                    text = stringResource(
                        R.string.ride_elapsed,
                        seconds / 3600,
                        seconds / 60 % 60,
                        seconds % 60
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (state.ongoing) {
            Button(onClick = { if (state.phase == RidePhase.PAUSED) resume() else pause() },
                enabled = ready, modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium) {
                Text(stringResource(if (state.phase == RidePhase.PAUSED) R.string.ride_resume else R.string.ride_pause))
            }
            if (state.phase == RidePhase.PAUSED) Text(stringResource(R.string.ride_pause_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = {
                if (state.ongoing) confirmEnd = true else {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    start()
                }
            },
            enabled = ready && state.phase !in setOf(RidePhase.STARTING, RidePhase.STOPPING),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = if (state.ongoing) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(
                text = stringResource(
                    if (state.ongoing) R.string.ride_end else R.string.ride_start
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        state.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    when (error) {
                        RideError.START_FAILED -> R.string.ride_start_failed
                        RideError.BLUETOOTH_PERMISSION -> R.string.gatt_permission_error
                        RideError.NOTIFICATIONS_DISABLED -> R.string.ride_notifications_required
                        RideError.SERVICE_LOST -> R.string.ride_service_lost
                        RideError.RECORDING_FAILED -> R.string.ride_recording_failed
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            if (error == RideError.NOTIFICATIONS_DISABLED || error == RideError.BLUETOOTH_PERMISSION) {
                TextButton(onClick = settings) {
                    Text(stringResource(R.string.open_app_settings))
                }
            }
        }
    }
}
