package com.isaac.ridelab.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.DiscoveredDevice
import com.isaac.ridelab.data.model.GattFailure
import com.isaac.ridelab.data.model.HeartRateState
import kotlinx.coroutines.delay

@Composable
internal fun HeartRateSummary(state: HeartRateState, connect: (DiscoveredDevice) -> Unit, disconnect: () -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.isActive) {
        while (state.isActive) { now = SystemClock.elapsedRealtime(); delay(1_000) }
    }
    Card(Modifier.fillMaxWidth().testTag("heart_rate_summary")) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.hr_source, state.device?.let { it.name ?: stringResource(R.string.unnamed_device) }
                ?: stringResource(R.string.value_unavailable)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val reading = state.heartRateBpm?.let { stringResource(R.string.hr_value, it) }
                ?: stringResource(R.string.value_unavailable)
            Text(if (state.receivedAtMs?.let { now - it >= 5_000 } == true) stringResource(R.string.stale_value, reading)
                else reading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("heart_rate_value"))
            state.device?.let { device ->
                Text(stringResource(R.string.device_address, device.address))
                Row {
                    Text(connectionStatusText(state.connection), Modifier.testTag("heart_rate_status"))
                    if (state.isActive) TextButton(onClick = disconnect) { Text(stringResource(R.string.hr_disconnect)) }
                    else TextButton(onClick = { connect(device) }) { Text(stringResource(R.string.hr_connect)) }
                }
            }
            if (state.discoveryComplete && (!state.serviceFound || !state.measurementFound)) Text(stringResource(R.string.hr_missing))
            state.issue?.let { issue ->
                Text(stringResource(when (issue.reason) {
                    GattFailure.PERMISSION_REQUIRED -> R.string.gatt_permission_error
                    GattFailure.BLUETOOTH_UNAVAILABLE -> R.string.gatt_bluetooth_error
                    GattFailure.CONNECT_FAILED -> R.string.gatt_connect_error
                    GattFailure.CONNECTION_LOST -> R.string.gatt_connection_lost
                    GattFailure.DISCOVERY_FAILED -> R.string.gatt_discovery_error
                    GattFailure.NOTIFY_UNSUPPORTED -> R.string.gatt_notify_unsupported
                    GattFailure.CCCD_MISSING -> R.string.gatt_cccd_missing
                    GattFailure.SUBSCRIPTION_FAILED -> R.string.gatt_subscription_error
                    GattFailure.TIMEOUT -> R.string.gatt_timeout
                    GattFailure.PLATFORM_EXCEPTION -> R.string.gatt_platform_error
                    GattFailure.CLEANUP_FAILED -> R.string.gatt_cleanup_error
                }), color = MaterialTheme.colorScheme.error)
                issue.statusCode?.let { Text(stringResource(R.string.gatt_status_code, it)) }
            }
            if (state.packetError != null) Text(stringResource(R.string.malformed_packet), color = MaterialTheme.colorScheme.error)
        }
    }
}
