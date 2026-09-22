package com.isaac.ridelab.service

import com.isaac.ridelab.ui.overlay.RideOverlayState
import com.isaac.ridelab.ui.overlay.RideOverlayWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.provider.Settings
import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.isaac.ridelab.MainActivity
import com.isaac.ridelab.RideLabApplication
import com.isaac.ridelab.R
import com.isaac.ridelab.ble.ftms.FtmsClient
import com.isaac.ridelab.ble.heartrate.HeartRateClient
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.locale.AppLocale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Owns the only trainer and HR clients, including the pre-ride preview connection.
 * Starting a ride promotes this same instance; no GATT objects are migrated/recreated.
 */
class RideRecordingService : Service() {
    private lateinit var trainer: FtmsClient
    private lateinit var heartRate: HeartRateClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session = RideSession { disconnectAll() }
    val rideState get() = session.state
    val trainerState get() = trainer.state
    val heartRateState get() = heartRate.state
    private val trainerBudget = ReconnectBudget()
    private val hrBudget = ReconnectBudget()
    private var trainerRetry: Job? = null
    private var hrRetry: Job? = null
    private var trainerWanted: DiscoveredDevice? = null
    private var hrWanted: DiscoveredDevice? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val binder = LocalBinder()
    private val repository get() = (application as RideLabApplication).rideRepository
    private val recorder by lazy { RideRecorder(repository) }
    private var startup: Job? = null
    private var recording: Job? = null
    private var finalizing: Job? = null
    private lateinit var recovery: Deferred<Unit>
    private val mutableOverlay = MutableStateFlow(RideOverlayState.OFF)
    val overlayState = mutableOverlay.asStateFlow()
    private var overlayJob: Job? = null
    private var overlayWindow: RideOverlayWindow? = null

    fun setOverlay(enabled: Boolean) {
        overlayJob?.cancel(); overlayJob = null
        if (!enabled || rideState.value.phase != RidePhase.ACTIVE) {
            overlayWindow?.hide(); mutableOverlay.value = RideOverlayState.OFF; return
        }
        overlayJob = scope.launch {
            try {
                while (isActive && rideState.value.phase == RidePhase.ACTIVE) {
                    if (!Settings.canDrawOverlays(this@RideRecordingService)) {
                        overlayWindow?.hide(); mutableOverlay.value = RideOverlayState.PERMISSION_REQUIRED; break
                    }
                    if (overlayWindow == null) overlayWindow = RideOverlayWindow(this@RideRecordingService) { setOverlay(false) }
                    val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
                    val interactive = getSystemService(PowerManager::class.java).isInteractive
                    if (locked || !interactive) overlayWindow?.hide()
                    else if (overlayWindow?.show(liveSnapshot(SystemClock.elapsedRealtime())) != true) {
                        mutableOverlay.value = RideOverlayState.FAILED
                        break
                    }
                    ensureActive()
                    mutableOverlay.value = RideOverlayState.VISIBLE
                    delay(1000)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("RideLabOverlay", "Overlay failed", e)
                overlayWindow?.hide(); mutableOverlay.value = RideOverlayState.FAILED
            }
        }
    }

    private var destroyed = false
    private var recordingError: Exception? = null

    inner class LocalBinder : Binder() { val service get() = this@RideRecordingService }

    override fun onCreate() {
        super.onCreate()
        trainer = FtmsClient(this)
        heartRate = HeartRateClient(this)
        Log.i(TAG, "Service created; sole BLE owner")
        scope.launch { rideState.map { it.phase }.distinctUntilChanged().collect { if (it != RidePhase.ACTIVE) setOverlay(false) } }
        recovery = scope.async {
            try {
                val count = repository.recoverInterrupted()
                Log.i(TAG, "Recording recovery abortedSessions=$count")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e(TAG, "Recording recovery failed", error)
                // start() retries recovery and exposes any continuing failure through ride state.
            }
        }
        scope.launch {
            trainer.state.map { it.connection to it.issue?.reason }.distinctUntilChanged().collect { (connection, failure) ->
                if (connection == ConnectionState.FAILED) reconnectTrainer(failure)
            }
        }
        scope.launch {
            heartRate.state.map { it.connection to it.issue?.reason }.distinctUntilChanged().collect { (connection, failure) ->
                if (connection == ConnectionState.FAILED) reconnectHeartRate(failure)
            }
        }
    }

    override fun onBind(intent: Intent) = binder

    fun connectTrainer(device: DiscoveredDevice) {
        if (trainer.state.value.isActive && trainer.state.value.device?.address == device.address) return
        trainerRetry?.cancel(); trainerBudget.reset(); trainerWanted = device
        trainer.connect(device)
    }

    fun connectHeartRate(device: DiscoveredDevice) {
        if (heartRate.state.value.isActive && heartRate.state.value.device?.address == device.address) return
        hrRetry?.cancel(); hrBudget.reset(); hrWanted = device
        heartRate.connect(device)
    }

    fun disconnectTrainer() {
        trainerWanted = null; trainerRetry?.cancel(); trainerRetry = null
        trainer.disconnect()
    }

    fun disconnectHeartRate() {
        hrWanted = null; hrRetry?.cancel(); hrRetry = null
        heartRate.disconnect()
    }

    fun sendFecTarget(watts: Int) = trainer.sendFecTarget(watts)

    fun uiHidden() {
        Log.i(TAG, "UI hidden phase=${rideState.value.phase} preserve=${session.keepsConnections}")
        session.uiHidden()
    }

    fun requestStart() {
        if (!session.begin()) return
        recordingError = null
        Log.i(TAG, "Ride STARTING; retaining existing GATT sessions")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            session.fail(RideError.BLUETOOTH_PERMISSION); return
        }
        val manager = getSystemService(NotificationManager::class.java)
        createChannel()
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
            session.fail(RideError.NOTIFICATIONS_DISABLED); return
        }
        try {
            ContextCompat.startForegroundService(this, Intent(this, RideRecordingService::class.java).setAction(START))
        } catch (error: RuntimeException) {
            Log.e(TAG, "Foreground service start failed", error)
            session.fail(RideError.START_FAILED)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == END) { endRide(); return START_NOT_STICKY }
        if (intent?.action != START || rideState.value.phase != RidePhase.STARTING) {
            if (!session.keepsConnections) stopSelf()
            return START_NOT_STICKY
        }
        try {
            val startedAt = SystemClock.elapsedRealtime()
            val startEpoch = System.currentTimeMillis()
            startForeground(NOTIFICATION_ID, notification(startedAt), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            // BLE callbacks/reconnect timers must be processed with the display off.
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RideLab:ActiveRide").apply { acquire() }
            startup = scope.launch {
                try {
                    recovery.await()
                    val start = startedAt
                    val trainerDevice = trainer.state.value.device
                    val hrDevice = heartRate.state.value.device
                    val id = recorder.start(com.isaac.ridelab.data.model.RideSession(
                        startTimeEpochMillis = startEpoch, trainerName = trainerDevice?.name,
                        trainerAddress = trainerDevice?.address, heartRateSensorName = hrDevice?.name,
                        heartRateSensorAddress = hrDevice?.address), start)
                    ensureActive()
                    if (rideState.value.phase == RidePhase.STOPPING) return@launch
                    trainerBudget.reset(); hrBudget.reset()
                    session.activate(start, id)
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(start))
                    Log.i(TAG, "Ride ACTIVE sessionId=$id startedAtElapsedMs=$start trainer=${trainerDevice?.address} hr=${hrDevice?.address}")
                    startRecordingLoop(start, id)
                    if (trainer.state.value.connection == ConnectionState.FAILED) reconnectTrainer(trainer.state.value.issue?.reason)
                    if (heartRate.state.value.connection == ConnectionState.FAILED) reconnectHeartRate(heartRate.state.value.issue?.reason)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    recordingFailure(error)
                }
            }
        } catch (error: RuntimeException) {
            Log.e(TAG, "Foreground promotion failed", error)
            session.fail(RideError.START_FAILED)
            stopForeground(STOP_FOREGROUND_REMOVE)
            releaseWakeLock()
            stopSelf()
        }
        // A killed process is recovered as ABORTED; never restart or replay control automatically.
        return START_NOT_STICKY
    }

    fun endRide() {
        if (!session.beginStop()) return
        Log.i(TAG, "Ride STOPPING: explicit end")
        val endedAt = SystemClock.elapsedRealtime()
        val endEpoch = System.currentTimeMillis()
        val last = liveSnapshot(endedAt)
        finalizing = scope.launch {
            try {
                startup?.join()
                recording?.cancelAndJoin()
                recordingError?.let { throw it }
                val id = checkNotNull(recorder.complete(last, endEpoch, endedAt))
                Log.i(TAG, "Recording COMPLETED sessionId=$id endEpoch=$endEpoch")
                session.end() // Flush/finalization succeeded before existing BLE shutdown.
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e(TAG, "Recording completion failed", error)
                session.fail(RideError.RECORDING_FAILED)
            } finally { if (!destroyed) stopRideWork() }
        }
    }

    private fun stopRideWork() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        stopSelf() // A visible UI may keep an idle bound instance for new device selection.
        Log.i(TAG, "Ride phase=${rideState.value.phase}; GATT closed, foreground stopped")
    }

    private fun liveSnapshot(nowMs: Long) = LiveRideData.snapshot(trainer.state.value, heartRate.state.value, nowMs)

    private fun startRecordingLoop(start: Long, id: Long) {
        recording = scope.launch {
            try {
                while (isActive && rideState.value.phase == RidePhase.ACTIVE) {
                    val now = SystemClock.elapsedRealtime()
                    recorder.sample(liveSnapshot(now), System.currentTimeMillis(), now)
                    // Schedule the next second, skipping missed slots rather than fabricating catch-up samples.
                    val after = SystemClock.elapsedRealtime()
                    val next = start + ((after - start) / 1_000 + 1) * 1_000
                    delay((next - after).coerceAtLeast(1))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e(TAG, "Recording insert failed sessionId=$id", error)
                recordingFailure(error)
            }
        }
    }

    private suspend fun recordingFailure(error: Exception) {
        recordingError = error
        Log.e(TAG, "Recording failed; aborting ride", error)
        session.beginStop()
        try { recorder.abort() } catch (abortError: Exception) { Log.e(TAG, "Recording abort failed; recover on next start", abortError) }
        session.fail(RideError.RECORDING_FAILED)
        stopRideWork()
    }

    private fun disconnectAll() { disconnectTrainer(); disconnectHeartRate() }

    private fun reconnectTrainer(failure: GattFailure?) {
        val device = trainerWanted ?: return
        if (trainerRetry?.isActive == true || !trainerBudget.take(rideState.value.phase == RidePhase.ACTIVE, failure)) return
        Log.w(TAG, "Reconnect scheduled role=trainer address=${device.address} delayMs=3000 attempt=1/1")
        trainerRetry = scope.launch {
            delay(3_000)
            if (rideState.value.phase == RidePhase.ACTIVE && trainerWanted == device && !trainer.state.value.isActive) {
                Log.i(TAG, "Reconnect invoked role=trainer address=${device.address}")
                trainer.connect(device) // Existing client closes/replaces its own failed session.
            }
        }
    }

    private fun reconnectHeartRate(failure: GattFailure?) {
        val device = hrWanted ?: return
        if (hrRetry?.isActive == true || !hrBudget.take(rideState.value.phase == RidePhase.ACTIVE, failure)) return
        Log.w(TAG, "Reconnect scheduled role=heartRate address=${device.address} delayMs=3000 attempt=1/1")
        hrRetry = scope.launch {
            delay(3_000)
            if (rideState.value.phase == RidePhase.ACTIVE && hrWanted == device && !heartRate.state.value.isActive) {
                Log.i(TAG, "Reconnect invoked role=heartRate address=${device.address}")
                heartRate.connect(device)
            }
        }
    }

    private fun createChannel() {
        val localized = AppLocale.activityContext(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, localized.getString(R.string.ride_channel), NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(startedAt: Long): Notification {
        val localized = AppLocale.activityContext(this)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val end = PendingIntent.getService(this, 1, Intent(this, RideRecordingService::class.java).setAction(END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(localized, CHANNEL)
            .setSmallIcon(R.drawable.ic_ride_notification)
            .setContentTitle(localized.getString(R.string.ride_active))
            .setContentText(localized.getString(R.string.ride_notification_text))
            .setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - startedAt))
            .setUsesChronometer(true).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, localized.getString(R.string.ride_end), end).build()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlayState.value == RideOverlayState.VISIBLE) { overlayWindow?.hide(); overlayWindow = null }
        if (rideState.value.phase == RidePhase.ACTIVE) {
            createChannel()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(rideState.value.startedAtMs!!))
        }
    }

    override fun onUnbind(intent: Intent): Boolean { uiHidden(); return true }
    override fun onTaskRemoved(rootIntent: Intent?) { uiHidden(); super.onTaskRemoved(rootIntent) }

    private fun releaseWakeLock() { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null }

    override fun onDestroy() {
        setOverlay(false); overlayWindow = null
        destroyed = true
        Log.i(TAG, "Service destroyed phase=${rideState.value.phase}")
        scope.cancel()
        // Best effort for detectable destruction. Process death is handled by DB recovery next launch.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { recorder.abort() } catch (error: Exception) { Log.e(TAG, "Recording destruction abort failed", error) }
        }
        session.end()
        trainer.close(); heartRate.close()
        releaseWakeLock()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "RideLabRide"
        const val CHANNEL = "active_ride"
        const val NOTIFICATION_ID = 601
        const val START = "com.isaac.ridelab.START_RIDE"
        const val END = "com.isaac.ridelab.END_RIDE"
    }
}

