package com.isaac.ridelab.ble.heartrate

import com.isaac.ridelab.ble.BleUuids
import com.isaac.ridelab.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class DeviceConnectionRoleTest {
    @Test fun advertisedHeartRateRoutesToIndependentHeartRateAction() {
        assertEquals(DeviceConnectionRole.HEART_RATE, device(setOf(BleUuids.HEART_RATE)).defaultConnectionRole())
        assertEquals(DeviceConnectionRole.HEART_RATE,
            device(setOf(BleUuids.HEART_RATE, BleUuids.CYCLING_POWER)).defaultConnectionRole())
    }

    @Test fun verifiedTrainerServicesKeepOriginalTrainerAction() {
        assertEquals(DeviceConnectionRole.TRAINER, device(setOf(BleUuids.CYCLING_POWER,
            UUID.fromString("6e40fec1-b5a3-f393-e0a9-e50e24dcca9e"))).defaultConnectionRole())
        assertEquals(DeviceConnectionRole.TRAINER, device(setOf(BleUuids.FITNESS_MACHINE)).defaultConnectionRole())
        assertEquals(DeviceConnectionRole.TRAINER, device(emptySet()).defaultConnectionRole())
    }

    @Test fun existingHeartRateClassificationIsRespected() {
        assertEquals(DeviceConnectionRole.HEART_RATE,
            device(emptySet()).copy(possibleTypes = setOf(DeviceType.HEART_RATE_SENSOR)).defaultConnectionRole())
    }

    @Test fun manuallySelectedSensorKeepsHeartRateRoleWhenAdvertisementOmitsService() {
        val sensor = device(emptySet())
        assertEquals(DeviceConnectionRole.HEART_RATE, sensor.defaultConnectionRole(sensor.address))
        assertEquals(DeviceConnectionRole.TRAINER, sensor.defaultConnectionRole("AA:BB:CC:DD:EE:FF"))
    }

    private fun device(services: Set<UUID>) = DiscoveredDevice(null, "00:11:22:33:44:55", -50,
        services, BleUuids.classify(services))
}
