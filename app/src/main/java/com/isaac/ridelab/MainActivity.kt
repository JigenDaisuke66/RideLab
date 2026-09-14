package com.isaac.ridelab

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.isaac.ridelab.ble.BleScanner
import com.isaac.ridelab.ui.screen.DevicesScreen
import com.isaac.ridelab.ui.theme.RideLabTheme

class MainActivity : ComponentActivity() {
    private lateinit var scanner: BleScanner
    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (scanner.refreshAvailability() && lifecycle.currentState.isAtLeast(
                androidx.lifecycle.Lifecycle.State.STARTED)) scanner.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanner = BleScanner(applicationContext)
        enableEdgeToEdge()
        setContent {
            val state by scanner.state.collectAsState()
            RideLabTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DevicesScreen(
                        state = state,
                        onStart = {
                            if (scanner.hasPermissions()) scanner.start()
                            else permissions.launch(arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT,
                            ))
                        },
                        onStop = scanner::stop,
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
        scanner.refreshAvailability()
    }

    override fun onStop() {
        scanner.stop()
        super.onStop()
    }

    override fun onDestroy() {
        scanner.close()
        super.onDestroy()
    }
}
