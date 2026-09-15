package com.isaac.ridelab.data.model

import com.isaac.ridelab.ble.BleUuids

enum class DeviceConnectionRole { TRAINER, HEART_RATE }

/** Only a default for card taps; an explicit heart-rate action is also available. */
fun DiscoveredDevice.defaultConnectionRole(selectedHeartRateAddress: String? = null): DeviceConnectionRole =
    if (address == selectedHeartRateAddress || BleUuids.HEART_RATE in advertisedServices || DeviceType.HEART_RATE_SENSOR in possibleTypes)
        DeviceConnectionRole.HEART_RATE else DeviceConnectionRole.TRAINER
