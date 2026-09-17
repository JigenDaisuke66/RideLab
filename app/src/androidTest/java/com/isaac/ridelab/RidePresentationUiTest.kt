package com.isaac.ridelab

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.runtime.*
import com.isaac.ridelab.ui.screen.ErgTargetUi
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.ble.BleScanState
import com.isaac.ridelab.ble.fec.*
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.ui.screen.DevicePicker
import com.isaac.ridelab.ui.screen.ErgControls
import com.isaac.ridelab.ui.theme.RideLabTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Compile-only here; run from Android Studio on a device to verify presentation callbacks. */
class RidePresentationUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun ergAdjustmentDoesNotSendUntilApply() {
        val sent = mutableListOf<Int>()
        val trainer = GattDeviceState(connection = ConnectionState.STREAMING,
            fec = FecState(FecCapability(true, 0x10, 0x0c, true), FecStatus.READY))
        compose.setContent { var pending by remember { mutableStateOf(ErgTargetUi(loaded = true)) }; RideLabTheme { ErgControls(trainer, pending, { pending = pending.copy(target = it) }) { sent.add(it) } } }
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
        compose.onNodeWithContentDescription(context.getString(R.string.fec_plus)).performClick()
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
        compose.onNodeWithText(context.getString(R.string.ui_apply_target)).performClick()
        compose.runOnIdle { assertEquals(listOf(105), sent) }
    }

    @Test fun restoredAndConfirmedTargetsNeverSendWithoutApply() {
        val sent = mutableListOf<Int>()
        var pending by mutableStateOf(ErgTargetUi(target = 135, loaded = true))
        val trainer = GattDeviceState(connection = ConnectionState.STREAMING,
            fec = FecState(FecCapability(true, 0x10, 0x0c, true), FecStatus.READY))
        compose.setContent { RideLabTheme { ErgControls(trainer, pending, { pending = pending.copy(target = it) }) { sent.add(it) } } }
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
        compose.onNodeWithText(context.getString(R.string.power_value, 135)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("150")
        compose.onNodeWithText(context.getString(R.string.erg_cancel)).performClick()
        compose.runOnIdle { assertEquals(135, pending.target); assertTrue(sent.isEmpty()) }
        compose.onNodeWithText(context.getString(R.string.power_value, 135)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("145")
        compose.onNodeWithText(context.getString(R.string.erg_confirm)).performClick()
        compose.runOnIdle { assertEquals(145, pending.target); assertTrue(sent.isEmpty()) }
        compose.onNodeWithText(context.getString(R.string.ui_apply_target)).performClick()
        compose.runOnIdle { assertEquals(listOf(145), sent) }
    }
    @Test fun cleanPickerPreservesExactDeviceSelection() {
        val device = DiscoveredDevice("Sensor", "00:11:22:33:44:55", -50, emptySet(), setOf(DeviceType.HEART_RATE_SENSOR))
        var selected: DiscoveredDevice? = null
        compose.setContent { RideLabTheme {
            DevicePicker(R.string.ui_heart_rate, BleScanState(devices = listOf(device)), null, false,
                { selected = it }, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Sensor").performClick()
        compose.runOnIdle { assertSame(device, selected) }
    }
}
