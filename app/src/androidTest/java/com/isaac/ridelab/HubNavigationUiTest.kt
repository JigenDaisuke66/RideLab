package com.isaac.ridelab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.ui.screen.*
import com.isaac.ridelab.service.RideState
import com.isaac.ridelab.ui.theme.RideLabTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Compile-checked; run separately on a device for real layout/interaction acceptance. */
class HubNavigationUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun homeSelectionAndWorkoutRemainIndependentActions() {
        var trainer = 0; var hr = 0; var workout = 0; var started = 0
        compose.setContent { RideLabTheme {
            HomeScreen(GattDeviceState(), HeartRateState(), FtpState(), HistoryState(loading = false),
                RideState(), true, { trainer++ }, { hr++ }, {}, {}, { started++ }, {}, {},
                openWorkouts = { workout++ })
        } }
        compose.onNodeWithText(context.getString(R.string.role_trainer)).performClick()
        compose.onNodeWithText(context.getString(R.string.ui_heart_rate)).performClick()
        compose.onNodeWithText(context.getString(R.string.ride_start)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.workout_library)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, trainer); assertEquals(1, hr); assertEquals(1, workout); assertEquals(0, started)
        }
    }

    @Test fun backupSlotStaysComposedInToolbarWhenHistoryLoads() {
        var toolbarClicks = 0
        compose.setContent { RideLabTheme {
            HistoryScreen(HistoryState(loading = true), {}, {}, ChartState(), {},
                backupControls = {
                    androidx.compose.material3.IconButton(onClick = { toolbarClicks++ }) {
                        androidx.compose.material3.Text(context.getString(R.string.ui_data_management))
                    }
                })
        } }
        compose.onNodeWithText(context.getString(R.string.ui_data_management)).performClick()
        compose.onNodeWithText(context.getString(R.string.history_loading)).assertExists()
        compose.runOnIdle { assertEquals(1, toolbarClicks) }
    }
}
