package com.isaac.ridelab.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback                                                                                            
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.isaac.ridelab.data.model.DiscoveredDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScanStatus { IDLE, SCANNING, FINISHED, PERMISSION_REQUIRED, BLUETOOTH_OFF, UNSUPPORTED, FAILED }

data class BleScanState(
    val status: ScanStatus = ScanStatus.IDLE,
    val devices: List<DiscoveredDevice> = emptyList(),
    val errorCode: Int? = null,
)

/** Foreground discovery only; no connections or ride ownership. Calls are on the main thread. */
class BleScanner(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(BleScanState())
    val state = mutableState.asStateFlow()
    private var scanner: BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    private val timeout = Runnable { stop() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) refreshAvailability()
        }
    }

    init {
        ContextCompat.registerReceiver(this.context, receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    }

    fun hasPermissions(): Boolean = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
    ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    fun refreshAvailability(): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val unavailable = try {
            when {
                adapter == null -> ScanStatus.UNSUPPORTED
                !hasPermissions() -> ScanStatus.PERMISSION_REQUIRED
                !adapter.isEnabled -> ScanStatus.BLUETOOTH_OFF
                else -> null
            }
        } catch (error: SecurityException) {
            Log.e(TAG, "Cannot read Bluetooth availability", error)
            ScanStatus.PERMISSION_REQUIRED
        }
        if (unavailable != null) {
            stop()
            mutableState.value = mutableState.value.copy(status = unavailable)
            return false
        }
        if (state.value.status in setOf(ScanStatus.UNSUPPORTED, ScanStatus.PERMISSION_REQUIRED, ScanStatus.BLUETOOTH_OFF)) {
            mutableState.value = mutableState.value.copy(status = ScanStatus.IDLE)
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (callback != null || !refreshAvailability()) return
        val session = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handler.post { if (callback === this) accept(result) }
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                handler.post { if (callback === this) results.forEach { accept(it) } }
            }
            override fun onScanFailed(errorCode: Int) {
                handler.post {
                    if (callback === this) {
                        Log.e(TAG, "BLE scan failed: $errorCode")
                        stop()
                        mutableState.value = mutableState.value.copy(status = ScanStatus.FAILED, errorCode = errorCode)
                    }
                }
            }
        }
        try {
            scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
            if (scanner == null) {
                mutableState.value = mutableState.value.copy(status = ScanStatus.BLUETOOTH_OFF)
                return
            }
            callback = session
            mutableState.value = BleScanState(status = ScanStatus.SCANNING)
            // Unfiltered discovery retains devices whose advertisements omit service UUIDs.
            scanner!!.startScan(null, ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), session)
            handler.postDelayed(timeout, 15_000)
        } catch (error: RuntimeException) {
            fail(error)
        }
    }

    @SuppressLint("MissingPermission")
    private fun accept(result: ScanResult) {
        try {
            val address = result.device.address
            val previous = state.value.devices.find { it.address == address }
            val services = previous?.advertisedServices.orEmpty() +
                result.scanRecord?.serviceUuids.orEmpty().map { it.uuid }
            val device = DiscoveredDevice(
                result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                    ?: previous?.name ?: result.device.name?.takeIf { it.isNotBlank() },
                address, result.rssi, services, BleUuids.classify(services),
            )
            val devices = state.value.devices.toMutableList()
            val index = devices.indexOfFirst { it.address == address }
            if (index < 0) devices.add(device) else devices[index] = device
            mutableState.value = mutableState.value.copy(devices = devices)
        } catch (error: RuntimeException) {
            fail(error)
        }
    }

    private fun fail(error: RuntimeException) {
        Log.e(TAG, "BLE scan operation failed", error)
        stop()
        mutableState.value = mutableState.value.copy(status =
            if (error is SecurityException) ScanStatus.PERMISSION_REQUIRED else ScanStatus.FAILED)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        handler.removeCallbacks(timeout)
        val active = callback
        callback = null // Ignore queued callbacks from a stopped scan.
        try {
            if (active != null) scanner?.stopScan(active)
            if (state.value.status == ScanStatus.SCANNING) {
                mutableState.value = mutableState.value.copy(status = ScanStatus.FINISHED)
            }
        } catch (error: RuntimeException) {
            Log.e(TAG, "Cannot stop BLE scan", error)
            mutableState.value = mutableState.value.copy(status =
                if (error is SecurityException) ScanStatus.PERMISSION_REQUIRED else ScanStatus.FAILED)
        } finally {
            scanner = null
        }
    }

    fun close() {
        stop()
        context.unregisterReceiver(receiver)
    }

    private companion object { const val TAG = "BleScanner" }
}

