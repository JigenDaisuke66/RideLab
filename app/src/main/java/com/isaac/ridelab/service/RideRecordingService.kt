package com.isaac.ridelab.service

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

    inner class LocalBinder : Binder() { val service get() = this@RideRecordingService }

    override fun onCreate() {
        super.onCreate()
        trainer = FtmsClient(this)
        heartRate = HeartRateClient(this)
        Log.i(TAG, "Service created; sole BLE owner")
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
            startForeground(NOTIFICATION_ID, notification(startedAt), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            // BLE callbacks/reconnect timers must be processed with the display off.
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RideLab:ActiveRide").apply { acquire() }
            trainerBudget.reset(); hrBudget.reset()
            session.activate(startedAt)
            Log.i(TAG, "Ride ACTIVE startedAtElapsedMs=$startedAt trainer=${trainer.state.value.device?.address} hr=${heartRate.state.value.device?.address}")
            if (trainer.state.value.connection == ConnectionState.FAILED) reconnectTrainer(trainer.state.value.issue?.reason)
            if (heartRate.state.value.connection == ConnectionState.FAILED) reconnectHeartRate(heartRate.state.value.issue?.reason)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Foreground promotion failed", error)
            session.fail(RideError.START_FAILED)
            stopForeground(STOP_FOREGROUND_REMOVE)
            releaseWakeLock()
            stopSelf()
        }
        // A killed process cannot restore an unrecorded ride or safely replay control commands.
        return START_NOT_STICKY
    }

    fun endRide() {
        Log.i(TAG, "Ride STOPPING: explicit end")
        session.end() // STOPPING before disconnect: collectors cannot schedule recovery.
        stopForeground(STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        stopSelf() // A visible UI may keep an idle bound instance for new device selection.
        Log.i(TAG, "Ride IDLE; GATT closed, foreground stopped")
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
        if (rideState.value.phase == RidePhase.ACTIVE) {
            createChannel()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(rideState.value.startedAtMs!!))
        }
    }

    override fun onUnbind(intent: Intent): Boolean { uiHidden(); return true }
    override fun onTaskRemoved(rootIntent: Intent?) { uiHidden(); super.onTaskRemoved(rootIntent) }

    private fun releaseWakeLock() { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed phase=${rideState.value.phase}")
        scope.cancel()
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

