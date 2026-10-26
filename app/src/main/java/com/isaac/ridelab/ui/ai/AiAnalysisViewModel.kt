package com.isaac.ridelab.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isaac.ridelab.RideLabApplication
import com.isaac.ridelab.ai.HttpAiAnalysisModel
import com.isaac.ridelab.ai.analysis.AiAnalysisEngine
import com.isaac.ridelab.ai.analysis.AiAnalysisSkill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiAnalysisViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RideLabApplication
    private val controller = AiAnalysisController(viewModelScope, app.aiSettingsStore,
        AiAnalysisEngine(HttpAiAnalysisModel { event -> android.util.Log.i("RideLabAi", event.toString()) }, app.aiRideData), skillText = { skill ->
        withContext(Dispatchers.IO) { app.assets.open("ai_skills/${skill.asset}").bufferedReader().use { it.readText() } }
    })
    val state = controller.state
    fun question(value: String) = controller.question(value)
    fun skill(value: AiAnalysisSkill) = controller.skill(value)
    fun analyze(languageTag: String) = controller.analyze(languageTag)
    fun cancel() = controller.cancel()
    fun clear() = controller.clear()
}
