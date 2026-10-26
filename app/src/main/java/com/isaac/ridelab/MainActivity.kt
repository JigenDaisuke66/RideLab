package com.isaac.ridelab

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import com.isaac.ridelab.ui.screen.HistoryViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.isaac.ridelab.ui.screen.CsvExportState
import com.isaac.ridelab.export.RideCsvExporter
import com.isaac.ridelab.data.model.DiscoveredDevice
import com.isaac.ridelab.locale.AppLocale
import com.isaac.ridelab.ui.screen.DevicesViewModel
import com.isaac.ridelab.ui.theme.RideLabTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.activityContext(newBase))
    }

    private lateinit var history: HistoryViewModel
    private var pendingCsvRide: Long? = null
    private val csvDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val id = pendingCsvRide
        pendingCsvRide = null
        if (uri != null && id != null) {
            history.exportCsv(id, uri, resources.getStringArray(R.array.ride_csv_headers).toList())
        }
    }
    private lateinit var devices: DevicesViewModel
    private var notificationStartPending = false
    private var notificationWorkout: com.isaac.ridelab.workout.WorkoutId? = null
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        val requested = notificationStartPending
        val workout = notificationWorkout
        notificationStartPending = false; notificationWorkout = null
        if (requested && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            if (workout == null) devices.startRide() else devices.startWorkout(workout)
        }
    }
    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val selected = devices.pendingConnection
        val heartRateSelected = devices.pendingHeartRateConnection
        devices.pendingConnection = null
        devices.pendingHeartRateConnection = null
        val canScan = devices.scanner.refreshAvailability()
        Log.i("RideLabGatt", "Permission result address=${selected?.address} permissions=$it")
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            // Retry the exact selection even on denial, so the client publishes a visible failure.
            if (heartRateSelected != null) devices.connectHeartRate(heartRateSelected)
            else if (selected != null) devices.connect(selected) else if (canScan) devices.scanner.start()
        } else {
            Log.i("RideLabGatt", "Permission result received outside foreground; connection not started")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        devices = ViewModelProvider(this)[DevicesViewModel::class.java]
        notificationStartPending = savedInstanceState?.getBoolean("notification_start_pending") ?: false
        notificationWorkout = com.isaac.ridelab.workout.WorkoutId.entries.firstOrNull {
            it.name == savedInstanceState?.getString("notification_workout")
        }
        val ftp = ViewModelProvider(this)[com.isaac.ridelab.ui.screen.FtpViewModel::class.java]
        val erg = ViewModelProvider(this)[com.isaac.ridelab.ui.screen.ErgTargetViewModel::class.java]
        history = ViewModelProvider(this)[HistoryViewModel::class.java]
        pendingCsvRide = savedInstanceState?.takeIf { it.containsKey("csv_ride_id") }?.getLong("csv_ride_id")
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                history.csvExport.collect { status ->
                    val message = when (status) {
                        CsvExportState.SUCCESS -> R.string.csv_export_success
                        CsvExportState.FAILED -> R.string.csv_export_failed
                        else -> null
                    }
                    if (message != null) {
                        android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                        history.acknowledgeExport()
                    }
                }
            }
        }
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(9, 13, 18)))
        val appearanceModel = ViewModelProvider(this)[com.isaac.ridelab.ui.settings.AppearanceViewModel::class.java]
        val aiModel = ViewModelProvider(this)[com.isaac.ridelab.ui.ai.AiSettingsViewModel::class.java]
        val aiAnalysis = ViewModelProvider(this)[com.isaac.ridelab.ui.ai.AiAnalysisViewModel::class.java]
        setContent {
            RideLabTheme {
                com.isaac.ridelab.ui.screen.AppShell(devices, history, ftp, erg,
                    ::onDeviceSelected, ::onHeartRateSelected, ::onStartRide,
                    scan = {
                        devices.pendingConnection = null
                        devices.pendingHeartRateConnection = null
                        if (devices.scanner.hasPermissions()) devices.scanner.start() else requestBluetoothPermissions()
                    },
                    appSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) },
                    bluetoothSettings = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    language = ::selectLanguage, enableOverlay = ::enableOverlay, exportCsv = ::exportCsv,
                    startWorkout = ::onStartWorkout, appearanceModel = appearanceModel,
                    aiContent = { close ->
                        com.isaac.ridelab.ui.ai.AiAnalysisRoute(aiAnalysis, close) {
                            com.isaac.ridelab.ui.ai.AiSettingsRoute(aiModel)
                        }
                    })
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingCsvRide?.let { outState.putLong("csv_ride_id", it) }
        outState.putBoolean("notification_start_pending", notificationStartPending)
        outState.putString("notification_workout", notificationWorkout?.name)
        super.onSaveInstanceState(outState)
    }

    private fun exportCsv(id: Long, startEpochMillis: Long) {
        if (pendingCsvRide != null || history.csvExport.value == CsvExportState.WRITING) return
        pendingCsvRide = id
        try {
            csvDocument.launch(RideCsvExporter.filename(startEpochMillis))
        } catch (error: Exception) {
            pendingCsvRide = null
            history.exportFailed(error)
        }
    }

    override fun onResume() {
        super.onResume()
        devices.scanner.refreshAvailability()
    }

    override fun onStart() {
        super.onStart()
        devices.enterForeground()
    }

    override fun onStop() {
        if (!isChangingConfigurations) devices.leaveForeground()
        super.onStop()
    }

    private fun requestBluetoothPermissions() {
        permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
    }

    internal fun onDeviceSelected(selected: DiscoveredDevice) {
        Log.i("RideLabGatt", "Device tapped address=${selected.address} name=${selected.name}")
        devices.pendingConnection = null
        devices.pendingHeartRateConnection = null
        // The client immediately publishes selection + CONNECTING (or a typed permission failure).
        devices.connect(selected)
        if (!devices.scanner.hasPermissions()) {
            devices.pendingConnection = selected
            Log.i("RideLabGatt", "Requesting Bluetooth permission address=${selected.address}")
            requestBluetoothPermissions()
        }
    }

    internal fun onHeartRateSelected(selected: DiscoveredDevice) {
        Log.i("RideLabHeartRate", "Device tapped address=${selected.address} name=${selected.name}")
        devices.pendingConnection = null
        devices.pendingHeartRateConnection = null
        devices.connectHeartRate(selected)
        if (!devices.scanner.hasPermissions()) {
            devices.pendingHeartRateConnection = selected
            requestBluetoothPermissions()
        }
    }

    private fun onStartRide() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationStartPending = true; notificationWorkout = null
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else devices.startRide()
    }

    private fun onStartWorkout(id: com.isaac.ridelab.workout.WorkoutId) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationStartPending = true; notificationWorkout = id
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else devices.startWorkout(id)
    }

    private fun enableOverlay() {
        if (Settings.canDrawOverlays(this)) {
            devices.setOverlay(true)
        } else {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: android.content.ActivityNotFoundException) {
                Log.e("RideLabOverlay", "Overlay permission settings unavailable", e)
                android.widget.Toast.makeText(this, R.string.overlay_failure, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun selectLanguage(language: String) {
        val model = ViewModelProvider(this)[com.isaac.ridelab.ui.settings.AppearanceViewModel::class.java]
        if (model.selectLanguage(com.isaac.ridelab.locale.AppLanguage.fromTag(language))) recreate()
    }
}
