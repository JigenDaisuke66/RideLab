package com.isaac.ridelab.ble.heartrate

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.isaac.ridelab.ble.BleUuids
import com.isaac.ridelab.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns only the selected heart-rate sensor. Never accesses the trainer client/session.
 * Public methods and callback processing run on the main thread.
 */
@SuppressLint("MissingPermission")
class HeartRateClient(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(HeartRateState())
    val state = mutableState.asStateFlow()
    private var gatt: BluetoothGatt? = null
    private var measurement: BluetoothGattCharacteristic? = null
    private var pendingCccd: BluetoothGattDescriptor? = null
    private var closed = false
    private val timeout = Runnable { fail(GattFailure.TIMEOUT) }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED && gatt != null) guarded {
                if (adapter()?.isEnabled != true) fail(GattFailure.BLUETOOTH_UNAVAILABLE)
            }
        }
    }

    init {
        ContextCompat.registerReceiver(this.context, receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    }

    private fun adapter() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun connect(device: DiscoveredDevice) {
        Log.i(TAG, "Selected address=${device.address} name=${device.name}")
        if (!closed && state.value.isActive && state.value.device?.address == device.address) return
        disconnect()
        mutableState.value = HeartRateState(device = device, connection = ConnectionState.CONNECTING)
        if (closed) { fail(GattFailure.CONNECT_FAILED); return }
        guarded {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                fail(GattFailure.PERMISSION_REQUIRED); return@guarded
            }
            val adapter = adapter()
            if (adapter?.isEnabled != true) { fail(GattFailure.BLUETOOTH_UNAVAILABLE); return@guarded }
            val deviceHandle = adapter.getRemoteDevice(device.address)
            armTimeout()
            Log.i(TAG, "connectGatt invoked address=${deviceHandle.address} autoConnect=false transport=LE")
            gatt = deviceHandle.connectGatt(context, false, callbacks, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) fail(GattFailure.CONNECT_FAILED)
        }
    }

    private val callbacks = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) = dispatch(gatt) {
            Log.i(TAG, "Connection address=${state.value.device?.address} status=$status newState=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail(if (state.value.connection == ConnectionState.CONNECTING) GattFailure.CONNECT_FAILED else GattFailure.CONNECTION_LOST, status)
            } else if (newState == BluetoothProfile.STATE_CONNECTED && state.value.connection == ConnectionState.CONNECTING) {
                mutableState.value = state.value.copy(connection = ConnectionState.DISCOVERING)
                armTimeout()
                val started = gatt.discoverServices()
                Log.i(TAG, "discoverServices invoked started=$started")
                if (!started) fail(GattFailure.DISCOVERY_FAILED)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) fail(GattFailure.CONNECTION_LOST, status)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = dispatch(gatt) {
            if (state.value.connection != ConnectionState.DISCOVERING) return@dispatch
            Log.i(TAG, "Services discovered status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) { fail(GattFailure.DISCOVERY_FAILED, status); return@dispatch }
            val service = gatt.getService(BleUuids.HEART_RATE)
            measurement = service?.getCharacteristic(HeartRateParser.MEASUREMENT)
            mutableState.value = state.value.copy(discoveryComplete = true,
                serviceFound = service != null, measurementFound = measurement != null)
            Log.i(TAG, "Detection service=0x180D found=${service != null} measurement=0x2A37 found=${measurement != null} properties=${measurement?.properties?.toString(16)}")
            val characteristic = measurement
            if (characteristic == null) { fail(GattFailure.DISCOVERY_FAILED); return@dispatch }
            subscribe(gatt, characteristic)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = dispatch(gatt) {
            if (descriptor !== pendingCccd || state.value.connection != ConnectionState.SUBSCRIBING) return@dispatch
            pendingCccd = null
            Log.i(TAG, "CCCD subscription completed status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) fail(GattFailure.SUBSCRIPTION_FAILED, status)
            else {
                handler.removeCallbacks(timeout)
                mutableState.value = state.value.copy(connection = ConnectionState.STREAMING)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val snapshot = value.copyOf()
            dispatch(gatt) { receive(characteristic, snapshot) }
        }

        @Deprecated("Android 12 callback")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) {
                val snapshot = characteristic.value?.copyOf() ?: byteArrayOf()
                dispatch(gatt) { receive(characteristic, snapshot) }
            }
        }

        override fun onServiceChanged(gatt: BluetoothGatt) = dispatch(gatt) {
            Log.w(TAG, "GATT database changed; reconnect required")
            fail(GattFailure.DISCOVERY_FAILED)
        }
    }

    @Suppress("DEPRECATION")
    private fun subscribe(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) { fail(GattFailure.NOTIFY_UNSUPPORTED); return }
        val cccd = characteristic.getDescriptor(HeartRateParser.CCCD)
        Log.i(TAG, "CCCD detected=${cccd != null}")
        if (cccd == null) { fail(GattFailure.CCCD_MISSING); return }
        if (!gatt.setCharacteristicNotification(characteristic, true)) { fail(GattFailure.SUBSCRIPTION_FAILED); return }
        mutableState.value = state.value.copy(connection = ConnectionState.SUBSCRIBING)
        pendingCccd = cccd
        armTimeout()
        val result = if (Build.VERSION.SDK_INT >= 33) gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        else {
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (gatt.writeDescriptor(cccd)) 0 else -1
        }
        Log.i(TAG, "CCCD subscription dispatched value=0100 result=$result")
        if (result != 0) fail(GattFailure.SUBSCRIPTION_FAILED, result)
    }

    private fun receive(characteristic: BluetoothGattCharacteristic, bytes: ByteArray) {
        if (characteristic !== measurement || state.value.connection !in setOf(ConnectionState.SUBSCRIBING, ConnectionState.STREAMING)) return
        val raw = bytes.joinToString(" ") { "%02X".format(it.toInt() and 255) }
        when (val result = HeartRateParser.parse(bytes)) {
            is HeartRateParseResult.Success -> {
                Log.d(TAG, "Measurement uuid=0x2A37 raw=$raw flags=0x${result.measurement.flags.toString(16)} bpm=${result.measurement.bpm}")
                mutableState.value = state.value.copy(heartRateBpm = result.measurement.bpm,
                    receivedAtMs = SystemClock.elapsedRealtime(), packetError = null)
            }
            is HeartRateParseResult.Invalid -> {
                Log.w(TAG, "Malformed uuid=0x2A37 raw=$raw reason=${result.reason}")
                mutableState.value = state.value.copy(packetError = result.reason)
            }
        }
    }

    private fun dispatch(source: BluetoothGatt, action: () -> Unit) {
        handler.post { if (!closed && source === gatt) guarded(action) }
    }

    private fun guarded(action: () -> Unit) {
        try { action() }
        catch (error: SecurityException) {
            Log.e(TAG, "Bluetooth permission exception", error); fail(GattFailure.PERMISSION_REQUIRED)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Bluetooth platform exception", error); fail(GattFailure.PLATFORM_EXCEPTION)
        }
    }

    private fun armTimeout() { handler.removeCallbacks(timeout); handler.postDelayed(timeout, 20_000) }

    private fun fail(reason: GattFailure, status: Int? = null) {
        Log.e(TAG, "Failure address=${state.value.device?.address} phase=${state.value.connection} reason=$reason status=$status")
        release()
        mutableState.value = state.value.copy(connection = ConnectionState.FAILED,
            issue = GattIssue(reason, status), heartRateBpm = null, receivedAtMs = null, packetError = null)
    }

    private fun release(): Boolean {
        handler.removeCallbacks(timeout)
        val previous = gatt
        gatt = null // Reject stale callbacks before closing this sensor's session.
        measurement = null; pendingCccd = null
        var clean = true
        if (previous != null) {
            try { previous.disconnect() } catch (error: RuntimeException) { clean = false; Log.e(TAG, "Disconnect failed", error) }
            try { previous.close() } catch (error: RuntimeException) { clean = false; Log.e(TAG, "Close failed", error) }
        }
        return clean
    }

    fun disconnect() {
        if (gatt != null) Log.i(TAG, "Disconnect requested address=${state.value.device?.address}")
        val clean = release()
        mutableState.value = state.value.copy(connection = ConnectionState.DISCONNECTED, heartRateBpm = null,
            receivedAtMs = null, packetError = null, issue = if (clean) null else GattIssue(GattFailure.CLEANUP_FAILED))
    }

    fun close() {
        if (closed) return
        closed = true
        disconnect()
        context.unregisterReceiver(receiver)
    }

    private companion object { const val TAG = "RideLabHeartRate" }
}

