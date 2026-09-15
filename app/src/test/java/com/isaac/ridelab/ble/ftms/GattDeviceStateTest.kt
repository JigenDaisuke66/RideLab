package com.isaac.ridelab.ble.ftms

import com.isaac.ridelab.ble.BleUuids
import com.isaac.ridelab.data.model.*
import org.junit.Assert.*
import org.junit.Test

class GattDeviceStateTest {
    @Test fun cyclingPowerMeasurementIsDetectedOnlyInsideCyclingPowerService() {
        val characteristic = GattCharacteristicInfo(
            com.isaac.ridelab.ble.power.CyclingPowerParser.MEASUREMENT_UUID, 0x10, emptyList())
        assertTrue(GattDeviceState(services = listOf(
            GattServiceInfo(BleUuids.CYCLING_POWER, 1, listOf(characteristic)),
        )).hasCyclingPowerMeasurement)
        assertFalse(GattDeviceState(services = listOf(
            GattServiceInfo(BleUuids.FITNESS_MACHINE, 1, listOf(characteristic)),
        )).hasCyclingPowerMeasurement)
    }
    @Test fun splitNotificationsPreserveValuesAndIndividualAges() {
        val metrics = TrainerMetrics()
            .update(IndoorBikeData(0, 25.0, null, null), 100)
            .update(IndoorBikeData(0x45, null, 90.5, 210), 200)
        assertEquals(25.0, metrics.speedKph!!.value, 0.0)
        assertEquals(100L, metrics.speedKph.receivedAtMs)
        assertEquals(200L, metrics.cadenceRpm!!.receivedAtMs)
        assertTrue(metrics.speedKph.isStale(5_100))
        assertFalse(metrics.cadenceRpm.isStale(5_100))
        assertEquals(210, metrics.trainerPowerWatts!!.value)
    }

    @Test fun zeroUpdatesReplacePreviousReadingsAndRefreshTheirAge() {
        val metrics = TrainerMetrics().update(IndoorBikeData(0x44, 20.0, 90.0, 200), 1)
            .update(IndoorBikeData(0x44, 0.0, 0.0, 0), 10)
        assertEquals(0, metrics.trainerPowerWatts!!.value)
        assertEquals(0.0, metrics.speedKph!!.value, 0.0)
        assertEquals(0.0, metrics.cadenceRpm!!.value, 0.0)
        assertEquals(10L, metrics.trainerPowerWatts.receivedAtMs)
    }

    @Test fun capabilitiesComeFromGattEvenWhenAdvertisementsOmitFtms() {
        val selected = DiscoveredDevice("Think X211-0013186", "00:11:22:33:44:55", -50,
            setOf(BleUuids.CYCLING_POWER), setOf(DeviceType.POWER_METER))
        val state = GattDeviceState(device = selected, discoveryComplete = true, services = listOf(
            GattServiceInfo(BleUuids.FITNESS_MACHINE, 1, listOf(
                GattCharacteristicInfo(FtmsConstants.INDOOR_BIKE_DATA, 0x10, emptyList()),
            )), GattServiceInfo(BleUuids.CYCLING_POWER, 2, emptyList()),
        ))
        assertTrue(state.hasFtms)
        assertTrue(state.hasCyclingPower)
        assertTrue(state.hasIndoorBikeData)
    }

    @Test fun indoorBikeDataMustBelongToFtmsAndCyclingPowerDoesNotImplyFtms() {
        val state = GattDeviceState(discoveryComplete = true, services = listOf(
            GattServiceInfo(BleUuids.CYCLING_POWER, 1, listOf(
                GattCharacteristicInfo(FtmsConstants.INDOOR_BIKE_DATA, 0x10, emptyList()),
            )),
        ))
        assertTrue(state.hasCyclingPower)
        assertFalse(state.hasFtms)
        assertFalse(state.hasIndoorBikeData)
    }
}
