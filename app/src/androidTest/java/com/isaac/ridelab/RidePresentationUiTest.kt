package com.isaac.ridelab

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import com.isaac.ridelab.ble.control.*
import com.isaac.ridelab.ble.ftms.FtmsControlState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isaac.ridelab.ui.screen.RideControls
import com.isaac.ridelab.ui.screen.RideScreen
import com.isaac.ridelab.service.RideState
import com.isaac.ridelab.service.RidePhase
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

    @Test fun stalePreferenceProfileCannotApplyDuringCapabilityChange() {
        val trainer = GattDeviceState(connection = ConnectionState.STREAMING,
            capabilities = TrainerCapabilities(ControlProtocol.FTMS, TrainerErgProfile(50, 500)),
            ftmsControl = FtmsControlState(status = ControlStatus.READY))
        compose.setContent { RideLabTheme {
            ErgControls(trainer, ErgTargetUi(loaded = true, profile = ThinkRiderX2MaxProfile)) { fail("Stale profile") }
        } }
        compose.onNodeWithText(context.getString(R.string.ui_apply_target)).assertIsNotEnabled()
    }

    @Test fun measurementOnlyKeepsErgDisabled() {
        compose.setContent { RideLabTheme { ErgControls(GattDeviceState(connection = ConnectionState.STREAMING)) {} } }
        compose.onNodeWithText(context.getString(R.string.ui_apply_target)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.control_measurement_only)).assertExists()
    }

    @Test fun ftmsApplyUsesGenericCallbackAndAcceptanceWaitsForProtocolState() {
        val sent = mutableListOf<Int>()
        var trainer by mutableStateOf(GattDeviceState(connection = ConnectionState.STREAMING,
            capabilities = TrainerCapabilities(ControlProtocol.FTMS, TrainerErgProfile(50, 500)),
            ftmsControl = FtmsControlState(status = ControlStatus.READY)))
        compose.setContent { RideLabTheme { ErgControls(trainer) { sent.add(it) } } }
        compose.onNodeWithText(context.getString(R.string.control_accepted)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.ui_apply_target)).performClick()
        compose.runOnIdle {
            assertEquals(listOf(100), sent)
            trainer = trainer.copy(ftmsControl = FtmsControlState(status = ControlStatus.WRITING, requestedWatts = 100))
        }
        compose.onNodeWithText(context.getString(R.string.ui_apply_target)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.control_accepted)).assertDoesNotExist()
        compose.runOnIdle {
            trainer = trainer.copy(ftmsControl = FtmsControlState(status = ControlStatus.ACCEPTED, authorized = true, requestedWatts = 100, acceptedWatts = 100))
        }
        compose.onNodeWithText(context.getString(R.string.control_accepted)).assertExists()
    }

    @Test fun pauseResumeAndEndAreSeparateActions() {
        var paused = 0; var resumed = 0; var ended = 0
        var state by mutableStateOf(RideState(phase = RidePhase.ACTIVE, startedAtMs = 0))
        compose.setContent { RideLabTheme {
            RideControls(state, true, {}, { ended++ }, {}, showElapsed = false,
                pause = { paused++; state = state.copy(phase = RidePhase.PAUSED, pausedAtMs = 1000) },
                resume = { resumed++; state = state.copy(phase = RidePhase.ACTIVE, pausedAtMs = null) })
        } }
        compose.onNodeWithText(context.getString(R.string.ride_pause)).performClick()
        compose.onNodeWithText(context.getString(R.string.ride_resume)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.ride_end)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, paused); assertEquals(0, ended) }
        compose.onNodeWithText(context.getString(R.string.ride_resume)).performClick()
        compose.runOnIdle { assertEquals(1, resumed); assertEquals(0, ended) }
    }

    @Test fun endRideRequiresConfirmationAndCancelKeepsRideActive() {
        var ended = 0
        compose.setContent { RideLabTheme {
            RideControls(RideState(phase = RidePhase.ACTIVE), true, {}, { ended++ }, {}, showElapsed = false)
        } }
        val end = context.getString(R.string.ride_end)
        compose.onNodeWithText(end).performClick()
        compose.runOnIdle { assertEquals(0, ended) }
        compose.onNodeWithText(context.getString(R.string.erg_cancel)).performClick()
        compose.runOnIdle { assertEquals(0, ended) }
        compose.onNodeWithText(end).performClick()
        compose.onAllNodesWithText(end).filter(hasClickAction() and hasAnyAncestor(isDialog())).onFirst().performClick()
        compose.runOnIdle { assertEquals(1, ended) }
    }

    @Test fun smallRideViewportKeepsRideActionVisible() {
        compose.setContent { RideLabTheme {
            Box(Modifier.requiredSize(360.dp, 640.dp)) {
                RideScreen(GattDeviceState(), HeartRateState(), RideState(), true, {}, {}, {}, {})
            }
        } }
        compose.onNodeWithText(context.getString(R.string.ride_start)).assertIsDisplayed()
    }

    @Test fun ergAdjustmentDoesNotSendUntilApply() {
        val sent = mutableListOf<Int>()
        val trainer = GattDeviceState(connection = ConnectionState.STREAMING,
            fec = FecState(FecCapability(true, 0x10, 0x0c, true), FecStatus.READY),
            capabilities = com.isaac.ridelab.ble.control.TrainerCapabilities(
                com.isaac.ridelab.ble.control.ControlProtocol.FEC, ThinkRiderX2MaxProfile, automaticErg = true))
        compose.setContent { var pending by remember { mutableStateOf(ErgTargetUi(loaded = true, profile = ThinkRiderX2MaxProfile)) }; RideLabTheme { ErgControls(trainer, pending, { pending = pending.copy(target = it) }) { sent.add(it) } } }
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
        compose.onNodeWithContentDescription(context.getString(R.string.fec_plus)).performClick()
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
        compose.onNodeWithText(context.getString(R.string.ui_apply_target)).performClick()
        compose.runOnIdle { assertEquals(listOf(105), sent) }
    }

    @Test fun restoredAndConfirmedTargetsNeverSendWithoutApply() {
        val sent = mutableListOf<Int>()
        var pending by mutableStateOf(ErgTargetUi(target = 135, loaded = true, profile = ThinkRiderX2MaxProfile))
        val trainer = GattDeviceState(connection = ConnectionState.STREAMING,
            fec = FecState(FecCapability(true, 0x10, 0x0c, true), FecStatus.READY),
            capabilities = com.isaac.ridelab.ble.control.TrainerCapabilities(
                com.isaac.ridelab.ble.control.ControlProtocol.FEC, ThinkRiderX2MaxProfile, automaticErg = true))
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
