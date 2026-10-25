package com.isaac.ridelab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.ai.*
import com.isaac.ridelab.ui.ai.*
import com.isaac.ridelab.ui.theme.RideLabTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Compile checked only; real provider credentials are never included in tests. */
class AiSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun openingOrClosingSettingsDoesNotTestConnectionAndTestIsExplicit() {
        var tests = 0; var saves = 0
        compose.setContent { RideLabTheme {
            AiSettingsCard(AiSettingsUiState(AiConfiguration(model = "chat", apiKey = "key"), AiSettingsOperation.IDLE),
                {}, { saves++ }, { tests++ }, {})
        } }
        compose.onNodeWithText(context.getString(R.string.ai_title)).performClick()
        compose.runOnIdle { assertEquals(0, tests); assertEquals(0, saves) }
        compose.onNodeWithText(context.getString(R.string.ai_test)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, tests); assertEquals(0, saves) }
        compose.onNodeWithText(context.getString(R.string.ai_close)).performClick()
        compose.runOnIdle { assertEquals(1, tests) }
    }

    @Test fun missingModelDisablesSaveAndTest() {
        compose.setContent { RideLabTheme {
            AiSettingsCard(AiSettingsUiState(operation = AiSettingsOperation.IDLE), {}, {}, {}, {})
        } }
        compose.onNodeWithText(context.getString(R.string.ai_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.ai_save)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.ai_test)).performScrollTo().assertIsNotEnabled()
    }
}
