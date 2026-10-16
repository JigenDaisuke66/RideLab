package com.isaac.ridelab.ble.ftms

import android.annotation.SuppressLint
import android.bluetooth.*
import android.os.Build
import android.os.Handler
import android.util.Log
import com.isaac.ridelab.ble.BleUuids
import com.isaac.ridelab.ble.control.*
import com.isaac.ridelab.data.model.GattServiceInfo
import java.util.UUID

/** Borrows the owner's GATT, never connects/closes it. Started only AFTER measurement CCCD.
 * Setup reads/subscriptions and control writes share one serialized ATT queue. FE-C may start
 * only after this adapter's setup completion, and only the selected protocol receives commands.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class FtmsGattControl(
    private val gatt: BluetoothGatt,
    private val handler: Handler,
    private val publish: (FtmsControlState) -> Unit,
    private val capabilities: (TrainerCapabilities) -> Unit,
    private val finished: () -> Unit,
    private val transportTimeout: () -> Unit,
) {
    private val service = gatt.getService(BleUuids.FITNESS_MACHINE)
    private val attTimeout = Runnable { queue.timeout() }
    private val procedureTimeout = Runnable { controller.timeout() }
    private val queue: SerialGattOperations = SerialGattOperations(
        { handler.postDelayed(attTimeout, 10_000) }, { handler.removeCallbacks(attTimeout) }, {
            Log.e(TAG, "FTMS ATT timeout; closing ambiguous GATT session")
            controller.failure(ControlIssue.TIMEOUT); transportTimeout()
        })
    private val controller: FtmsControlSession = FtmsControlSession(::writeControl,
        { handler.postDelayed(procedureTimeout, FtmsControlProtocol.TIMEOUT_MS) },
        { handler.removeCallbacks(procedureTimeout) }, {
            Log.i(TAG, "FTMS control status=${it.status} authorized=${it.authorized} requested=${it.requestedWatts} accepted=${it.acceptedWatts} issue=${it.issue} result=${it.resultCode}")
            publish(it)
        })
    private var feature: ByteArray? = null
    private var range: ByteArray? = null
    private var closed = false
    private data class Key(val kind: String, val uuid: UUID)

    fun start(name: String?, services: List<GattServiceInfo>, requiredProtocol: ControlProtocol?) {
        read(FtmsControlProtocol.FEATURE) { feature = it
            read(FtmsControlProtocol.POWER_RANGE) rangeRead@{ range = it
                val candidate = TrainerControlPolicy.select(name, services, feature, range)
                val selected = TrainerControlPolicy.preserveProtocol(candidate, requiredProtocol)
                if (selected != candidate) Log.w(TAG, "Protocol change blocked previous=$requiredProtocol discovered=${candidate.protocol}")
                Log.i(TAG, "Trainer control selected=$selected feature=${hex(feature)} powerRange=${hex(range)}")
                capabilities(selected)
                if (selected.protocol != ControlProtocol.FTMS) { finished(); return@rangeRead }
                subscribe(FtmsControlProtocol.CONTROL_POINT, indications = true) { cpOk ->
                    if (!cpOk) { controller.failure(ControlIssue.TRANSPORT); finished() }
                    else subscribe(FtmsControlProtocol.STATUS, indications = false) { statusOk ->
                        if (statusOk) controller.configure(selected.powerRange!!)
                        else controller.failure(ControlIssue.TRANSPORT)
                        finished()
                    }
                }
            }
        }
    }
    private fun read(uuid: UUID, done: (ByteArray?) -> Unit) {
        val ch = service?.getCharacteristic(uuid)
        if (ch == null || ch.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) { done(null); return }
        queue.enqueue(Key("read", uuid), { guarded {
            gatt.readCharacteristic(ch).also { Log.i(TAG, "FTMS read invoked uuid=$uuid started=$it") }
        } }) { status, bytes ->
            Log.i(TAG, "FTMS read uuid=$uuid status=$status raw=${hex(bytes)}")
            done(bytes?.takeIf { status == BluetoothGatt.GATT_SUCCESS })
        }
    }
    private fun subscribe(uuid: UUID, indications: Boolean, done: (Boolean) -> Unit) {
        val ch = service?.getCharacteristic(uuid)
        val descriptor = ch?.getDescriptor(FtmsConstants.CLIENT_CHARACTERISTIC_CONFIGURATION)
        if (ch == null || descriptor == null) { done(false); return }
        val bytes = if (indications) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        queue.enqueue(Key("descriptor", uuid), { guarded {
            if (!gatt.setCharacteristicNotification(ch, true)) {
                Log.e(TAG, "FTMS local notification enable failed uuid=$uuid"); false
            } else {
                val result = if (Build.VERSION.SDK_INT >= 33) gatt.writeDescriptor(descriptor, bytes)
                else { descriptor.value = bytes; if (gatt.writeDescriptor(descriptor)) 0 else -1 }
                Log.i(TAG, "FTMS CCCD dispatch uuid=$uuid value=${hex(bytes)} result=$result")
                result == 0
            }
        } }) { status, _ ->
            Log.i(TAG, "FTMS CCCD uuid=$uuid indications=$indications status=$status")
            done(status == BluetoothGatt.GATT_SUCCESS)
        }
    }
    private fun writeControl(bytes: ByteArray): Boolean {
        val ch = service?.getCharacteristic(FtmsControlProtocol.CONTROL_POINT) ?: return false
        if (closed || !queue.idle) return false
        var dispatched = true
        queue.enqueue(Key("write", ch.uuid), { guarded {
            Log.i(TAG, "FTMS outgoing=${hex(bytes)} writeType=DEFAULT")
            val result = if (Build.VERSION.SDK_INT >= 33) gatt.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            else { ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; ch.value = bytes; if (gatt.writeCharacteristic(ch)) 0 else -1 }
            Log.i(TAG, "FTMS control write dispatch result=$result")
            dispatched = result == 0; dispatched
        } }) { status, _ ->
            Log.i(TAG, "FTMS GATT write status=$status; transport only, awaiting matching indication")
            controller.onWrite(status)
        }
        return dispatched
    }
    fun setTargetPower(watts: Int) = !closed && controller.setTargetPower(watts)
    fun onRead(ch: BluetoothGattCharacteristic, status: Int, bytes: ByteArray) {
        if (ch.service === service) queue.complete(Key("read", ch.uuid), status, bytes)
    }
    fun onDescriptor(descriptor: BluetoothGattDescriptor, status: Int): Boolean =
        descriptor.characteristic.service === service && queue.complete(Key("descriptor", descriptor.characteristic.uuid), status)
    fun onWrite(ch: BluetoothGattCharacteristic, status: Int) {
        if (ch.service === service) queue.complete(Key("write", ch.uuid), status)
    }
    fun receive(ch: BluetoothGattCharacteristic, bytes: ByteArray): Boolean {
        if (ch.service !== service || closed) return false
        when (ch.uuid) {
            FtmsControlProtocol.CONTROL_POINT -> {
                Log.i(TAG, "FTMS indication raw=${hex(bytes)}")
                controller.onIndication(bytes)
            }
            FtmsControlProtocol.STATUS -> {
                Log.i(TAG, "FTMS machine status raw=${hex(bytes)}")
                controller.onMachineStatus(bytes)
            }
            else -> return false
        }
        return true
    }
    fun close() { closed = true; queue.close(); controller.reset() }
    private fun guarded(action: () -> Boolean): Boolean = try { action() } catch (e: RuntimeException) {
        Log.e(TAG, "FTMS GATT operation exception", e); false
    }
    private fun hex(bytes: ByteArray?) = bytes?.joinToString(" ") { "%02X".format(it.toInt() and 255) }
    private companion object { const val TAG = "RideLabGatt" }
}
