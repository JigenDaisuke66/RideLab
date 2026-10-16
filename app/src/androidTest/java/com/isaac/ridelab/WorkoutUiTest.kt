package com.isaac.ridelab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.ui.screen.*
import com.isaac.ridelab.ui.theme.RideLabTheme
import com.isaac.ridelab.workout.*
import com.isaac.ridelab.service.*
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.ble.control.*
import com.isaac.ridelab.ble.fec.ThinkRiderX2MaxProfile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Compile-checked without generating an APK. Run manually on device from Android Studio. */
class WorkoutUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val verifiedControl = TrainerControlState(TrainerCapabilities(ControlProtocol.FEC, ThinkRiderX2MaxProfile, automaticErg = true), ControlStatus.READY)

    @Test fun initialFtpTestCanStartWithoutFtpOnlyAfterConfirmation() {
        val starts = mutableListOf<WorkoutId>()
        compose.setContent { RideLabTheme {
            WorkoutLibraryScreen(FtpState(loaded = true), true, {}, {}, { starts.add(it) }, control = verifiedControl)
        } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(context.getString(R.string.workout_initial_ftp)))
        compose.onNodeWithText(context.getString(R.string.workout_initial_ftp)).performClick()
        compose.onNodeWithText(context.getString(R.string.workout_ftp_required)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.workout_start)).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(starts.isEmpty()) }
        compose.onAllNodesWithText(context.getString(R.string.workout_start))
            .filter(hasClickAction() and hasAnyAncestor(isDialog())).onFirst().performClick()
        compose.runOnIdle { assertEquals(listOf(WorkoutId.INITIAL_FTP_RAMP), starts) }
    }

    @Test fun previewAndCancelNeverStartAutomaticControl() {
        val starts = mutableListOf<WorkoutId>()
        compose.setContent { RideLabTheme {
            WorkoutLibraryScreen(FtpState(loaded = true, ftp = 150), true, {}, {}, { starts.add(it) }, control = verifiedControl)
        } }
        compose.onNodeWithText(context.getString(R.string.workout_recovery)).performClick()
        compose.onNodeWithText(context.getString(R.string.workout_start)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(starts.isEmpty()) }
        compose.onNodeWithText(context.getString(R.string.erg_cancel)).performClick()
        compose.runOnIdle { assertTrue(starts.isEmpty()) }
        compose.onNodeWithText(context.getString(R.string.workout_start)).performClick()
        compose.onAllNodesWithText(context.getString(R.string.workout_start))
            .filter(hasClickAction() and hasAnyAncestor(isDialog())).onFirst().performClick()
        compose.runOnIdle { assertEquals(listOf(WorkoutId.RECOVERY_30), starts) }
    }
    @Test fun absentFtpCannotStartWorkout() {
        compose.setContent { RideLabTheme {
            WorkoutLibraryScreen(FtpState(loaded = true), true, {}, {}, { fail("No FTP must not start") }, control = verifiedControl)
        } }
        compose.onNodeWithText(context.getString(R.string.workout_recovery)).performClick()
        compose.onNodeWithText(context.getString(R.string.workout_start)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.workout_ftp_required)).assertExists()
    }
    @Test fun activeWorkoutHidesManualApplyAndKeepsPauseVisible() {
        compose.setContent { RideLabTheme {
            RideScreen(GattDeviceState(), HeartRateState(), RideState(RidePhase.ACTIVE, 0), true,
                {}, {}, { fail("Manual ERG must be locked") }, {},
                workout = WorkoutState(WorkoutPhase.ACTIVE, BuiltInWorkouts.find(WorkoutId.RECOVERY_30), 150))
        } }
        compose.onNodeWithText(context.getString(R.string.ui_apply_target)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.workout_pause)).assertIsDisplayed()
    }
    @Test fun acceptingFtpRequiresExplicitConfirmation() {
        var accepted = 0
        compose.setContent { RideLabTheme {
            RampResultPanel(RampTestResult(1, 212.0, 159), FtpState(loaded = true, ftp = 150), { accepted++ }, {})
        } }
        val use = context.getString(R.string.workout_use_estimate, 159)
        compose.onNodeWithText(use).performClick()
        compose.runOnIdle { assertEquals(0, accepted) }
        compose.onNodeWithText(context.getString(R.string.erg_cancel)).performClick()
        compose.runOnIdle { assertEquals(0, accepted) }
        compose.onNodeWithText(use).performClick()
        compose.onNodeWithText(context.getString(R.string.erg_confirm)).performClick()
        compose.runOnIdle { assertEquals(1, accepted) }
    }
}
