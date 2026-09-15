package com.isaac.ridelab.ble.heartrate

import org.junit.Assert.*
import org.junit.Test

class HeartRateParserTest {
    @Test fun unsignedEightBitBpmAndAbsentFields() {
        for (bpm in listOf(0, 72, 134, 255)) {
            val data = success(0, bpm)
            assertEquals(bpm, data.bpm)
            assertEquals(0, data.flags)
            assertNull(data.energyKj)
            assertNull(data.contactDetected)
            assertTrue(data.rrIntervals1024.isEmpty())
        }
    }

    @Test fun unsignedSixteenBitLittleEndianBpm() {
        assertEquals(300, success(1, 0x2c, 1).bpm)
        assertEquals(65535, success(1, 255, 255).bpm)
        assertEquals(0, success(1, 0, 0).bpm)
        // Width may change on any notification.
        assertEquals(80, success(0, 80).bpm)
        assertEquals(80, success(1, 80, 0).bpm)
    }

    @Test fun energyAndMultipleRrIntervalsRespectBpmWidth() {
        for (wide in listOf(false, true)) {
            val bytes = if (wide) intArrayOf(0x19, 0x2c, 1, 0x34, 0x12, 0, 4, 0xff, 3)
                else intArrayOf(0x18, 134, 0x34, 0x12, 0, 4, 0xff, 3)
            val data = success(*bytes)
            assertEquals(if (wide) 300 else 134, data.bpm)
            assertEquals(0x1234, data.energyKj)
            assertEquals(listOf(1024, 1023), data.rrIntervals1024)
        }
        assertEquals(65535, success(8, 72, 255, 255).energyKj)
        assertEquals(listOf(65535), success(16, 72, 255, 255).rrIntervals1024)
    }

    @Test fun contactFlagsDoNotAddPayloadBytes() {
        assertNull(success(2, 80).contactDetected) // Contact detection not supported.
        assertEquals(false, success(4, 80).contactDetected)
        assertEquals(true, success(6, 80).contactDetected)
        assertEquals(300, success(7, 0x2c, 1).bpm)
    }

    @Test fun truncatedMandatoryAndOptionalFieldsAreRejected() {
        val packets = listOf(intArrayOf(), intArrayOf(0), intArrayOf(1), intArrayOf(1, 72),
            intArrayOf(8, 72), intArrayOf(8, 72, 1), intArrayOf(16, 72), intArrayOf(16, 72, 0),
            intArrayOf(16, 72, 0, 4, 0), intArrayOf(0x19, 0x2c, 1, 0, 0))
        for (packet in packets) assertEquals(HeartRateParseResult.Invalid(HeartRatePacketError.TRUNCATED), parse(*packet))
    }

    @Test fun unflaggedTrailingDataAndReservedFlagsAreRejected() {
        assertEquals(HeartRateParseResult.Invalid(HeartRatePacketError.TRAILING_BYTES), parse(0, 72, 0))
        for (flag in listOf(0x20, 0x40, 0x80))
            assertEquals(HeartRateParseResult.Invalid(HeartRatePacketError.RESERVED_FLAGS), parse(flag, 72))
    }

    private fun parse(vararg bytes: Int) = HeartRateParser.parse(bytes.map(Int::toByte).toByteArray())
    private fun success(vararg bytes: Int) = (parse(*bytes) as HeartRateParseResult.Success).measurement
}
