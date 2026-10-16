package com.isaac.ridelab.ble.fec

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import android.os.Handler
import android.util.Log
import com.isaac.ridelab.data.model.GattServiceInfo

/** Experimental single-command controller. Called on the GATT owner's main thread.
 * The owner starts this subscription only after its measurement CCCD write completes.
 * No retries, periodic commands, or implicit target writes.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class FecOverBleTrainerController(
    private val handler: Handler,
    private val publish: (FecState) -> Unit,
    private val onTimeout: () -> Unit,
) {
    private var state = FecState()
    private var gatt: BluetoothGatt? = null
    private var notify: BluetoothGattCharacteristic? = null
    private var write: BluetoothGattCharacteristic? = null
    private var pending: BluetoothGattDescriptor? = null
    private var profile = TrainerErgProfile(50, 200)
    private val timeout = Runnable {
        failure("timeout; reconnect required")
        onTimeout() // Close session: a late callback must never complete a newer operation.
    }
    private val cooldown = Runnable { update(state.copy(status = FecStatus.SENT)) }

    private fun update(value: FecState) { state = value; publish(value) }

    fun detect(gatt: BluetoothGatt, services: List<GattServiceInfo>, profile: TrainerErgProfile) {
        reset()
        this.profile = profile
        this.gatt = gatt
        val capability = FecProtocol.detect(services)
        val service = gatt.getService(FecProtocol.SERVICE)
        notify = service?.getCharacteristic(FecProtocol.NOTIFY)
        write = service?.getCharacteristic(FecProtocol.WRITE)
        Log.i(TAG, "FE-C capability service=${capability.serviceFound} FEC2=${capability.notifyProperties?.toString(16)} CCCD=${capability.hasCccd} FEC3=${capability.writeProperties?.toString(16)} supported=${capability.supported}")
        update(FecState(capability, if (capability.supported) FecStatus.WAITING else FecStatus.UNAVAILABLE))
    }

    fun subscribe() {
        if (state.status != FecStatus.WAITING) return
        val session = gatt ?: return
        val characteristic = notify ?: return
        val descriptor = characteristic.getDescriptor(FecProtocol.CCCD) ?: return failure("FEC2 CCCD missing")
        update(state.copy(status = FecStatus.SUBSCRIBING))
        pending = descriptor
        armTimeout()
        try {
            if (!session.setCharacteristicNotification(characteristic, true)) return failure("FEC2 local notification enable rejected")
            val result = if (Build.VERSION.SDK_INT >= 33) {
                session.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (session.writeDescriptor(descriptor)) 0 else -1
            }
            Log.i(TAG, "FE-C FEC2 subscription started CCCD=0100 result=$result")
            if (result != 0) failure("FEC2 subscription dispatch failed", result)
        } catch (error: RuntimeException) { failure("FEC2 subscription exception", error = error) }
    }

    fun onDescriptorWrite(descriptor: BluetoothGattDescriptor, status: Int): Boolean {
        if (descriptor !== pending) return false
        pending = null
        handler.removeCallbacks(timeout)
        Log.i(TAG, "FE-C FEC2 subscription completed status=$status")
        if (status == BluetoothGatt.GATT_SUCCESS) update(state.copy(status = FecStatus.READY))
        else failure("FEC2 subscription failed", status)
        return true
    }

    fun sendTargetPower(requestedWatts: Int) {
        if (!state.canSend) {
            Log.w(TAG, "FE-C target rejected requestedWatts=$requestedWatts phase=${state.status}")
            return
        }
        val session = gatt ?: return
        val characteristic = write ?: return
        if (!profile.accepts(requestedWatts) || requestedWatts !in FecProtocol.MIN_ENCODABLE_WATTS..FecProtocol.MAX_ENCODABLE_WATTS) {
            failure("target outside operating range requestedWatts=$requestedWatts profile=$profile")
            return
        }
        val watts = requestedWatts
        val bytes = FecProtocol.targetPower(watts)
        // Prefer an ATT response when available. This confirms transport only, not ERG acceptance.
        val type = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        update(state.copy(status = FecStatus.WRITING, lastTargetWatts = watts, statusCode = null))
        armTimeout()
        Log.i(TAG, "FE-C target requestedWatts=$requestedWatts clampedWatts=$watts outgoing=${hex(bytes)} writeType=$type")
        try {
            val result = if (Build.VERSION.SDK_INT >= 33) session.writeCharacteristic(characteristic, bytes, type)
            else {
                characteristic.writeType = type
                characteristic.value = bytes
                if (session.writeCharacteristic(characteristic)) 0 else -1
            }
            Log.i(TAG, "FE-C FEC3 write dispatched result=$result writeType=$type")
            if (result != 0) failure("FEC3 write dispatch failed", result)
        } catch (error: RuntimeException) { failure("FEC3 write exception", error = error) }
    }

    fun onCharacteristicWrite(characteristic: BluetoothGattCharacteristic, status: Int) {
        if (characteristic !== write || state.status != FecStatus.WRITING) return
        handler.removeCallbacks(timeout)
        Log.i(TAG, "FE-C FEC3 write completed status=$status targetWatts=${state.lastTargetWatts}; transport result only")
        if (status != BluetoothGatt.GATT_SUCCESS) failure("FEC3 write failed", status)
        else {
            update(state.copy(statusCode = status))
            handler.postDelayed(cooldown, 2_000) // Gate repeated taps; no command is scheduled.
        }
    }

    fun receive(characteristic: BluetoothGattCharacteristic, bytes: ByteArray): Boolean {
        if (characteristic !== notify) return false
        Log.d(TAG, "FE-C FEC2 incoming=${hex(bytes)}")
        return true
    }

    private fun armTimeout() { handler.postDelayed(timeout, 10_000) }
    private fun failure(message: String, code: Int? = null, error: RuntimeException? = null) {
        handler.removeCallbacks(timeout)
        pending = null
        Log.e(TAG, "FE-C failure $message status=$code", error)
        update(state.copy(status = FecStatus.FAILED, statusCode = code))
    }

    fun reset() {
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(cooldown)
        gatt = null; notify = null; write = null; pending = null
        update(FecState(status = FecStatus.DISCONNECTED))
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
    private companion object { const val TAG = "RideLabGatt" }
}
