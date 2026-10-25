package com.isaac.ridelab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.data.model.Appearance
import com.isaac.ridelab.locale.AppLanguage
import com.isaac.ridelab.ui.overlay.RideOverlayState
import com.isaac.ridelab.ui.screen.*
import com.isaac.ridelab.ui.settings.*
import com.isaac.ridelab.ui.theme.MetricTile
import com.isaac.ridelab.ui.theme.RideLabTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Compiled here; visual acceptance and actual UI execution require a device. */
class UiPolishPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun baselineMetricKeepsFullLocalizedAccessibleValue() {
        val value = context.getString(R.string.power_value, 135)
        compose.setContent { RideLabTheme { MetricTile(context.getString(R.string.ui_power), value) } }
        compose.onNodeWithText(value).assertIsDisplayed()
    }

    @Test fun settingsLanguageDispatchesOnceWithoutPreferenceOrDeviceCommands() {
        val languages = mutableListOf<String>()
        var otherActions = 0
        compose.setContent { RideLabTheme {
            SettingsDestination(
                SettingsUiState(FtpState(loaded = true), RideOverlayState.OFF, false, Appearance()),
                SettingsActions({ _, _ -> otherActions++ }, { _, _ -> otherActions++ },
                    { languages.add(it) }, { otherActions++ }, { otherActions++ }, { otherActions++ }),
                Modifier)
        } }
        val label = context.getString(R.string.language_french)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(context.getString(R.string.ui_language)))
        compose.onNodeWithText(context.getString(R.string.ui_language)).performClick()
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(AppLanguage.FRENCH.tag), languages)
            assertEquals(0, otherActions)
        }
    }

    @Test fun emptyHistoryShowsNextStepWithoutDispatchingAnExportOrDelete() {
        compose.setContent { RideLabTheme {
            HistoryScreen(HistoryState(loading = false), {}, {}, ChartState(), {})
        } }
        compose.onNodeWithText(context.getString(R.string.history_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.ui_history_empty_hint)).assertIsDisplayed()
    }
}
