package com.isaac.ridelab.ble.power

import com.isaac.ridelab.ble.ftms.PacketError
import java.util.UUID

data class CrankRevolutionData(val revolutions: Int, val lastEventTime1024: Int)
data class WheelRevolutionData(val revolutions: Long, val lastEventTime2048: Int)
data class CyclingPowerMeasurement(
    val flags: Int,
    val instantaneousPowerWatts: Int,
    val crank: CrankRevolutionData?,
    val wheel: WheelRevolutionData?,
)

sealed interface CyclingPowerParseResult {
    data class Success(val measurement: CyclingPowerMeasurement) : CyclingPowerParseResult
    data class Invalid(val reason: PacketError, val offset: Int) : CyclingPowerParseResult
}

/** Bluetooth SIG Cycling Power Measurement (0x2A63), little-endian fields. */
object CyclingPowerParser {
    val MEASUREMENT_UUID: UUID = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")

    fun parse(bytes: ByteArray): CyclingPowerParseResult {
        var offset = 0
        fun unsigned(size: Int): Long {
            if (offset + size > bytes.size) throw TruncatedPacket()
            var value = 0L
            repeat(size) { value = value or ((bytes[offset++].toLong() and 255) shl (it * 8)) }
            return value
        }
        fun skip(size: Int) { unsigned(size) }
        return try {
            val flags = unsigned(2).toInt()
            if (flags and 0xe000 != 0) return CyclingPowerParseResult.Invalid(PacketError.RESERVED_FLAGS, 0)
            fun present(bit: Int) = flags and (1 shl bit) != 0
            val power = unsigned(2).toShort().toInt()
            if (present(0)) skip(1) // Pedal power balance
            if (present(2)) skip(2) // Accumulated torque
            val wheel = if (present(4)) WheelRevolutionData(unsigned(4), unsigned(2).toInt()) else null
            val crank = if (present(5)) CrankRevolutionData(unsigned(2).toInt(), unsigned(2).toInt()) else null
            if (present(6)) skip(4) // Extreme force magnitudes
            if (present(7)) skip(4) // Extreme torque magnitudes
            if (present(8)) skip(3) // Packed extreme angles
            if (present(9)) skip(2) // Top dead spot angle
            if (present(10)) skip(2) // Bottom dead spot angle
            if (present(11)) skip(2) // Accumulated energy
            // Bits 1, 3 and 12 are metadata, not extra payload fields.
            if (offset != bytes.size) CyclingPowerParseResult.Invalid(PacketError.TRAILING_BYTES, offset)
            else CyclingPowerParseResult.Success(CyclingPowerMeasurement(flags, power, crank, wheel))
        } catch (_: TruncatedPacket) {
            CyclingPowerParseResult.Invalid(PacketError.TRUNCATED, offset)
        }
    }

    private class TruncatedPacket : RuntimeException()
}

