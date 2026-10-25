package com.isaac.ridelab

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.data.repository.OverlayPreferences
import com.isaac.ridelab.ui.chart.*
import com.isaac.ridelab.ui.overlay.RideOverlayState
import com.isaac.ridelab.ui.ride.*
import com.isaac.ridelab.ui.theme.RideLabTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Device-side interaction checks, compile-only unless separately run on hardware. */
class RideMeterUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun chartSelectionUsesExistingSeriesWithoutChangingData() {
        val power = ChartSeries(listOf(listOf(ChartPoint(0, 0.0), ChartPoint(1000, 100.0))))
        val hr = ChartSeries(listOf(listOf(ChartPoint(1000, 130.0))))
        val cadence = ChartSeries(emptyList())
        val state = LiveTrendState(RideCharts(power, hr, cadence, 1000))
        compose.setContent { RideLabTheme { LiveRideCharts(state) } }
        compose.onNodeWithContentDescription(context.getString(R.string.chart_power)).assertExists()
        compose.onNodeWithText(context.getString(R.string.ui_heart_rate)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.chart_hr)).assertExists()
        compose.onNodeWithText(context.getString(R.string.cadence)).performClick()
        compose.onNodeWithText(context.getString(R.string.history_no_data)).assertExists()
        compose.onNodeWithText(context.getString(R.string.ui_power)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.chart_power)).assertExists()
        compose.runOnIdle { assertSame(power, state.charts.power); assertSame(cadence, state.charts.cadence) }
    }

    @Test fun compactOverlayButtonDispatchesExactlyOnceInEachDirection() {
        var overlay by mutableStateOf(RideOverlayState.OFF)
        var enabled = 0; var disabled = 0
        compose.setContent { RideLabTheme {
            RideToolbar(overlay, true, OverlayPreferences(),
                { enabled++; overlay = RideOverlayState.VISIBLE },
                { disabled++; overlay = RideOverlayState.OFF }, {})
        } }
        compose.onNodeWithContentDescription(context.getString(R.string.overlay_enable)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.overlay_disable)).performClick()
        compose.runOnIdle { assertEquals(1, enabled); assertEquals(1, disabled) }
    }

    @Test fun settingsStayAccessibleWithoutStartingRideOrConnectingDevices() {
        var dispatched = 0
        compose.setContent { RideLabTheme {
            RideToolbar(RideOverlayState.OFF, false, OverlayPreferences(),
                { dispatched++ }, { dispatched++ }, { dispatched++ })
        } }
        compose.onNodeWithContentDescription(context.getString(R.string.overlay_enable)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.history_more_actions)).performClick()
        compose.onNodeWithText(context.getString(R.string.overlay_adjust)).performClick()
        compose.onNodeWithText(context.getString(R.string.capsule_preview)).assertExists()
        compose.runOnIdle { assertEquals(0, dispatched) }
    }
}
