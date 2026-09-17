package com.isaac.ridelab.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.ble.BleScanState
import com.isaac.ridelab.ble.ScanStatus
import com.isaac.ridelab.data.model.*

internal fun ConnectionState.readyForRide() = this in setOf(ConnectionState.CONNECTED, ConnectionState.SUBSCRIBING, ConnectionState.STREAMING)
internal fun friendlyConnection(state: ConnectionState) = when (state) {
    ConnectionState.DISCONNECTED -> R.string.ui_disconnected
    ConnectionState.FAILED -> R.string.ui_connection_failed
    ConnectionState.CONNECTING, ConnectionState.DISCOVERING -> R.string.ui_connecting
    else -> R.string.ui_connected
}

@Composable
internal fun DeviceStatusCard(title: Int, name: String?, state: ConnectionState, select: () -> Unit, disconnect: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name ?: stringResource(R.string.ui_no_device), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(friendlyConnection(state)))
            Row {
                TextButton(onClick = select) { Text(stringResource(R.string.ui_choose_device)) }
                if (state !in setOf(ConnectionState.DISCONNECTED, ConnectionState.FAILED))
                    TextButton(onClick = disconnect) { Text(stringResource(R.string.disconnect_device)) }
            }
        }
    }
}

/** Role-specific chooser reuses scanner results and exact device objects, without diagnostics. */
@Composable
internal fun DevicePicker(role: Int, scan: BleScanState, selected: String?, active: Boolean,
    connect: (DiscoveredDevice) -> Unit, start: () -> Unit, stop: () -> Unit,
    appSettings: () -> Unit, bluetoothSettings: () -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(role)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(when (scan.status) {
                ScanStatus.PERMISSION_REQUIRED -> R.string.scan_permission_required
                ScanStatus.BLUETOOTH_OFF -> R.string.scan_bluetooth_off
                ScanStatus.UNSUPPORTED -> R.string.scan_unsupported
                ScanStatus.FAILED -> R.string.ui_scan_failed
                ScanStatus.SCANNING -> R.string.ui_searching
                else -> R.string.ui_choose_hint
            }))
            if (scan.status == ScanStatus.PERMISSION_REQUIRED) TextButton(onClick = appSettings) { Text(stringResource(R.string.open_app_settings)) }
            if (scan.status == ScanStatus.BLUETOOTH_OFF) TextButton(onClick = bluetoothSettings) { Text(stringResource(R.string.open_bluetooth_settings)) }
            if (scan.devices.isEmpty()) Text(stringResource(R.string.ui_no_devices))
            LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scan.devices, key = { it.address }) { device ->
                    OutlinedButton(onClick = { connect(device); close() }, enabled = !(active && selected == device.address), modifier = Modifier.fillMaxWidth()) {
                        Text(device.name ?: stringResource(R.string.unnamed_device))
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = if (scan.status == ScanStatus.SCANNING) stop else start,
            enabled = scan.status !in setOf(ScanStatus.UNSUPPORTED, ScanStatus.BLUETOOTH_OFF)) {
            Text(stringResource(if (scan.status == ScanStatus.SCANNING) R.string.stop_scan else R.string.ui_search_devices))
        }
    }, dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.history_back)) } })
}
