package com.isaac.ridelab.ble.ftms

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.isaac.ridelab.ble.BleUuids
import com.isaac.ridelab.data.model.ConnectionState
import com.isaac.ridelab.data.model.DiscoveredDevice
import com.isaac.ridelab.data.model.GattCharacteristicInfo
import com.isaac.ridelab.data.model.GattDeviceState
import com.isaac.ridelab.data.model.GattFailure
import com.isaac.ridelab.data.model.GattIssue
import com.isaac.ridelab.data.model.GattServiceInfo
import com.isaac.ridelab.data.model.TrainerMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One selected device, native GATT discovery and passive FTMS data only.
 * Public calls and all state updates run on the main thread. No control point writes.
 */
@SuppressLint("MissingPermission") // Checked before connection; revocations are handled by guarded operations.
class FtmsClient(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(GattDeviceState())
    val state = mutableState.asStateFlow()
    private var gatt: BluetoothGatt? = null
    private var bikeData: BluetoothGattCharacteristic? = null
    private var pendingCccd: BluetoothGattDescriptor? = null
    private var closed = false
    private val timeout = Runnable {
        Log.e(TAG, "Operation timeout phase=${state.value.connection} address=${state.value.device?.address}")
        fail(GattFailure.TIMEOUT)
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED && gatt != null) {
                guarded {
                    if (adapter()?.isEnabled != true) fail(GattFailure.BLUETOOTH_UNAVAILABLE)
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(this.context, receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    }

    private fun adapter() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun connect(device: DiscoveredDevice) {
        if (closed) {
            Log.e(TAG, "Connect rejected: client is closed address=${device.address}")
            mutableState.value = GattDeviceState(device = device, connection = ConnectionState.FAILED,
                issue = GattIssue(GattFailure.CONNECT_FAILED))
            return
        }
        disconnect()
        mutableState.value = GattDeviceState(device = device, connection = ConnectionState.CONNECTING)
        Log.i(TAG, "Connect address=${device.address} name=${device.name} advertised=${device.advertisedServices}")
        guarded {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                fail(GattFailure.PERMISSION_REQUIRED)
                return@guarded
            }
            val adapter = adapter()
            if (adapter == null || !adapter.isEnabled) {
                fail(GattFailure.BLUETOOTH_UNAVAILABLE)
                return@guarded
            }
            armTimeout()
            val bluetoothDevice = adapter.getRemoteDevice(device.address)
            Log.i(TAG, "connectGatt invoked address=${bluetoothDevice.address} autoConnect=false transport=LE")
            gatt = bluetoothDevice.connectGatt(
                context, false, callbacks, BluetoothDevice.TRANSPORT_LE,
            )
            Log.i(TAG, "connectGatt returned address=${bluetoothDevice.address} hasGatt=${gatt != null}")
            if (gatt == null) fail(GattFailure.CONNECT_FAILED)
        }
    }

    private val callbacks = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) = dispatch(gatt) {
            Log.i(TAG, "Connection status=$status newState=$newState address=${state.value.device?.address}")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail(if (state.value.connection == ConnectionState.CONNECTING) GattFailure.CONNECT_FAILED else GattFailure.CONNECTION_LOST, status)
            } else if (newState == BluetoothProfile.STATE_CONNECTED && state.value.connection == ConnectionState.CONNECTING) {
                mutableState.value = state.value.copy(connection = ConnectionState.DISCOVERING)
                armTimeout()
                Log.i(TAG, "discoverServices invoked address=${state.value.device?.address}")
                val started = gatt.discoverServices()
                Log.i(TAG, "discoverServices returned address=${state.value.device?.address} started=$started")
                if (!started) fail(GattFailure.DISCOVERY_FAILED)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                fail(GattFailure.CONNECTION_LOST, status)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = dispatch(gatt) {
            if (state.value.connection != ConnectionState.DISCOVERING) return@dispatch
            Log.i(TAG, "Services discovered address=${state.value.device?.address} status=$status count=${gatt.services.size}")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail(GattFailure.DISCOVERY_FAILED, status)
                return@dispatch
            }
            handler.removeCallbacks(timeout)
            val services = gatt.services.map { service ->
                Log.i(TAG, "Service uuid=${service.uuid} instance=${service.instanceId} type=${service.type}")
                GattServiceInfo(service.uuid, service.instanceId, service.characteristics.map { characteristic ->
                    Log.i(TAG, "Characteristic service=${service.uuid} uuid=${characteristic.uuid} instance=${characteristic.instanceId} properties=0x${characteristic.properties.toString(16)}")
                    characteristic.descriptors.forEach { Log.i(TAG, "Descriptor characteristic=${characteristic.uuid} uuid=${it.uuid}") }
                    GattCharacteristicInfo(characteristic.uuid, characteristic.properties, characteristic.descriptors.map { it.uuid })
                })
            }
            mutableState.value = state.value.copy(services = services, discoveryComplete = true, connection = ConnectionState.CONNECTED)
            Log.i(TAG, "Capabilities FTMS=${state.value.hasFtms} CyclingPower=${state.value.hasCyclingPower} IndoorBikeData=${state.value.hasIndoorBikeData}")
            // Advertising is never used to gate connection or FTMS detection.
            bikeData = gatt.services.filter { it.uuid == BleUuids.FITNESS_MACHINE }
                .flatMap { it.characteristics }.firstOrNull { it.uuid == FtmsConstants.INDOOR_BIKE_DATA }
            bikeData?.let { subscribe(gatt, it) }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = dispatch(gatt) {
            if (descriptor !== pendingCccd || state.value.connection != ConnectionState.SUBSCRIBING) return@dispatch
            Log.i(TAG, "CCCD write completed uuid=${descriptor.uuid} status=$status")
            pendingCccd = null
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail(GattFailure.SUBSCRIPTION_FAILED, status)
            } else {
                handler.removeCallbacks(timeout)
                mutableState.value = state.value.copy(connection = ConnectionState.STREAMING)
                Log.i(TAG, "Indoor Bike Data notifications enabled; waiting for packets")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val snapshot = value.copyOf()
            dispatch(gatt) { receive(characteristic, snapshot) }
        }

        @Deprecated("Legacy callback for Android 12 and 12L")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // Android 13+ dispatches the value-bearing callback; do not read its mutable cache.
            if (Build.VERSION.SDK_INT < 33) {
                val snapshot = characteristic.value?.copyOf() ?: byteArrayOf()
                dispatch(gatt) { receive(characteristic, snapshot) }
            }
        }

        override fun onServiceChanged(gatt: BluetoothGatt) = dispatch(gatt) {
            Log.w(TAG, "GATT database changed; reconnect to rediscover services")
            fail(GattFailure.DISCOVERY_FAILED)
        }
    }

    @Suppress("DEPRECATION")
    private fun subscribe(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) {
            fail(GattFailure.NOTIFY_UNSUPPORTED)
            return
        }
        val cccd = characteristic.getDescriptor(FtmsConstants.CLIENT_CHARACTERISTIC_CONFIGURATION)
        if (cccd == null) {
            fail(GattFailure.CCCD_MISSING)
            return
        }
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            fail(GattFailure.SUBSCRIPTION_FAILED)
            return
        }
        mutableState.value = state.value.copy(connection = ConnectionState.SUBSCRIBING)
        pendingCccd = cccd
        armTimeout()
        Log.i(TAG, "CCCD write started characteristic=${characteristic.uuid} value=0100")
        if (Build.VERSION.SDK_INT >= 33) {
            val result = gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            if (result != BluetoothStatusCodes.SUCCESS) fail(GattFailure.SUBSCRIPTION_FAILED, result)
        } else {
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (!gatt.writeDescriptor(cccd)) fail(GattFailure.SUBSCRIPTION_FAILED)
        }
    }

    private fun receive(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic !== bikeData || state.value.connection !in setOf(ConnectionState.SUBSCRIBING, ConnectionState.STREAMING)) return
        val hex = value.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val count = state.value.notificationCount + 1
        when (val result = FtmsParser.parseIndoorBikeData(value)) {
            is IndoorBikeParseResult.Success -> {
                val data = result.data
                Log.d(TAG, "IndoorBikeData packet=$count raw=$hex flags=0x${data.flags.toString(16)} speedKph=${data.speedKph} cadenceRpm=${data.cadenceRpm} trainerPowerWatts=${data.instantaneousPowerWatts}")
                mutableState.value = state.value.copy(
                    metrics = state.value.metrics.update(data, SystemClock.elapsedRealtime()),
                    notificationCount = count, lastPacketError = null,
                )
            }
            is IndoorBikeParseResult.Invalid -> {
                Log.w(TAG, "Malformed IndoorBikeData packet=$count raw=$hex reason=${result.reason} offset=${result.offset}")
                mutableState.value = state.value.copy(notificationCount = count,
                    malformedPacketCount = state.value.malformedPacketCount + 1, lastPacketError = result.reason)
            }
        }
    }

    private fun dispatch(source: BluetoothGatt, action: () -> Unit) {
        handler.post {
            if (!closed && source === gatt) guarded(action)
            else Log.d(TAG, "Ignoring callback from closed/replaced GATT session")
        }
    }

    private fun guarded(action: () -> Unit) {
        try {
            action()
        } catch (error: SecurityException) {
            Log.e(TAG, "Bluetooth permission failure phase=${state.value.connection}", error)
            fail(GattFailure.PERMISSION_REQUIRED)
        } catch (error: RuntimeException) {
            Log.e(TAG, "GATT exception phase=${state.value.connection}", error)
            fail(GattFailure.PLATFORM_EXCEPTION)
        }
    }

    private fun armTimeout() {
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 20_000)
    }

    private fun fail(reason: GattFailure, code: Int? = null) {
        Log.e(TAG, "GATT failure reason=$reason code=$code phase=${state.value.connection} address=${state.value.device?.address}")
        releaseGatt()
        mutableState.value = state.value.copy(connection = ConnectionState.FAILED,
            issue = GattIssue(reason, code), metrics = TrainerMetrics())
    }

    private fun releaseGatt(): Boolean {
        handler.removeCallbacks(timeout)
        val previous = gatt
        gatt = null // Invalidate queued callbacks before disconnect/close.
        bikeData = null
        pendingCccd = null
        var clean = true
        if (previous != null) {
            try { previous.disconnect() } catch (error: RuntimeException) {
                clean = false
                Log.e(TAG, "GATT disconnect failed", error)
            }
            try { previous.close() } catch (error: RuntimeException) {
                clean = false
                Log.e(TAG, "GATT close failed", error)
            }
        }
        return clean
    }

    fun disconnect() {
        if (gatt != null) Log.i(TAG, "Disconnect requested address=${state.value.device?.address}")
        val clean = releaseGatt()
        // Retain the discovered UUIDs for diagnosis, but never show old measurements as live.
        mutableState.value = state.value.copy(connection = ConnectionState.DISCONNECTED,
            metrics = TrainerMetrics(), issue = if (clean) state.value.issue else GattIssue(GattFailure.CLEANUP_FAILED))
    }

    fun close() {
        if (closed) return
        closed = true
        disconnect()
        context.unregisterReceiver(receiver)
    }

    private companion object { const val TAG = "RideLabGatt" }
}

