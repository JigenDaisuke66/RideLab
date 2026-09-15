package com.isaac.ridelab.ble.fec

import com.isaac.ridelab.data.model.GattCharacteristicInfo
import com.isaac.ridelab.data.model.GattServiceInfo
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class FecProtocolTest {
    @Test fun targetsHaveCorrectPageScalingAndFraming() {
        // Manually derived 16-bit quarter-watt fields and XOR checksums.
        val cases = listOf(
            Triple(50, "C8 00", "E1"), Triple(100, "90 01", "B8"),
            Triple(150, "58 02", "73"), Triple(200, "20 03", "0A"),
        )
        for ((watts, power, checksum) in cases) {
            val expected = hex("A4 09 4F 05 31 FF FF FF FF FF $power $checksum")
            val packet = FecProtocol.targetPower(watts)
            assertArrayEquals("$watts W", expected, packet)
            assertEquals(13, packet.size)
            assertEquals(packet.size - 4, packet[1].toInt())
            assertEquals(watts * 4, (packet[10].toInt() and 255) or ((packet[11].toInt() and 255) shl 8))
            assertEquals(0, FecProtocol.checksum(packet).toInt())
        }
    }

    @Test fun checksumUsesXorIncludingSyncNotArithmeticSum() {
        assertEquals(0, FecProtocol.checksum(byteArrayOf()).toInt())
        assertEquals(0xa4, FecProtocol.checksum(hex("A4")).toInt() and 255)
        assertEquals(0, FecProtocol.checksum(hex("FF FF")).toInt())
        // Independent General FE example in Tacx's FE-C over BLE guide, page 5.
        assertEquals(0x30, FecProtocol.checksum(hex("A4 09 4E 05 10 19 04 00 00 00 FF 24")).toInt() and 255)
    }

    @Test fun clampsAtProtocolBoundaryIncludingExtremeInputs() {
        for (watts in listOf(Int.MIN_VALUE, -1, 0, 49))
            assertArrayEquals(FecProtocol.targetPower(50), FecProtocol.targetPower(watts))
        for (watts in listOf(201, 1000, Int.MAX_VALUE))
            assertArrayEquals(FecProtocol.targetPower(200), FecProtocol.targetPower(watts))
        assertEquals(420, FecProtocol.targetPower(105).let { (it[10].toInt() and 255) or ((it[11].toInt() and 255) shl 8) })
    }

    private fun service(notify: Int = 0x10, write: Int = 0x0c, cccd: Boolean = true) =
        GattServiceInfo(FecProtocol.SERVICE, 0, listOf(
            GattCharacteristicInfo(FecProtocol.NOTIFY, notify, if (cccd) listOf(FecProtocol.CCCD) else emptyList()),
            GattCharacteristicInfo(FecProtocol.WRITE, write, emptyList()),
        ))

    @Test fun detectsActualLayoutAndBothWriteTypes() {
        for (write in listOf(0x04, 0x08, 0x0c))
            assertTrue(FecProtocol.detect(listOf(service(write = write))).supported)
    }

    @Test fun requiresServiceCharacteristicsPropertiesAndCccd() {
        assertFalse(FecProtocol.detect(emptyList()).supported)
        val original = service()
        assertFalse(FecProtocol.detect(listOf(original.copy(uuid = UUID.randomUUID()))).supported)
        for (characteristic in original.characteristics)
            assertFalse(FecProtocol.detect(listOf(original.copy(characteristics = listOf(characteristic)))).supported)
        assertFalse(FecProtocol.detect(listOf(service(write = 0x02))).supported)
        assertFalse(FecProtocol.detect(listOf(service(notify = 0x02))).supported)
        assertFalse(FecProtocol.detect(listOf(service(cccd = false))).supported)
    }

    @Test fun sendGateRequiresCapabilityAndCompletedSubscriptionAndNoPendingWrite() {
        val capability = FecProtocol.detect(listOf(service()))
        for (status in FecStatus.entries) {
            assertEquals(status in setOf(FecStatus.READY, FecStatus.SENT), FecState(capability, status).canSend)
            assertFalse(FecState(status = status).canSend)
        }
    }

    private fun hex(value: String) = value.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
