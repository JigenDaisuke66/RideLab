package com.isaac.ridelab.ble.ftms

import com.isaac.ridelab.ble.BleUuids
import com.isaac.ridelab.ble.fec.TrainerErgProfile
import com.isaac.ridelab.data.model.GattServiceInfo
import java.util.UUID

/** Bluetooth FTMS 1.0.1 sections 4.3, 4.15–4.17. See TRAINER_COMPATIBILITY.md. */
object FtmsControlProtocol {
    private fun uuid(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
    val FEATURE = uuid("2acc")
    val POWER_RANGE = uuid("2ad8")
    val CONTROL_POINT = uuid("2ad9")
    val STATUS = uuid("2ada")
    const val REQUEST_CONTROL = 0x00
    const val SET_TARGET_POWER = 0x05
    const val RESPONSE = 0x80
    const val SUCCESS = 0x01
    const val TIMEOUT_MS = 30_000L

    fun supportsErg(services: List<GattServiceInfo>, feature: ByteArray?): Boolean {
        val chars = services.firstOrNull { it.uuid == BleUuids.FITNESS_MACHINE }?.characteristics ?: return false
        fun has(uuid: UUID, properties: Int, cccd: Boolean = false) = chars.any {
            it.uuid == uuid && it.properties and properties == properties &&
                (!cccd || FtmsConstants.CLIENT_CHARACTERISTIC_CONFIGURATION in it.descriptorUuids)
        }
        return feature?.size == 8 && feature[4].toInt() and 0x08 != 0 &&
            has(FEATURE, 0x02) && has(FtmsConstants.INDOOR_BIKE_DATA, 0x10, true) &&
            has(CONTROL_POINT, 0x28, true) && has(STATUS, 0x10, true)
    }

    fun powerRange(bytes: ByteArray?): TrainerErgProfile? {
        if (bytes == null || bytes.size != 6) return null
        fun u16(offset: Int) = (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
        val min = u16(0).toShort().toInt()
        val max = u16(2).toShort().toInt()
        val step = u16(4)
        if (min > max || max < 0 || step == 0) return null
        // Only nonnegative ERG targets, retaining the server's increment alignment.
        val first = if (min >= 0) min else min + ((-min + step - 1) / step) * step
        if (first > max) return null
        return TrainerErgProfile(first, max, step)
    }

    fun targetPower(watts: Int): ByteArray {
        require(watts in 0..Short.MAX_VALUE.toInt())
        return byteArrayOf(SET_TARGET_POWER.toByte(), watts.toByte(), (watts ushr 8).toByte())
    }
    data class Response(val request: Int, val result: Int)
    fun response(bytes: ByteArray): Response? = if (bytes.size == 3 && bytes[0].toInt() and 255 == RESPONSE &&
        bytes[2].toInt() in 1..5) Response(bytes[1].toInt() and 255, bytes[2].toInt()) else null
}
