package com.isaac.ridelab.ui.ai

import com.isaac.ridelab.ai.*
import com.isaac.ridelab.ai.analysis.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId

data class AiAnalysisUiState(
    val question: String = "",
    val skill: AiAnalysisSkill = AiAnalysisSkill.RIDE_REVIEW,
    val running: Boolean = false,
    val cancelling: Boolean = false,
    val provider: AiProvider? = null,
    val traces: List<AiToolTrace> = emptyList(),
    val answer: AiAnalysisAnswer? = null,
    val error: AiAnalysisError? = null,
    val apiProblem: AiProblem? = null,
    val httpStatus: Int? = null,
    val requestIndex: Int = 0,
    val generatingReport: Boolean = false,
    val elapsedSeconds: Long = 0,
)

class AiAnalysisController(
    private val scope: CoroutineScope,
    private val settings: AiSettingsStore,
    private val engine: AiAnalysisEngine,
    private val skillText: suspend (AiAnalysisSkill) -> String,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> String = { ZoneId.systemDefault().id },
    private val timeoutMillis: Long = 300_000,
) {
    private val mutable = MutableStateFlow(AiAnalysisUiState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    fun question(value: String) { if (!state.value.running) mutable.value = state.value.copy(question = value.take(2000)) }
    fun skill(value: AiAnalysisSkill) { if (!state.value.running) mutable.value = state.value.copy(skill = value) }

    fun analyze(languageTag: String) {
        val current = state.value
        if (current.running || job?.isCompleted == false || current.question.isBlank()) return
        mutable.value = current.copy(running = true, cancelling = false, answer = null, traces = emptyList(),
            error = null, apiProblem = null, httpStatus = null, provider = null,
            requestIndex = 0, generatingReport = false, elapsedSeconds = 0)
        job = scope.launch {
            val start = System.nanoTime()
            val ticker = launch {
                while (isActive) {
                    delay(1000)
                    mutable.value = mutable.value.copy(elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000)
                }
            }
            try {
                val preferences = try { settings.read()
                } catch (error: CancellationException) { throw error
                } catch (_: Exception) { throw AiAnalysisException(AiAnalysisError.STORAGE) }
                val configuration = preferences.configuration()
                if (!preferences.configurations.containsKey(preferences.selected) || configuration.problem() != null)
                    throw AiAnalysisException(AiAnalysisError.CONFIGURATION, configuration.problem())
                mutable.value = mutable.value.copy(provider = configuration.provider)
                val answer = withTimeout(timeoutMillis) {
                    val instructions = AiAnalysisInstructions.system(skillText(current.skill), languageTag, now(), zone())
                    engine.analyze(configuration, instructions, current.question.trim(), onRequest = { index, allowTools ->
                        mutable.value = mutable.value.copy(requestIndex = index, generatingReport = !allowTools)
                    }) { traces ->
                        mutable.value = mutable.value.copy(traces = traces)
                    }
                }
                mutable.value = mutable.value.copy(answer = answer)
            } catch (_: TimeoutCancellationException) {
                mutable.value = mutable.value.copy(error = AiAnalysisError.TIME_LIMIT)
            } catch (error: CancellationException) {
                mutable.value = mutable.value.copy(error = AiAnalysisError.CANCELLED)
                throw error
            } catch (error: AiAnalysisException) {
                mutable.value = mutable.value.copy(error = error.reason, apiProblem = error.apiProblem, httpStatus = error.httpStatus)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(error = AiAnalysisError.INTERNAL)
            } finally { ticker.cancel(); mutable.value = mutable.value.copy(running = false, cancelling = false) }
        }
    }

    fun cancel() {
        if (state.value.running && !state.value.cancelling) {
            mutable.value = state.value.copy(cancelling = true)
            job?.cancel()
        }
    }
    fun clear() { if (!state.value.running) mutable.value = state.value.copy(answer = null, traces = emptyList(), error = null, provider = null) }
}
