package com.isaac.ridelab.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.ConnectionState
import com.isaac.ridelab.data.model.GattDeviceState
import com.isaac.ridelab.data.model.GattFailure
import com.isaac.ridelab.data.model.MeasurementSource
import com.isaac.ridelab.data.model.TimedMetric
import kotlinx.coroutines.delay

@Composable
fun ConnectionDetails(state: GattDeviceState, onDisconnect: () -> Unit, onFecTarget: (Int) -> Unit = {}) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.isActive) {
        while (state.isActive) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.selected_device, state.device?.name ?: stringResource(R.string.unnamed_device)),
                style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.device_address, state.device?.address.orEmpty()))
            Text(connectionStatusText(state.connection))
            Text(stringResource(R.string.foreground_connection_note))
            if (state.isActive) TextButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect_device)) }
            if (state.discoveryComplete) {
                TrainerControlSummary(state.control)
                FecDebugControl(state.fec, state.isActive, state.capabilities.powerRange, onFecTarget)
            }
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
            if (state.discoveryComplete) {
                Text(stringResource(R.string.discovery_snapshot))
                Text(stringResource(R.string.ftms_detection, detected(state.hasFtms)))
                Text(stringResource(R.string.cycling_power_detection, detected(state.hasCyclingPower)))
                Text(stringResource(R.string.indoor_bike_detection, detected(state.hasIndoorBikeData)))
                Text(stringResource(R.string.cycling_power_measurement_detection, detected(state.hasCyclingPowerMeasurement)))
                if (!state.hasFtms) Text(stringResource(R.string.ftms_absent))
                else if (!state.hasIndoorBikeData) Text(stringResource(R.string.indoor_bike_absent))
            }
            if (state.measurementSource != null && state.isActive) {
                Text(stringResource(R.string.live_trainer_data), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.live_value_note))
                val isCyclingPower = state.measurementSource == MeasurementSource.CYCLING_POWER
                val cps = state.cyclingPower
                Text(stringResource(if (isCyclingPower) R.string.source_cycling_power else R.string.source_ftms))
                val speed = if (isCyclingPower) null else state.metrics.speedKph
                val cadence = if (isCyclingPower) cps?.cadenceRpm else state.metrics.cadenceRpm
                val power = if (isCyclingPower) cps?.let { TimedMetric(it.measurement.instantaneousPowerWatts, it.receivedAtMs) }
                    else state.metrics.trainerPowerWatts
                MetricLine(stringResource(R.string.speed), speed?.let { stringResource(R.string.speed_value, it.value) }, speed?.isStale(now) == true)
                MetricLine(stringResource(R.string.cadence), cadence?.let { stringResource(R.string.cadence_value, it.value) }, cadence?.isStale(now) == true)
                MetricLine(stringResource(R.string.trainer_power), power?.let { stringResource(R.string.power_value, it.value) }, power?.isStale(now) == true)
                if (isCyclingPower) {
                    Text(stringResource(R.string.cycling_power_cadence_note))
                    val crank = cps?.measurement?.crank
                    val wheel = cps?.measurement?.wheel
                    Text(if (crank == null) stringResource(R.string.crank_unavailable)
                        else stringResource(R.string.crank_raw, crank.revolutions, crank.lastEventTime1024))
                    Text(if (wheel == null) stringResource(R.string.wheel_unavailable)
                        else stringResource(R.string.wheel_raw, wheel.revolutions, wheel.lastEventTime2048))
                }
                Text(stringResource(R.string.packet_counts, state.notificationCount, state.malformedPacketCount))
                if (state.notificationCount == 0) Text(stringResource(R.string.waiting_for_data))
                if (state.lastPacketError != null) Text(stringResource(R.string.malformed_packet), color = MaterialTheme.colorScheme.error)
            }
            if (state.discoveryComplete) {
                Text(stringResource(R.string.discovered_gatt_services), style = MaterialTheme.typography.titleMedium)
                if (state.services.isEmpty()) Text(stringResource(R.string.gatt_no_services))
                state.services.forEach { service ->
                    Text(stringResource(R.string.gatt_service, service.uuid.toString(), service.instanceId))
                    service.characteristics.forEach { characteristic ->
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(R.string.gatt_characteristic, characteristic.uuid.toString()))
                            Text(stringResource(R.string.gatt_properties, characteristic.properties.toString(16), propertyNames(characteristic.properties)))
                            characteristic.descriptorUuids.forEach { Text(stringResource(R.string.gatt_descriptor, it.toString())) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun connectionStatusText(state: ConnectionState): String = stringResource(when (state) {
    ConnectionState.DISCONNECTED -> R.string.gatt_disconnected
    ConnectionState.CONNECTING -> R.string.gatt_connecting
    ConnectionState.DISCOVERING -> R.string.gatt_discovering
    ConnectionState.CONNECTED -> R.string.gatt_connected
    ConnectionState.SUBSCRIBING -> R.string.gatt_subscribing
    ConnectionState.STREAMING -> R.string.gatt_streaming
    ConnectionState.FAILED -> R.string.gatt_failed
})

@Composable
private fun detected(found: Boolean) = stringResource(if (found) R.string.service_found else R.string.service_not_found)

@Composable
private fun MetricLine(label: String, value: String?, stale: Boolean) {
    val reading = value ?: stringResource(R.string.value_unavailable)
    Text(stringResource(R.string.metric_line, label,
        if (stale) stringResource(R.string.stale_value, reading) else reading))
}

@Composable
private fun propertyNames(bits: Int): String {
    val labels = listOf(
        0x01 to R.string.property_broadcast, 0x02 to R.string.property_read,
        0x04 to R.string.property_write_no_response, 0x08 to R.string.property_write,
        0x10 to R.string.property_notify, 0x20 to R.string.property_indicate,
        0x40 to R.string.property_signed_write, 0x80 to R.string.property_extended,
    ).filter { bits and it.first != 0 }.map { stringResource(it.second) }
    return if (labels.isEmpty()) stringResource(R.string.property_none)
    else labels.joinToString(stringResource(R.string.list_separator))
}
