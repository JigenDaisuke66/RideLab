package com.isaac.ridelab.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.ble.BleScanState
import com.isaac.ridelab.ble.ScanStatus
import com.isaac.ridelab.data.model.DeviceType

@Composable
fun DevicesScreen(
    state: BleScanState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSettings: () -> Unit,
    onBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.devices_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.scan_description))
            Text(stringResource(when (state.status) {
                ScanStatus.IDLE -> R.string.scan_idle
                ScanStatus.SCANNING -> R.string.scan_scanning
                ScanStatus.FINISHED -> R.string.scan_finished
                ScanStatus.PERMISSION_REQUIRED -> R.string.scan_permission_required
                ScanStatus.BLUETOOTH_OFF -> R.string.scan_bluetooth_off
                ScanStatus.UNSUPPORTED -> R.string.scan_unsupported
                ScanStatus.FAILED -> R.string.scan_failed
            }))
            state.errorCode?.let { Text(stringResource(R.string.scan_error_code, it)) }
            Button(
                onClick = if (state.status == ScanStatus.SCANNING) onStop else onStart,
                enabled = state.status != ScanStatus.UNSUPPORTED && state.status != ScanStatus.BLUETOOTH_OFF,
            ) {
                Text(stringResource(if (state.status == ScanStatus.SCANNING) R.string.stop_scan else R.string.start_scan))
            }
            if (state.status == ScanStatus.PERMISSION_REQUIRED) {
                TextButton(onClick = onSettings) { Text(stringResource(R.string.open_app_settings)) }
            }
            if (state.status == ScanStatus.BLUETOOTH_OFF) {
                TextButton(onClick = onBluetoothSettings) { Text(stringResource(R.string.open_bluetooth_settings)) }
            }
            if (state.devices.isEmpty()) Text(stringResource(R.string.scan_empty))
        }
        items(state.devices, key = { it.address }) { device ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(device.name ?: stringResource(R.string.unnamed_device), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.device_address, device.address))
                    Text(stringResource(R.string.device_rssi, device.rssi))
                    if (device.possibleTypes.isEmpty()) Text(stringResource(R.string.device_type_unknown))
                    device.possibleTypes.forEach { type ->
                        Text(stringResource(when (type) {
                            DeviceType.TRAINER -> R.string.possible_trainer
                            DeviceType.HEART_RATE_SENSOR -> R.string.possible_heart_rate
                            DeviceType.POWER_METER -> R.string.possible_power_meter
                        }))
                    }
                    Text(stringResource(R.string.advertised_services))
                    if (device.advertisedServices.isEmpty()) Text(stringResource(R.string.services_unavailable))
                    device.advertisedServices.forEach { Text(it.toString()) }
                }
            }
        }
    }
}
