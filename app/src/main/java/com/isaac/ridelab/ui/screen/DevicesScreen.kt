package com.isaac.ridelab.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.ble.BleScanState
import com.isaac.ridelab.ble.ScanStatus
import com.isaac.ridelab.data.model.DeviceType
import com.isaac.ridelab.data.model.DiscoveredDevice
import com.isaac.ridelab.data.model.GattDeviceState
import com.isaac.ridelab.data.model.HeartRateState
import com.isaac.ridelab.data.model.DeviceConnectionRole
import com.isaac.ridelab.data.model.defaultConnectionRole
import kotlinx.coroutines.launch

@Composable
fun DevicesScreen(
    state: BleScanState,
    connection: GattDeviceState,
    onConnect: (DiscoveredDevice) -> Unit,
    onDisconnect: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSettings: () -> Unit,
    onBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onFecTarget: (Int) -> Unit = {},
    heartRate: HeartRateState = HeartRateState(),
    onConnectHeartRate: (DiscoveredDevice) -> Unit = {},
    onDisconnectHeartRate: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Kept outside the scrolling list: tapping a lower row must still reveal immediate status.
        connection.device?.let { selectedDevice ->
            Card(Modifier.fillMaxWidth().testTag("connection_summary")) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.role_trainer))
                    Text(stringResource(R.string.selected_device, selectedDevice.name ?: stringResource(R.string.unnamed_device)),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.device_address, selectedDevice.address))
                    Text(connectionStatusText(connection.connection), Modifier.testTag("connection_status"))
                    connection.issue?.statusCode?.let { Text(stringResource(R.string.gatt_status_code, it)) }
                    Row {
                        TextButton(onClick = { scope.launch { listState.scrollToItem(1) } }) {
                            Text(stringResource(R.string.view_connection_details))
                        }
                        if (connection.isActive) {
                            TextButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect_device)) }
                        } else {
                            TextButton(onClick = { onConnect(selectedDevice) }) { Text(stringResource(R.string.connect_device)) }
                        }
                    }
                }
            }
        }
        HeartRateSummary(heartRate, onConnectHeartRate, onDisconnectHeartRate)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("device_list"), state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "scan_header") {
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
            if (connection.device != null) {
                item(key = "connection_details") { ConnectionDetails(connection, onDisconnect, onFecTarget) }
            }
            items(state.devices, key = { it.address }) { device ->
                val heartRateRole = device.defaultConnectionRole(heartRate.device?.address) == DeviceConnectionRole.HEART_RATE
                val trainerSelected = connection.device?.address == device.address
                val heartRateSelected = heartRate.device?.address == device.address
                val isSelected = trainerSelected || heartRateSelected
                val canConnect = if (heartRateRole) !(heartRateSelected && heartRate.isActive)
                    else !(trainerSelected && connection.isActive)
                val connectDevice = { if (heartRateRole) onConnectHeartRate(device) else onConnect(device) }
                val containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                Card(
                    onClick = connectDevice,
                    enabled = canConnect,
                    modifier = Modifier.fillMaxWidth().testTag("device_card_${device.address}")
                        .semantics { selected = isSelected },
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(containerColor = containerColor,
                        disabledContainerColor = containerColor,
                        disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(device.name ?: stringResource(R.string.unnamed_device), style = MaterialTheme.typography.titleMedium)
                        if (trainerSelected) Text(stringResource(R.string.selected_device_badge))
                        if (heartRateSelected) Text(stringResource(R.string.hr_selected_badge))
                        Text(stringResource(R.string.device_address, device.address))
                        Text(stringResource(R.string.device_rssi, device.rssi))
                        Button(onClick = connectDevice, enabled = canConnect) {
                            Text(stringResource(if (heartRateRole) R.string.hr_connect else R.string.connect_device))
                        }
                        if (!heartRateRole) TextButton(onClick = { onConnectHeartRate(device) },
                            enabled = !(heartRateSelected && heartRate.isActive)) { Text(stringResource(R.string.hr_connect)) }
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
}
