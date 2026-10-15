package com.isaac.ridelab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.ui.screen.AppearanceControls
import com.isaac.ridelab.ui.settings.AppearanceAction
import com.isaac.ridelab.ui.theme.RideLabTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Compile-checked here; device execution is separate and requires an APK. */
class AppearanceActionsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun paletteButtonsDispatchExactlyOneRoleSpecificAction() {
        val actions = mutableListOf<AppearanceAction>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { RideLabTheme { AppearanceControls(Appearance(), actions::add) } }
        compose.onAllNodesWithText(context.getString(R.string.theme_blue)).onFirst().performClick()
        compose.runOnIdle { assertEquals(listOf(AppearanceAction.Palette(ThemePalette.BLUE)), actions) }
        compose.onAllNodesWithText(context.getString(R.string.theme_blue)).onLast().performClick()
        compose.runOnIdle { assertEquals(AppearanceAction.OverlayPalette(ThemePalette.BLUE), actions.last()); assertEquals(2, actions.size) }
    }
}
