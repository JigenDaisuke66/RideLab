package com.isaac.ridelab.ui.screen

import android.app.Application
import android.content.*
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.isaac.ridelab.ble.BleScanner
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI/scanner owner only. The binder is a command/state bridge, not a GATT owner. */
class DevicesViewModel(application: Application) : AndroidViewModel(application) {
    val scanner = BleScanner(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observations: Job? = null
    private var service: RideRecordingService? = null
    private var bound = false
    private var visible = false
    private val mutableTrainer = MutableStateFlow(GattDeviceState())
    private val mutableHeartRate = MutableStateFlow(HeartRateState())
    private val mutableRide = MutableStateFlow(RideState())
    private val mutableReady = MutableStateFlow(false)
    val trainerState = mutableTrainer.asStateFlow()
    val heartRateState = mutableHeartRate.asStateFlow()
    val rideState = mutableRide.asStateFlow()
    val serviceReady = mutableReady.asStateFlow()
    var pendingConnection: DiscoveredDevice? = null
    var pendingHeartRateConnection: DiscoveredDevice? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val owner = (binder as RideRecordingService.LocalBinder).service
            service = owner
            observations?.cancel()
            observations = scope.launch {
                launch { owner.trainerState.collect { mutableTrainer.value = it } }
                launch { owner.heartRateState.collect { mutableHeartRate.value = it } }
                launch { owner.rideState.collect { mutableRide.value = it } }
            }
            mutableReady.value = true
            if (!visible) owner.uiHidden()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            observations?.cancel(); service = null; mutableReady.value = false
            mutableTrainer.value = GattDeviceState(connection = ConnectionState.FAILED, issue = GattIssue(GattFailure.CONNECTION_LOST))
            mutableHeartRate.value = HeartRateState(connection = ConnectionState.FAILED, issue = GattIssue(GattFailure.CONNECTION_LOST))
            mutableRide.value = RideState(RidePhase.ERROR, error = RideError.SERVICE_LOST)
        }
    }

    init {
        try {
            bound = application.bindService(Intent(application, RideRecordingService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (!bound) mutableRide.value = RideState(RidePhase.ERROR, error = RideError.SERVICE_LOST)
        } catch (error: RuntimeException) {
            Log.e("RideLabRide", "Service binding failed", error)
            mutableRide.value = RideState(RidePhase.ERROR, error = RideError.SERVICE_LOST)
        }
    }

    fun connect(device: DiscoveredDevice) {
        Log.i("RideLabGatt", "ViewModel connect address=${device.address}")
        scanner.stop(); service?.connectTrainer(device)
    }
    fun connectHeartRate(device: DiscoveredDevice) { scanner.stop(); service?.connectHeartRate(device) }
    fun disconnectTrainer() { service?.disconnectTrainer() }
    fun disconnectHeartRate() { service?.disconnectHeartRate() }
    fun sendFecTarget(watts: Int) { service?.sendFecTarget(watts) }
    fun startRide() { scanner.stop(); service?.requestStart() }
    fun endRide() { service?.endRide() }
    fun enterForeground() { visible = true }

    fun leaveForeground() {
        visible = false
        scanner.stop()
        service?.uiHidden() // Service policy preserves STARTING/ACTIVE sessions.
    }

    override fun onCleared() {
        scanner.close()
        service?.uiHidden()
        observations?.cancel(); scope.cancel()
        if (bound) getApplication<Application>().unbindService(connection)
        service = null
        // No client disconnect/close here. An ACTIVE service outlives this ViewModel.
    }
}
