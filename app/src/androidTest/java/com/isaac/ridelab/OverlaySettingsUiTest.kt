package com.isaac.ridelab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.ui.shared.OverlayControl
import com.isaac.ridelab.ui.overlay.RideOverlayState
import com.isaac.ridelab.ui.theme.RideLabTheme
import org.junit.Rule
import org.junit.Test

class OverlaySettingsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun disconnectedSettingsOpenPreviewWithOnlyTwoSliders() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { RideLabTheme { OverlayControl(RideOverlayState.OFF, false, {}, {}) } }
        compose.onNodeWithText(context.getString(R.string.overlay_enable)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.overlay_adjust)).performClick()
        compose.onNodeWithText(context.getString(R.string.capsule_preview)).assertExists()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(2)
    }
}
