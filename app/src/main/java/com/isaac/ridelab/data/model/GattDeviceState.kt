package com.isaac.ridelab.data.model

import com.isaac.ridelab.ble.BleUuids
import com.isaac.ridelab.ble.ftms.FtmsConstants
import com.isaac.ridelab.ble.ftms.IndoorBikeData
import com.isaac.ridelab.ble.ftms.PacketError
import java.util.UUID

data class GattCharacteristicInfo(val uuid: UUID, val properties: Int, val descriptorUuids: List<UUID>)
data class GattServiceInfo(val uuid: UUID, val instanceId: Int, val characteristics: List<GattCharacteristicInfo>)

enum class GattFailure {
    PERMISSION_REQUIRED, BLUETOOTH_UNAVAILABLE, CONNECT_FAILED, CONNECTION_LOST,
    DISCOVERY_FAILED, NOTIFY_UNSUPPORTED, CCCD_MISSING, SUBSCRIPTION_FAILED,
    TIMEOUT, PLATFORM_EXCEPTION, CLEANUP_FAILED,
}

data class GattIssue(val reason: GattFailure, val statusCode: Int? = null)

data class TimedMetric<T>(val value: T, val receivedAtMs: Long) {
    fun isStale(nowMs: Long): Boolean = nowMs - receivedAtMs >= 5_000
}

data class TrainerMetrics(
    val speedKph: TimedMetric<Double>? = null,
    val cadenceRpm: TimedMetric<Double>? = null,
    val trainerPowerWatts: TimedMetric<Int>? = null,
) {
    // FTMS can distribute fields across notifications. Preserve absent fields with their own age.
    fun update(data: IndoorBikeData, nowMs: Long) = copy(
        speedKph = data.speedKph?.let { TimedMetric(it, nowMs) } ?: speedKph,
        cadenceRpm = data.cadenceRpm?.let { TimedMetric(it, nowMs) } ?: cadenceRpm,
        trainerPowerWatts = data.instantaneousPowerWatts?.let { TimedMetric(it, nowMs) } ?: trainerPowerWatts,
    )
}

data class GattDeviceState(
    val device: DiscoveredDevice? = null,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val services: List<GattServiceInfo> = emptyList(),
    val discoveryComplete: Boolean = false,
    val issue: GattIssue? = null,
    val metrics: TrainerMetrics = TrainerMetrics(),
    val notificationCount: Int = 0,
    val malformedPacketCount: Int = 0,
    val lastPacketError: PacketError? = null,
) {
    val hasFtms: Boolean get() = services.any { it.uuid == BleUuids.FITNESS_MACHINE }
    val hasCyclingPower: Boolean get() = services.any { it.uuid == BleUuids.CYCLING_POWER }
    val hasIndoorBikeData: Boolean get() = services.any { service ->
        service.uuid == BleUuids.FITNESS_MACHINE && service.characteristics.any { it.uuid == FtmsConstants.INDOOR_BIKE_DATA }
    }
    val isActive: Boolean get() = connection !in setOf(ConnectionState.DISCONNECTED, ConnectionState.FAILED)
}
