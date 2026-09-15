package com.isaac.ridelab.ble.heartrate

import java.util.UUID

data class HeartRateMeasurement(
    val flags: Int,
    val bpm: Int,
    val contactDetected: Boolean?,
    val energyKj: Int?,
    val rrIntervals1024: List<Int>,
)

enum class HeartRatePacketError { TRUNCATED, RESERVED_FLAGS, TRAILING_BYTES }
sealed interface HeartRateParseResult {
    data class Success(val measurement: HeartRateMeasurement) : HeartRateParseResult
    data class Invalid(val reason: HeartRatePacketError) : HeartRateParseResult
}

/** Bluetooth HRS 1.0, section 3.1: unsigned BPM, optional energy and RR intervals. */
object HeartRateParser {
    val MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun parse(bytes: ByteArray): HeartRateParseResult {
        fun invalid(reason: HeartRatePacketError) = HeartRateParseResult.Invalid(reason)
        if (bytes.isEmpty()) return invalid(HeartRatePacketError.TRUNCATED)
        val flags = bytes[0].toInt() and 255
        if (flags and 0xe0 != 0) return invalid(HeartRatePacketError.RESERVED_FLAGS)
        val bpmWidth = if (flags and 1 == 0) 1 else 2
        val hasEnergy = flags and 8 != 0
        val hasRr = flags and 16 != 0
        val fixedLength = 1 + bpmWidth + if (hasEnergy) 2 else 0
        if (bytes.size < fixedLength) return invalid(HeartRatePacketError.TRUNCATED)
        val remaining = bytes.size - fixedLength
        if (hasRr && (remaining < 2 || remaining % 2 != 0)) return invalid(HeartRatePacketError.TRUNCATED)
        if (!hasRr && remaining != 0) return invalid(HeartRatePacketError.TRAILING_BYTES)
        fun uint16(offset: Int) = (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
        val bpm = if (bpmWidth == 1) bytes[1].toInt() and 255 else uint16(1)
        return HeartRateParseResult.Success(HeartRateMeasurement(flags, bpm,
            contactDetected = if (flags and 4 == 0) null else flags and 2 != 0,
            energyKj = if (hasEnergy) uint16(1 + bpmWidth) else null,
            rrIntervals1024 = if (hasRr) (fixedLength until bytes.size step 2).map(::uint16) else emptyList()))
    }
}

