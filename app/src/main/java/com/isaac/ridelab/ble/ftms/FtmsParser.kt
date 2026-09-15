package com.isaac.ridelab.ble.ftms

data class IndoorBikeData(
    val flags: Int,
    val speedKph: Double?,
    val cadenceRpm: Double?,
    val instantaneousPowerWatts: Int?,
)

enum class PacketError { TRUNCATED, RESERVED_FLAGS, TRAILING_BYTES }

sealed interface IndoorBikeParseResult {
    data class Success(val data: IndoorBikeData) : IndoorBikeParseResult
    data class Invalid(val reason: PacketError, val offset: Int) : IndoorBikeParseResult
}

/** Bluetooth SIG GATT Specification Supplement, Indoor Bike Data (0x2AD2).
 * Little endian. Bit 0 is inverted: speed is present only when More Data is zero.
 * Validate even unused optional fields so a truncated packet never partially updates live data.
 */
object FtmsParser {
    fun parseIndoorBikeData(bytes: ByteArray): IndoorBikeParseResult {
        var offset = 0
        fun u16(): Int {
            if (offset + 2 > bytes.size) throw TruncatedPacket()
            return ((bytes[offset++].toInt() and 0xff) or
                ((bytes[offset++].toInt() and 0xff) shl 8))
        }
        fun skip(length: Int) {
            if (offset + length > bytes.size) throw TruncatedPacket()
            offset += length
        }
        return try {
            val flags = u16()
            if (flags and 0xe000 != 0) return IndoorBikeParseResult.Invalid(PacketError.RESERVED_FLAGS, 0)
            fun present(bit: Int) = flags and (1 shl bit) != 0
            val speed = if (!present(0)) u16() / 100.0 else null
            if (present(1)) skip(2) // Average speed
            val cadence = if (present(2)) u16() / 2.0 else null
            if (present(3)) skip(2) // Average cadence
            if (present(4)) skip(3) // Total distance, uint24
            if (present(5)) skip(2) // Resistance level: observation only, no control
            val power = if (present(6)) u16().toShort().toInt() else null
            if (present(7)) skip(2) // Average power
            if (present(8)) skip(5) // Total energy, energy/hour, energy/minute
            if (present(9)) skip(1) // Heart rate
            if (present(10)) skip(1) // Metabolic equivalent
            if (present(11)) skip(2) // Elapsed time
            if (present(12)) skip(2) // Remaining time
            if (offset != bytes.size) IndoorBikeParseResult.Invalid(PacketError.TRAILING_BYTES, offset)
            else IndoorBikeParseResult.Success(IndoorBikeData(flags, speed, cadence, power))
        } catch (_: TruncatedPacket) {
            IndoorBikeParseResult.Invalid(PacketError.TRUNCATED, offset)
        }
    }

    private class TruncatedPacket : RuntimeException()
}

