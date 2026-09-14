package com.isaac.ridelab.data.model

import java.util.UUID

data class DiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val advertisedServices: Set<UUID>,
    val possibleTypes: Set<DeviceType>,
)

