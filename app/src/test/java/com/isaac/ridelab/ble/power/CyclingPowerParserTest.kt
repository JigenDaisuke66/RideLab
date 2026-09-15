package com.isaac.ridelab.ble.power

import com.isaac.ridelab.ble.ftms.PacketError
import org.junit.Assert.*
import org.junit.Test

class CyclingPowerParserTest {
    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()
    private fun parse(bytes: ByteArray) = (CyclingPowerParser.parse(bytes) as CyclingPowerParseResult.Success).measurement

    @Test fun powerOnlyIncludesSignedAndZeroValuesWithoutInventedRevolutions() {
        val data = parse(bytes(0, 0, 6, 255))
        assertEquals(-250, data.instantaneousPowerWatts)
        assertNull(data.crank)
        assertNull(data.wheel)
        assertEquals(0, parse(bytes(0, 0, 0, 0)).instantaneousPowerWatts)
        assertEquals(-32768, parse(bytes(0, 0, 0, 128)).instantaneousPowerWatts)
        assertEquals(32767, parse(bytes(0, 0, 255, 127)).instantaneousPowerWatts)
    }

    @Test fun metadataFlagsHaveNoExtraBytes() {
        val data = parse(bytes(0x0a, 0x10, 200, 0))
        assertEquals(0x100a, data.flags)
        assertEquals(200, data.instantaneousPowerWatts)
    }

    @Test fun optionalBalanceAndTorqueDoNotShiftWheelAndCrankFields() {
        val data = parse(bytes(0x35, 0, 200, 0, 50, 0x34, 0x12,
            0xff, 0xff, 0xff, 0xff, 0x00, 0x08, 0xfe, 0xff, 0x00, 0x04))
        assertEquals(WheelRevolutionData(4294967295L, 2048), data.wheel)
        assertEquals(CrankRevolutionData(65534, 1024), data.crank)
    }

    @Test fun wheelAndCrankAreIndependentOptionalFields() {
        val wheel = parse(bytes(0x10, 0, 1, 0, 1, 0, 0, 0, 0, 8))
        assertNull(wheel.crank)
        assertEquals(WheelRevolutionData(1, 2048), wheel.wheel)
        val crank = parse(bytes(0x20, 0, 1, 0, 1, 0, 0, 4))
        assertNull(crank.wheel)
        assertEquals(CrankRevolutionData(1, 1024), crank.crank)
    }

    @Test fun validatesWidthsOfEveryOptionalFieldCombination() {
        val widths = mapOf(0 to 1, 2 to 2, 4 to 6, 5 to 4, 6 to 4, 7 to 4, 8 to 3, 9 to 2, 10 to 2, 11 to 2)
        for (flags in 0..0x1fff) {
            val count = widths.filterKeys { flags and (1 shl it) != 0 }.values.sum()
            val packet = bytes(flags and 255, flags shr 8, 200, 0) + ByteArray(count)
            assertEquals(200, parse(packet).instantaneousPowerWatts)
            assertEquals(flags, parse(packet).flags)
            for (length in 0 until packet.size) {
                assertEquals(PacketError.TRUNCATED,
                    (CyclingPowerParser.parse(packet.copyOf(length)) as CyclingPowerParseResult.Invalid).reason)
            }
        }
    }

    @Test fun rejectsTrailingBytesAndReservedFlags() {
        assertEquals(PacketError.TRAILING_BYTES,
            (CyclingPowerParser.parse(bytes(0, 0, 1, 0, 9)) as CyclingPowerParseResult.Invalid).reason)
        assertEquals(PacketError.RESERVED_FLAGS,
            (CyclingPowerParser.parse(bytes(0, 0x20, 1, 0)) as CyclingPowerParseResult.Invalid).reason)
    }
}
