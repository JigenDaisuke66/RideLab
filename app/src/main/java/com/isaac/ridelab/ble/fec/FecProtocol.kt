package com.isaac.ridelab.ble.fec

import com.isaac.ridelab.data.model.GattServiceInfo
import java.util.UUID

/** ANT FE-C page 49 transported as one complete ANT serial frame over BLE.
 * See FEC_EXPERIMENT.md for sources and the checksum discrepancy in references.
 */
object FecProtocol {
    val SERVICE: UUID = UUID.fromString("6e40fec1-b5a3-f393-e0a9-e50e24dcca9e")
    val NOTIFY: UUID = UUID.fromString("6e40fec2-b5a3-f393-e0a9-e50e24dcca9e")
    val WRITE: UUID = UUID.fromString("6e40fec3-b5a3-f393-e0a9-e50e24dcca9e")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val MIN_WATTS = 50
    const val MAX_WATTS = 200

    fun clamp(watts: Int) = watts.coerceIn(MIN_WATTS, MAX_WATTS)

    fun targetPower(watts: Int): ByteArray {
        val quarters = clamp(watts) * 4 // Page 49 uses unsigned 0.25 W units, little endian.
        val frame = ByteArray(13)
        frame[0] = 0xa4.toByte() // ANT sync
        frame[1] = 9 // Channel + eight-byte data page
        frame[2] = 0x4f // Acknowledged data message
        frame[3] = 5 // FE-C over BLE channel (ignored by trainer)
        frame[4] = 0x31 // Target power page
        for (i in 5..9) frame[i] = 0xff.toByte() // Reserved page bytes 1..5
        frame[10] = quarters.toByte()
        frame[11] = (quarters ushr 8).toByte()
        frame[12] = checksum(frame.copyOf(12))
        return frame
    }

    /** XOR of every supplied byte, including sync when supplied a frame prefix. */
    fun checksum(bytes: ByteArray): Byte = bytes.fold(0) { sum, byte -> sum xor (byte.toInt() and 0xff) }.toByte()

    fun detect(services: List<GattServiceInfo>): FecCapability {
        val service = services.firstOrNull { it.uuid == SERVICE } ?: return FecCapability()
        val notify = service.characteristics.firstOrNull { it.uuid == NOTIFY }
        val write = service.characteristics.firstOrNull { it.uuid == WRITE }
        return FecCapability(true, notify?.properties, write?.properties,
            notify?.descriptorUuids?.contains(CCCD) == true)
    }
}

data class FecCapability(
    val serviceFound: Boolean = false,
    val notifyProperties: Int? = null,
    val writeProperties: Int? = null,
    val hasCccd: Boolean = false,
) {
    val canNotify get() = (notifyProperties ?: 0) and 0x10 != 0 && hasCccd
    val canWrite get() = (writeProperties ?: 0) and 0x0c != 0
    val supported get() = serviceFound && canNotify && canWrite
}

enum class FecStatus { UNAVAILABLE, WAITING, SUBSCRIBING, READY, WRITING, SENT, FAILED, DISCONNECTED }
data class FecState(
    val capability: FecCapability = FecCapability(),
    val status: FecStatus = FecStatus.UNAVAILABLE,
    val lastTargetWatts: Int? = null,
    val statusCode: Int? = null,
) {
    val canSend get() = capability.supported && status in setOf(FecStatus.READY, FecStatus.SENT)
}
