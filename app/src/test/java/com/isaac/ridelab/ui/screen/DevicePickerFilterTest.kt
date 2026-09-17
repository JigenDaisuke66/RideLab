package com.isaac.ridelab.ui.screen

import com.isaac.ridelab.ble.fec.FecProtocol
import com.isaac.ridelab.data.model.*
import org.junit.Assert.*
import org.junit.Test

class DevicePickerFilterTest {
    private fun device(id: String, vararg types: DeviceType, rssi: Int = -50) =
        DiscoveredDevice(id, id, rssi, emptySet(), types.toSet())
    @Test fun trainerPrioritizesTrainerAndFecBeforeCpsFallback() {
        val power = device("power", DeviceType.POWER_METER, rssi = -20)
        val trainer = device("trainer", DeviceType.TRAINER, rssi = -70)
        val fec = device("fec").copy(advertisedServices = setOf(FecProtocol.SERVICE))
        assertEquals(listOf(fec, trainer, power), pickerDevices(listOf(power, device("hr", DeviceType.HEART_RATE_SENSOR), trainer, fec, device("unknown")), DeviceType.TRAINER))
    }
    @Test fun heartRateAndPowerRolesExcludeOtherKnownRoles() {
        val hr = device("hr", DeviceType.HEART_RATE_SENSOR)
        val power = device("power", DeviceType.POWER_METER)
        val all = listOf(device("trainer", DeviceType.TRAINER), power, hr, device("unknown"))
        assertEquals(listOf(hr), pickerDevices(all, DeviceType.HEART_RATE_SENSOR))
        assertEquals(listOf(power), pickerDevices(all, DeviceType.POWER_METER))
    }
    @Test fun unknownIsOptInAndAlwaysLast() {
        val unknown = device("unknown", rssi = -10)
        val hr = device("hr", DeviceType.HEART_RATE_SENSOR, rssi = -90)
        val all = listOf(unknown, device("power", DeviceType.POWER_METER), hr)
        assertEquals(listOf(hr), pickerDevices(all, DeviceType.HEART_RATE_SENSOR))
        assertEquals(listOf(hr, unknown), pickerDevices(all, DeviceType.HEART_RATE_SENSOR, true))
    }
    @Test fun orderingIsDeterministicAndObjectsArePreserved() {
        val a = device("a", DeviceType.HEART_RATE_SENSOR)
        val b = device("b", DeviceType.HEART_RATE_SENSOR)
        val strong = device("strong", DeviceType.HEART_RATE_SENSOR, rssi = -20)
        val all = listOf(b, strong, a)
        val result = pickerDevices(all, DeviceType.HEART_RATE_SENSOR)
        assertEquals(listOf(strong, a, b), result)
        assertEquals(result, pickerDevices(all.reversed(), DeviceType.HEART_RATE_SENSOR))
        assertSame(strong, result.first())
    }
}
