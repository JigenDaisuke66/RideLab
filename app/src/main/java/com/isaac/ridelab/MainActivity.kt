package com.isaac.ridelab

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import com.isaac.ridelab.data.model.DiscoveredDevice
import com.isaac.ridelab.data.model.GattFailure
import com.isaac.ridelab.locale.AppLocale
import com.isaac.ridelab.ui.screen.DevicesScreen
import com.isaac.ridelab.ui.screen.DevicesViewModel
import com.isaac.ridelab.ui.theme.RideLabTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.activityContext(newBase))
    }

    private lateinit var devices: DevicesViewModel
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
        enableEdgeToEdge()
        setContent {
            val state by devices.scanner.state.collectAsState()
            val connection by devices.client.state.collectAsState()
            val heartRate by devices.heartRateClient.state.collectAsState()
            RideLabTheme {
                Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
                    Row(Modifier.statusBarsPadding()) {
                        TextButton(onClick = { selectLanguage(AppLocale.CHINESE) }) {
                            Text(stringResource(R.string.language_chinese))
                        }
                        TextButton(onClick = { selectLanguage(AppLocale.ENGLISH) }) {
                            Text(stringResource(R.string.language_english))
                        }
                    }
                }) { innerPadding ->
                    DevicesScreen(
                        state = state,
                        connection = connection,
                        onConnect = ::onDeviceSelected,
                        onDisconnect = devices.client::disconnect,
                        onFecTarget = devices::sendFecTarget,
                        heartRate = heartRate,
                        onConnectHeartRate = ::onHeartRateSelected,
                        onDisconnectHeartRate = devices.heartRateClient::disconnect,
                        onStart = {
                            devices.pendingConnection = null
                            devices.pendingHeartRateConnection = null
                            if (devices.scanner.hasPermissions()) devices.scanner.start()
                            else requestBluetoothPermissions()
                        },
                        onStop = devices.scanner::stop,
                        onSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null))) },
                        onBluetoothSettings = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        devices.scanner.refreshAvailability()
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
        if (devices.client.state.value.issue?.reason == GattFailure.PERMISSION_REQUIRED) {
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
        if (devices.heartRateClient.state.value.issue?.reason == GattFailure.PERMISSION_REQUIRED) {
            devices.pendingHeartRateConnection = selected
            requestBluetoothPermissions()
        }
    }

    private fun selectLanguage(language: String) {
        if (AppLocale.select(this, language)) recreate()
    }
}
