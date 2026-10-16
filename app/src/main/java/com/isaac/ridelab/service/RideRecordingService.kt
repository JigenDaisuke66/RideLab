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
import com.isaac.ridelab.workout.*
import com.isaac.ridelab.ble.fec.ThinkRiderX2MaxProfile
import com.isaac.ridelab.ble.control.ControlOwner
import com.isaac.ridelab.ble.control.ControlStatus

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
    private val workout: WorkoutController = WorkoutController(ThinkRiderX2MaxProfile) { watts ->
        if (!trainerControlReady) false else {
            Log.i("RideLabWorkout", "Target request watts=$watts step=${workoutState.value.stepIndex}")
            trainer.setTargetPower(watts, ControlOwner.WORKOUT, workoutState.value.controlsTrainer)
        }
    }
    val workoutState get() = workout.state
    private val trainerControlReady: Boolean get() = trainer.state.value.connection in
        setOf(ConnectionState.CONNECTED, ConnectionState.SUBSCRIBING, ConnectionState.STREAMING) && trainer.state.value.control.canWorkout
    private val rampReview get() = (application as RideLabApplication).rampReview
    private var workoutPreparing: Job? = null
    private var workoutJob: Job? = null
    private var pendingWorkout: WorkoutDefinition? = null
    private var startup: Job? = null
    private var recording: Job? = null
    private var finalizing: Job? = null
    private var pauseCommit: Job? = null
    private var retainingConnections = false
    private fun rideClock(now: Long = SystemClock.elapsedRealtime()) = rideState.value.recordingClock(now)
    private lateinit var recovery: Deferred<Unit>
    private val mutableOverlay = MutableStateFlow(RideOverlayState.OFF)
    val overlayState = mutableOverlay.asStateFlow()
    private var overlayJob: Job? = null
    private var overlayWindow: RideOverlayWindow? = null

    fun setOverlay(enabled: Boolean) {
        overlayJob?.cancel(); overlayJob = null
        if (!enabled || !rideState.value.ongoing) {
            overlayWindow?.hide(); mutableOverlay.value = RideOverlayState.OFF; return
        }
        overlayJob = scope.launch {
            try {
                while (isActive && rideState.value.ongoing) {
                    if (!Settings.canDrawOverlays(this@RideRecordingService)) {
                        overlayWindow?.hide(); mutableOverlay.value = RideOverlayState.PERMISSION_REQUIRED; break
                    }
                    if (overlayWindow == null) overlayWindow = RideOverlayWindow(this@RideRecordingService) { setOverlay(false) }
                    val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
                    val interactive = getSystemService(PowerManager::class.java).isInteractive
                    if (locked || !interactive) overlayWindow?.hide()
                    else if (overlayWindow?.show(liveSnapshot(SystemClock.elapsedRealtime()),
                            keepScreenOn = rideState.value.phase == RidePhase.ACTIVE) != true) {
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
        scope.launch { rideState.map { it.phase }.distinctUntilChanged().collect { if (!rideState.value.ongoing) setOverlay(false) } }
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
            trainer.state.collect { value ->
                val before = workoutState.value.phase
                val now = rideClock()
                if (value.connection in setOf(ConnectionState.DISCONNECTED, ConnectionState.FAILED)) workout.unavailable(now)
                else if (value.control.status == ControlStatus.FAILED) workout.controlFailed(now)
                if (before == WorkoutPhase.ACTIVE && workoutState.value.phase == WorkoutPhase.PAUSED)
                    Log.w("RideLabWorkout", "Auto-pause issue=${workoutState.value.issue}; explicit resume required")
            }
        }
        scope.launch {
            trainer.state.map { it.connection to it.issue?.reason }.distinctUntilChanged().collect { (connection, failure) ->
                if (connection == ConnectionState.FAILED) reconnectTrainer(failure)
                stopRetainedPreviewIfDisconnected()
            }
        }
        scope.launch {
            heartRate.state.map { it.connection to it.issue?.reason }.distinctUntilChanged().collect { (connection, failure) ->
                if (connection == ConnectionState.FAILED) reconnectHeartRate(failure)
                stopRetainedPreviewIfDisconnected()
            }
        }
    }

    override fun onBind(intent: Intent) = binder

    fun connectTrainer(device: DiscoveredDevice) {
        if (trainer.state.value.isActive && trainer.state.value.device?.address == device.address) return
        workout.unavailable(rideClock())
        trainerRetry?.cancel(); trainerBudget.reset(); trainerWanted = device
        trainer.connect(device)
    }

    fun connectHeartRate(device: DiscoveredDevice) {
        if (heartRate.state.value.isActive && heartRate.state.value.device?.address == device.address) return
        hrRetry?.cancel(); hrBudget.reset(); hrWanted = device
        heartRate.connect(device)
    }

    fun disconnectTrainer() {
        workout.unavailable(rideClock())
        trainerWanted = null; trainerRetry?.cancel(); trainerRetry = null
        trainer.disconnect()
        stopRetainedPreviewIfDisconnected()
    }

    fun disconnectHeartRate() {
        hrWanted = null; hrRetry?.cancel(); hrRetry = null
        heartRate.disconnect()
        stopRetainedPreviewIfDisconnected()
    }

    fun setTargetPower(watts: Int) {
        if (workoutState.value.controlsTrainer || rideState.value.phase == RidePhase.STOPPING) {
            Log.w("RideLabWorkout", "Manual target blocked while workout owns trainer"); return
        }
        trainer.setTargetPower(watts, ControlOwner.MANUAL, workoutState.value.controlsTrainer)
    }

    fun requestWorkout(id: WorkoutId) {
        if (rideState.value.phase !in setOf(RidePhase.IDLE, RidePhase.ERROR) || workoutPreparing?.isActive == true ||
            rampReview.state.value?.phase == RampReviewPhase.APPLYING) return
        val definition = BuiltInWorkouts.find(id)
        if (!workout.prepare(definition)) return
        workoutPreparing = scope.launch {
            try {
                val ftp = (application as RideLabApplication).ftpSettings.read()
                if (definition.requiresFtp && ftp == null) { workout.fail(WorkoutIssue.FTP_REQUIRED); return@launch }
                if (!trainerControlReady) { workout.fail(WorkoutIssue.TRAINER_UNAVAILABLE); return@launch }
                rampReview.clear()
                pendingWorkout = definition
                beginRide()
                if (rideState.value.phase == RidePhase.ERROR) {
                    pendingWorkout = null; workout.fail(WorkoutIssue.RIDE_FAILED)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("RideLabWorkout", "Preparation failed", e)
                pendingWorkout = null; workout.fail(WorkoutIssue.RIDE_FAILED)
            }
        }
    }
    fun pauseRide() {
        val now = SystemClock.elapsedRealtime()
        if (!session.pause(now)) return
        workout.pause(rideClock(now))
        val epoch = System.currentTimeMillis()
        val clock = rideClock(now)
        pauseCommit = scope.launch {
            try { recorder.pause(epoch, clock) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { recordingFailure(e) }
        }
        updateNotification()
        Log.i(TAG, "Ride PAUSED elapsedMs=${rideState.value.elapsedMs(now)}; GATT retained, sampling suspended; ERG target unchanged")
    }

    fun resumeRide() {
        if (!session.resume(SystemClock.elapsedRealtime())) return
        if (workoutState.value.phase == WorkoutPhase.PAUSED) workout.resume(rideClock(), trainerControlReady)
        updateNotification()
        Log.i(TAG, "Ride ACTIVE resumed; same session and GATT connections")
    }

    fun pauseWorkout() { pauseRide() }
    fun resumeWorkout() {
        if (rideState.value.phase == RidePhase.PAUSED) resumeRide()
        else if (rideState.value.phase == RidePhase.ACTIVE) workout.resume(rideClock(), trainerControlReady)
        Log.i("RideLabWorkout", "Resume phase=${workoutState.value.phase} step=${workoutState.value.stepIndex}")
    }
    fun skipWorkout() {
        if (rideState.value.phase == RidePhase.ACTIVE) {
            workout.skip(rideClock(), trainerControlReady)
            if (workoutState.value.phase == WorkoutPhase.COMPLETED) endRide()
        }
    }
    fun endWorkout() { endRide() }

    fun uiHidden() {
        Log.i(TAG, "UI hidden phase=${rideState.value.phase} preserve=${session.keepsConnections}")
        session.uiHidden()
    }

    fun requestStart() {
        if (workoutState.value.controlsTrainer) return
        workout.reset(); pendingWorkout = null
        beginRide()
    }
    private fun beginRide() {
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
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            // BLE callbacks/reconnect timers must be processed with the display off.
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RideLab:ActiveRide").apply { acquire() }
            retainingConnections = false
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
                    updateNotification()
                    Log.i(TAG, "Ride ACTIVE sessionId=$id startedAtElapsedMs=$start trainer=${trainerDevice?.address} hr=${hrDevice?.address}")
                    pendingWorkout?.let { definition ->
                        pendingWorkout = null
                        val snapshot = repository.analyticsSession(id)?.analyticsFtpWatts
                        if (!workout.start(snapshot, SystemClock.elapsedRealtime(), start)) {
                            Log.e("RideLabWorkout", "Workout refused phase=${workoutState.value.phase} issue=${workoutState.value.issue}")
                            endRide(); return@launch
                        }
                        Log.i("RideLabWorkout", "Started id=${definition.id} session=$id ftpSnapshot=$snapshot")
                        startWorkoutLoop()
                    }
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
            if (workoutState.value.controlsTrainer) workout.fail(WorkoutIssue.RIDE_FAILED)
            stopForeground(STOP_FOREGROUND_REMOVE)
            releaseWakeLock()
            stopSelf()
        }
        // A killed process is recovered as ABORTED; never restart or replay control automatically.
        return START_NOT_STICKY
    }

    fun endRide() {
        val endedAt = SystemClock.elapsedRealtime()
        val recordingEnd = rideClock(endedAt)
        val wasPaused = rideState.value.phase == RidePhase.PAUSED
        if (!session.beginStop()) return
        Log.i(TAG, "Ride STOPPING: explicit end")
        workout.finish(recordingEnd, testEnded = true)
        val isRamp = workoutState.value.definition?.rampTest == true
        val ranges = workout.effortRanges()
        val endEpoch = System.currentTimeMillis()
        val last = if (wasPaused) LiveRideData(targetPowerWatts = trainer.state.value.lastRequestedTarget) else liveSnapshot(endedAt)
        finalizing = scope.launch {
            try {
                startup?.join()
                workoutJob?.cancelAndJoin()
                recording?.cancelAndJoin()
                pauseCommit?.join()
                recordingError?.let { throw it }
                val id = checkNotNull(recorder.complete(last, endEpoch, recordingEnd))
                Log.i(TAG, "Recording COMPLETED sessionId=$id endEpoch=$endEpoch")
                if (isRamp) {
                    try {
                        val rows = repository.historySamples(id)
                        val result = withContext(Dispatchers.Default) { RampTestCalculator.calculate(id, rows, ranges) }
                        rampReview.publish(result)
                        Log.i("RideLabWorkout", "Ramp result session=$id best60=${result.best60Watts} estimatedFtp=${result.estimatedFtp}; awaiting explicit acceptance")
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        Log.e("RideLabWorkout", "Ramp analysis failed; completed ride preserved", e)
                        workout.fail(WorkoutIssue.RESULT_FAILED)
                    }
                }
                session.end() // End recording only; retain the same BLE sessions for the next ride.
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e(TAG, "Recording completion failed", error)
                session.fail(RideError.RECORDING_FAILED)
            } finally { if (!destroyed) stopRideWork() }
        }
    }

    private fun stopRideWork() {
        releaseWakeLock()
        trainerRetry?.cancel(); hrRetry?.cancel()
        if (rideState.value.phase == RidePhase.IDLE && (trainer.state.value.isActive || heartRate.state.value.isActive)) {
            retainingConnections = true
            updateNotification()
            Log.i(TAG, "Recording stopped; existing GATT retained in connectedDevice foreground service")
        } else {
            retainingConnections = false
            session.releasePreview()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopRetainedPreviewIfDisconnected() {
        if (!retainingConnections || trainer.state.value.isActive || heartRate.state.value.isActive) return
        retainingConnections = false
        session.releasePreview()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "No retained devices; foreground service stopped")
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,
            notification())
    }

    private fun liveSnapshot(nowMs: Long) = LiveRideData.snapshot(trainer.state.value, heartRate.state.value, nowMs)

    private fun startRecordingLoop(start: Long, id: Long) {
        recording = scope.launch {
            try {
                while (isActive && rideState.value.ongoing) {
                    val now = SystemClock.elapsedRealtime()
                    if (rideState.value.phase == RidePhase.ACTIVE) {
                        pauseCommit?.join()
                        if (rideState.value.phase == RidePhase.ACTIVE)
                            recorder.sample(liveSnapshot(now), System.currentTimeMillis(), rideClock(now))
                    }
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

    private fun startWorkoutLoop() {
        workoutJob?.cancel()
        workoutJob = scope.launch {
            try { while (isActive && rideState.value.ongoing) {
                val oldIndex = workoutState.value.stepIndex
                if (rideState.value.phase == RidePhase.ACTIVE) workout.tick(rideClock())
                if (oldIndex != workoutState.value.stepIndex)
                    Log.i("RideLabWorkout", "Step index=${workoutState.value.stepIndex} target=${workoutState.value.targetWatts}")
                if (workoutState.value.phase == WorkoutPhase.COMPLETED) { endRide(); break }
                delay(250)
            } } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("RideLabWorkout", "Scheduler failed; stopping and preserving ride", e)
                workout.finish(rideClock())
                workout.fail(WorkoutIssue.CONTROL_FAILED)
                endRide()
            }
        }
    }

    private suspend fun recordingFailure(error: Exception) {
        workout.finish(rideClock())
        if (workoutState.value.definition != null) workout.fail(WorkoutIssue.RIDE_FAILED)
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
        if (trainerRetry?.isActive == true || !trainerBudget.take(rideState.value.ongoing, failure)) return
        Log.w(TAG, "Reconnect scheduled role=trainer address=${device.address} delayMs=3000 attempt=1/1")
        trainerRetry = scope.launch {
            delay(3_000)
            if (rideState.value.ongoing && trainerWanted == device && !trainer.state.value.isActive) {
                Log.i(TAG, "Reconnect invoked role=trainer address=${device.address}")
                trainer.connect(device, trainer.state.value.capabilities.protocol) // Keep protocol pinned across automatic recovery.
            }
        }
    }

    private fun reconnectHeartRate(failure: GattFailure?) {
        val device = hrWanted ?: return
        if (hrRetry?.isActive == true || !hrBudget.take(rideState.value.ongoing, failure)) return
        Log.w(TAG, "Reconnect scheduled role=heartRate address=${device.address} delayMs=3000 attempt=1/1")
        hrRetry = scope.launch {
            delay(3_000)
            if (rideState.value.ongoing && hrWanted == device && !heartRate.state.value.isActive) {
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

    private fun notification(): Notification {
        val localized = AppLocale.activityContext(this)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val end = PendingIntent.getService(this, 1, Intent(this, RideRecordingService::class.java).setAction(END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(localized, CHANNEL)
            .setSmallIcon(R.drawable.ic_ride_notification)
            .setContentTitle(localized.getString(when (rideState.value.phase) {
                RidePhase.PAUSED -> R.string.ride_paused
                RidePhase.IDLE -> R.string.ride_devices_retained
                else -> R.string.ride_active
            }))
            .setContentText(localized.getString(if (rideState.value.phase == RidePhase.IDLE)
                R.string.ride_devices_retained_hint else R.string.ride_notification_text))
            .setWhen(System.currentTimeMillis() - rideState.value.elapsedMs(SystemClock.elapsedRealtime()))
            .setUsesChronometer(rideState.value.phase in setOf(RidePhase.STARTING, RidePhase.ACTIVE))
            .setShowWhen(rideState.value.phase == RidePhase.ACTIVE)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(open)
            // Workout cancellation must pass through the UI confirmation, not a notification tap.
            .addAction(0, localized.getString(if (workoutState.value.controlsTrainer || retainingConnections) R.string.workout_open else R.string.ride_end),
                if (workoutState.value.controlsTrainer || retainingConnections) open else end).build()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlayState.value == RideOverlayState.VISIBLE) { overlayWindow?.hide(); overlayWindow = null }
        if (rideState.value.ongoing || retainingConnections) {
            createChannel()
            updateNotification()
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

