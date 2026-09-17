package com.isaac.ridelab.ui.screen

import com.isaac.ridelab.ble.fec.FecProtocol
import com.isaac.ridelab.data.model.DeviceType
import com.isaac.ridelab.data.model.DiscoveredDevice
import java.util.Locale

/** Presentation filtering only. Preserve the exact scan objects for connection dispatch. */
internal fun pickerDevices(devices: List<DiscoveredDevice>, role: DeviceType, showUnknown: Boolean = false): List<DiscoveredDevice> {
    fun rank(device: DiscoveredDevice): Int? {
        val trainer = DeviceType.TRAINER in device.possibleTypes || FecProtocol.SERVICE in device.advertisedServices
        val power = DeviceType.POWER_METER in device.possibleTypes
        val hr = DeviceType.HEART_RATE_SENSOR in device.possibleTypes
        return when {
            role == DeviceType.TRAINER && trainer -> 0
            role == DeviceType.TRAINER && power -> 1 // CPS-only trainer advertisements remain selectable.
            role == DeviceType.HEART_RATE_SENSOR && hr -> 0
            role == DeviceType.POWER_METER && power -> 0
            showUnknown && !trainer && !power && !hr -> 2
            else -> null
        }
    }
    return devices.mapNotNull { device -> rank(device)?.let { it to device } }
        .sortedWith(compareBy<Pair<Int, DiscoveredDevice>> { it.first }
            .thenByDescending { it.second.rssi }
            .thenBy { it.second.name.orEmpty().lowercase(Locale.ROOT) }
            .thenBy { it.second.address })
        .map { it.second }
}
