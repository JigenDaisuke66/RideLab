package com.isaac.ridelab

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.ble.BleScanState
import com.isaac.ridelab.ble.BleUuids
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.ui.screen.DevicesScreen
import com.isaac.ridelab.ui.theme.RideLabTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DevicesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val device = DiscoveredDevice("Think X211-0013186", "00:11:22:33:44:55", -50,
        setOf(BleUuids.CYCLING_POWER), setOf(DeviceType.POWER_METER))
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun tappingDeviceNameConnectsThatDeviceWithoutUsingTheButton() {
        var selected: DiscoveredDevice? = null
        compose.setContent {
            RideLabTheme {
                DevicesScreen(BleScanState(devices = listOf(device)), GattDeviceState(),
                    onConnect = { selected = it }, onDisconnect = {}, onStart = {}, onStop = {},
                    onSettings = {}, onBluetoothSettings = {})
            }
        }
        compose.onNodeWithText(device.name!!, useUnmergedTree = true).performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals(device, selected) }
    }

    @Test fun lowerCardTapKeepsExactSelectionAndStatusVisibleAndAllowsRetry() {
        // Duplicate names ensure selection is by address, not name or a changing list index.
        val devices = (0..12).map { device.copy(address = "00:11:22:33:44:%02X".format(it)) }
        val target = devices.last()
        var connection by mutableStateOf(GattDeviceState())
        var taps = 0
        compose.setContent {
            RideLabTheme {
                DevicesScreen(BleScanState(devices = devices), connection,
                    onConnect = {
                        taps++
                        connection = GattDeviceState(device = it, connection = ConnectionState.CONNECTING)
                    }, onDisconnect = {}, onStart = {}, onStop = {}, onSettings = {}, onBluetoothSettings = {})
            }
        }
        val cardTag = "device_card_${target.address}"
        compose.onNodeWithTag("device_list").performScrollToNode(hasTestTag(cardTag))
        // Card background, outside the separate connect button.
        compose.onNodeWithTag(cardTag).performTouchInput { click(Offset(width - 8f, 8f)) }
        compose.runOnIdle { assertEquals(target, connection.device); assertEquals(1, taps) }
        compose.onNodeWithTag(cardTag).assertIsSelected()
        compose.onNodeWithTag("connection_summary").assertIsDisplayed()
        compose.onNodeWithTag("connection_status").assertTextEquals(context.getString(R.string.gatt_connecting)).assertIsDisplayed()

        listOf(ConnectionState.DISCOVERING to R.string.gatt_discovering,
            ConnectionState.CONNECTED to R.string.gatt_connected,
            ConnectionState.FAILED to R.string.gatt_failed).forEach { (stage, label) ->
            compose.runOnIdle { connection = connection.copy(connection = stage) }
            compose.onNodeWithTag("connection_status").assertTextEquals(context.getString(label)).assertIsDisplayed()
        }
        compose.onNodeWithTag("device_list").performScrollToNode(hasTestTag(cardTag))
        compose.onNodeWithTag(cardTag).performTouchInput { click(Offset(width - 8f, 8f)) }
        compose.runOnIdle { assertEquals(2, taps); assertEquals(target, connection.device) }
    }

    @Test fun deviceWithoutAdvertisedFtmsCanBeSelectedForConnection() {
        var selected: DiscoveredDevice? = null
        compose.setContent {
            RideLabTheme {
                DevicesScreen(BleScanState(devices = listOf(device)), GattDeviceState(),
                    onConnect = { selected = it }, onDisconnect = {}, onStart = {}, onStop = {},
                    onSettings = {}, onBluetoothSettings = {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.connect_device)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(device, selected) }
    }

    @Test fun discoveredCyclingPowerWithoutFtmsShowsExplicitExplanation() {
        val connected = GattDeviceState(device = device, connection = ConnectionState.CONNECTED,
            discoveryComplete = true, services = listOf(GattServiceInfo(BleUuids.CYCLING_POWER, 1, emptyList())))
        compose.setContent {
            RideLabTheme {
                DevicesScreen(BleScanState(), connected, onConnect = {}, onDisconnect = {},
                    onStart = {}, onStop = {}, onSettings = {}, onBluetoothSettings = {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.ftms_absent)).assertExists()
        compose.onNodeWithText(context.getString(R.string.cycling_power_detection,
            context.getString(R.string.service_found))).assertExists()
    }

    @Test fun activityLaunchAndRecreationKeepOwnersValid() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { assertEquals("com.isaac.ridelab", it.packageName) }
        }
    }
}
