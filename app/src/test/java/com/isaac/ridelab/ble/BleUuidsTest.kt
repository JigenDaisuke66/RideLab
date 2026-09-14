package com.isaac.ridelab.ble

import com.isaac.ridelab.data.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class BleUuidsTest {
    @Test
    fun classifiesStandardCyclingServicesWithoutChoosingOneRole() {
        assertEquals(
            setOf(DeviceType.TRAINER, DeviceType.POWER_METER, DeviceType.HEART_RATE_SENSOR),
            BleUuids.classify(setOf(
                UUID.fromString("00001826-0000-1000-8000-00805f9b34fb"),
                UUID.fromString("00001818-0000-1000-8000-00805f9b34fb"),
                UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"),
            )),
        )
    }

    @Test
    fun absentOrUnrelatedServicesRemainUnknown() {
        assertEquals(emptySet<DeviceType>(), BleUuids.classify(emptySet()))
        assertEquals(emptySet<DeviceType>(), BleUuids.classify(setOf(
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"),
        )))
    }
}
