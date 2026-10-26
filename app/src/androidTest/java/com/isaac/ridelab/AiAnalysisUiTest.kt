package com.isaac.ridelab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.ui.ai.*
import com.isaac.ridelab.ui.theme.RideLabTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AiAnalysisUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun openingDoesNotAnalyzeAndExplicitStartDispatchesOnce() {
        var requests = 0
        compose.setContent { RideLabTheme {
            AiAnalysisScreen(AiAnalysisUiState(question = "Review my last ride"), {}, {}, { requests++ }, {}, {}, {})
        } }
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText(context.getString(R.string.ai_analysis_start)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }
    @Test fun runningStateOffersStopRatherThanACompetingStart() {
        var stops = 0
        compose.setContent { RideLabTheme {
            AiAnalysisScreen(AiAnalysisUiState(question = "q", running = true), {}, {}, {}, { stops++ }, {}, {})
        } }
        compose.onNodeWithText(context.getString(R.string.ai_analysis_start)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.ai_analysis_stop)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, stops) }
    }
    @Test fun socketTimeoutIsNotLabeledAsUnsupportedToolCalling() {
        compose.setContent { RideLabTheme {
            AiAnalysisScreen(AiAnalysisUiState(error = com.isaac.ridelab.ai.analysis.AiAnalysisError.API,
                apiProblem = com.isaac.ridelab.ai.AiProblem.TIMEOUT, requestIndex = 4), {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText(context.getString(R.string.ai_analysis_request_timeout, 4)).performScrollTo().assertExists()
        compose.onNodeWithText(context.getString(R.string.ai_analysis_api_error)).assertDoesNotExist()
    }
}
