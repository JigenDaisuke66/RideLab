package com.isaac.ridelab.ble.ftms

import org.junit.Assert.*
import org.junit.Test

class FtmsParserTest {
    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()
    private fun parsed(packet: ByteArray) = (FtmsParser.parseIndoorBikeData(packet) as IndoorBikeParseResult.Success).data

    @Test fun speedIsMandatoryWhenMoreDataIsClear() {
        val data = parsed(bytes(0, 0, 0xd2, 4))
        assertEquals(12.34, data.speedKph!!, 0.00001)
        assertNull(data.cadenceRpm)
        assertNull(data.instantaneousPowerWatts)
    }

    @Test fun moreDataOmitsSpeedButAllowsCadenceAndSignedPower() {
        val data = parsed(bytes(0x45, 0, 0xb5, 0, 0x06, 0xff))
        assertNull(data.speedKph)
        assertEquals(90.5, data.cadenceRpm!!, 0.00001)
        assertEquals(-250, data.instantaneousPowerWatts)
    }

    @Test fun zeroReadingsAreValidMeasurements() {
        val data = parsed(bytes(0x44, 0, 0, 0, 0, 0, 0, 0))
        assertEquals(0.0, data.speedKph!!, 0.0)
        assertEquals(0.0, data.cadenceRpm!!, 0.0)
        assertEquals(0, data.instantaneousPowerWatts)
    }

    @Test fun unsignedSpeedAndCadenceAndSignedPowerBoundaries() {
        val data = parsed(bytes(0x44, 0, 0xff, 0xff, 0xff, 0xff, 0, 0x80))
        assertEquals(655.35, data.speedKph!!, 0.00001)
        assertEquals(32767.5, data.cadenceRpm!!, 0.0)
        assertEquals(-32768, data.instantaneousPowerWatts)
        assertEquals(32767, parsed(bytes(0x41, 0, 0xff, 0x7f)).instantaneousPowerWatts)
    }

    private val allFields = bytes(
        0xfe, 0x1f, // All known optional fields; speed present
        0xd2, 4, // 12.34 km/h
        0x10, 0, // Average speed
        0xb5, 0, // 90.5 rpm
        0x20, 0, // Average cadence
        0x11, 0x22, 0x33, // uint24 distance
        0xff, 0xff, // Resistance
        0x06, 0xff, // -250 W
        0x30, 0, // Average power
        0x40, 0, 0x50, 0, 0x60, // Energy (5 bytes)
        0x70, 0x80, // Heart rate and MET
        0x90, 0, 0xa0, 0, // Elapsed and remaining time
    )

    @Test fun skipsAllOptionalFieldsAtTheirSpecifiedWidths() {
        val data = parsed(allFields)
        assertEquals(12.34, data.speedKph!!, 0.00001)
        assertEquals(90.5, data.cadenceRpm!!, 0.00001)
        assertEquals(-250, data.instantaneousPowerWatts)
    }

    @Test fun rejectsEveryTruncatedPrefixIncludingUnusedTailFields() {
        for (length in 0 until allFields.size) {
            val result = FtmsParser.parseIndoorBikeData(allFields.copyOf(length))
            assertTrue("length=$length", result is IndoorBikeParseResult.Invalid)
            assertEquals(PacketError.TRUNCATED, (result as IndoorBikeParseResult.Invalid).reason)
        }
    }

    @Test fun handlesEveryCombinationOfKnownFlags() {
        val fields = listOf(
            bytes(0xd2, 4), bytes(0x10, 0), bytes(0xb5, 0), bytes(0x20, 0),
            bytes(0x11, 0x22, 0x33), bytes(0xff, 0xff), bytes(0x06, 0xff),
            bytes(0x30, 0), bytes(0x40, 0, 0x50, 0, 0x60), bytes(0x70),
            bytes(0x80), bytes(0x90, 0), bytes(0xa0, 0),
        )
        for (flags in 0..0x1fff) {
            var packet = bytes(flags and 0xff, flags shr 8)
            fields.forEachIndexed { bit, field ->
                if ((bit == 0 && flags and 1 == 0) || (bit > 0 && flags and (1 shl bit) != 0)) packet += field
            }
            val data = parsed(packet)
            assertEquals(flags, data.flags)
            assertEquals(if (flags and 1 == 0) 12.34 else null, data.speedKph)
            assertEquals(if (flags and 4 != 0) 90.5 else null, data.cadenceRpm)
            assertEquals(if (flags and 64 != 0) -250 else null, data.instantaneousPowerWatts)
        }
    }

    @Test fun rejectsReservedFlagsAndUnexpectedTrailingBytes() {
        for (bit in 13..15) {
            val result = FtmsParser.parseIndoorBikeData(bytes(1, 1 shl (bit - 8))) as IndoorBikeParseResult.Invalid
            assertEquals(PacketError.RESERVED_FLAGS, result.reason)
        }
        val result = FtmsParser.parseIndoorBikeData(bytes(1, 0, 0)) as IndoorBikeParseResult.Invalid
        assertEquals(PacketError.TRAILING_BYTES, result.reason)
    }
}
