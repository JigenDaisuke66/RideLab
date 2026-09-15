package com.isaac.ridelab

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.ble.BleScanState
import com.isaac.ridelab.ble.BleUuids
import com.isaac.ridelab.data.model.DeviceType
import com.isaac.ridelab.data.model.DiscoveredDevice
import com.isaac.ridelab.ui.screen.DevicesScreen
import com.isaac.ridelab.ui.screen.DevicesViewModel
import com.isaac.ridelab.ui.theme.RideLabTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.UUID

class GattConnectionPathTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @SuppressLint("MissingPermission") // Test grants runtime permissions before using the adapter.
    @Test fun cardTapReachesRealConnectGattForSelectedAddress() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.BLUETOOTH_CONNECT)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.BLUETOOTH_SCAN)
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        assertNotNull("This integration test requires a BLE-capable test device", adapter)
        assertTrue("Enable Bluetooth on the test device before running this integration test", adapter.isEnabled)

        val address = "02:" + UUID.randomUUID().toString().replace("-", "").take(10)
            .chunked(2).joinToString(":").uppercase(Locale.ROOT)
        val selected = DiscoveredDevice("Tap path test", address, -50,
            setOf(BleUuids.CYCLING_POWER), setOf(DeviceType.POWER_METER))
        lateinit var viewModel: DevicesViewModel
        compose.activityRule.scenario.onActivity { activity ->
            viewModel = ViewModelProvider(activity)[DevicesViewModel::class.java]
            // Only the scan-result fixture is supplied by the test. All event handlers,
            // ViewModel, client and BluetoothDevice.connectGatt are the production path.
            activity.setContent {
                val connection by viewModel.client.state.collectAsState()
                RideLabTheme {
                    DevicesScreen(BleScanState(devices = listOf(selected)), connection,
                        onConnect = activity::onDeviceSelected, onDisconnect = viewModel.client::disconnect,
                        onStart = {}, onStop = {}, onSettings = {}, onBluetoothSettings = {})
                }
            }
        }
        try {
            compose.onNodeWithText(selected.name!!, useUnmergedTree = true)
                .performScrollTo().performTouchInput { click() }
            compose.runOnIdle { assertEquals(selected, viewModel.client.state.value.device) }
            compose.onNodeWithTag("connection_status").assertIsDisplayed()
            val log = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
                "logcat -d --pid=${Process.myPid()} -s RideLabGatt:I",
            )).bufferedReader().use { it.readText() }
            val tapped = log.indexOf("Device tapped address=$address")
            val dispatched = log.indexOf("ViewModel connect address=$address", tapped)
            val invoked = log.indexOf("connectGatt invoked address=$address", dispatched)
            val returned = log.indexOf("connectGatt returned address=$address hasGatt=true", invoked)
            assertTrue("Missing device-tap log\n$log", tapped >= 0)
            assertTrue("Missing ViewModel dispatch\n$log", dispatched > tapped)
            assertTrue("Native connectGatt was not invoked for the tapped address\n$log", invoked > dispatched)
            assertTrue("Native connectGatt did not return a GATT instance\n$log", returned > invoked)
        } finally {
            compose.runOnIdle { viewModel.client.disconnect() }
        }
    }
}
