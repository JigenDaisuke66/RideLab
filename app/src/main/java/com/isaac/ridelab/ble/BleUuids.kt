package com.isaac.ridelab.ble

import com.isaac.ridelab.data.model.DeviceType
import java.util.UUID

object BleUuids {
    val FITNESS_MACHINE: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    val HEART_RATE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val CYCLING_POWER: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")

    fun classify(services: Set<UUID>): Set<DeviceType> = buildSet {
        if (FITNESS_MACHINE in services) add(DeviceType.TRAINER)
        if (HEART_RATE in services) add(DeviceType.HEART_RATE_SENSOR)
        if (CYCLING_POWER in services) add(DeviceType.POWER_METER)
    }
}

