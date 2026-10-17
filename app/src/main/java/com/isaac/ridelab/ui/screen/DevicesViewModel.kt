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
    private val mutableOverlay = MutableStateFlow(com.isaac.ridelab.ui.overlay.RideOverlayState.OFF)
    val overlayState = mutableOverlay.asStateFlow()
    fun setOverlay(enabled: Boolean) { service?.setOverlay(enabled) }
    val trainerState = mutableTrainer.asStateFlow()
    val heartRateState = mutableHeartRate.asStateFlow()
    val rideState = mutableRide.asStateFlow()
    val serviceReady = mutableReady.asStateFlow()
    private val trend = com.isaac.ridelab.ui.ride.LiveRideTrend()
    private val mutableTrend = MutableStateFlow(com.isaac.ridelab.ui.ride.LiveTrendState())
    val liveTrend = mutableTrend.asStateFlow()
    private val mutableWorkout = MutableStateFlow(com.isaac.ridelab.workout.WorkoutState())
    val workoutState = mutableWorkout.asStateFlow()
    private val rampReview = (application as com.isaac.ridelab.RideLabApplication).rampReview
    val rampResult = rampReview.state
    fun startWorkout(id: com.isaac.ridelab.workout.WorkoutId) { scanner.stop(); service?.requestWorkout(id) }
    fun pauseWorkout() { service?.pauseWorkout() }
    fun resumeWorkout() { service?.resumeWorkout() }
    fun skipWorkout() { service?.skipWorkout() }
    fun endWorkout() { service?.endWorkout() }
    fun keepRampResult() { rampReview.keep() }
    fun acceptRampResult() { scope.launch {
        try { rampReview.accept() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.e("RideLabWorkout", "FTP acceptance save failed", e) }
    } }
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
                launch { owner.overlayState.collect { mutableOverlay.value = it } }
                launch { owner.rideState.collect { mutableRide.value = it } }
                launch { owner.workoutState.collect { mutableWorkout.value = it } }
            }
            mutableReady.value = true
            if (!visible) owner.uiHidden()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            mutableOverlay.value = com.isaac.ridelab.ui.overlay.RideOverlayState.OFF
            observations?.cancel(); service = null; mutableReady.value = false
            mutableTrainer.value = GattDeviceState(connection = ConnectionState.FAILED, issue = GattIssue(GattFailure.CONNECTION_LOST))
            mutableHeartRate.value = HeartRateState(connection = ConnectionState.FAILED, issue = GattIssue(GattFailure.CONNECTION_LOST))
            mutableRide.value = RideState(RidePhase.ERROR, error = RideError.SERVICE_LOST)
            if (mutableWorkout.value.controlsTrainer) mutableWorkout.value = mutableWorkout.value.copy(
                phase = com.isaac.ridelab.workout.WorkoutPhase.ERROR, issue = com.isaac.ridelab.workout.WorkoutIssue.RIDE_FAILED)
        }
    }

    init {
        scope.launch {
            while (isActive) {
                if (visible) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    mutableTrend.value = trend.update(mutableRide.value, now,
                        LiveRideData.snapshot(mutableTrainer.value, mutableHeartRate.value, now))
                }
                delay(1000)
            }
        }
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
    fun setTargetPower(watts: Int) { service?.setTargetPower(watts) }
    fun startRide() { scanner.stop(); service?.requestStart() }
    fun endRide() { service?.endRide() }
    fun pauseRide() { service?.pauseRide() }
    fun resumeRide() { service?.resumeRide() }
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
