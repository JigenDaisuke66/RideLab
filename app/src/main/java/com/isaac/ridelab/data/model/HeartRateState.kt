package com.isaac.ridelab.data.model

import com.isaac.ridelab.ble.heartrate.HeartRatePacketError

/** Independent source/session state. Trainer state and power never live in this object. */
data class HeartRateState(
    val device: DiscoveredDevice? = null,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val issue: GattIssue? = null,
    val discoveryComplete: Boolean = false,
    val serviceFound: Boolean = false,
    val measurementFound: Boolean = false,
    val heartRateBpm: Int? = null,
    val receivedAtMs: Long? = null,
    val packetError: HeartRatePacketError? = null,
) {
    val isActive get() = connection !in setOf(ConnectionState.DISCONNECTED, ConnectionState.FAILED)
}
