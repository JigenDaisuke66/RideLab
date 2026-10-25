package com.isaac.ridelab.ui.ai

import com.isaac.ridelab.ai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AiSettingsOperation { LOADING, IDLE, SAVING, TESTING }

data class AiSettingsUiState(
    val configuration: AiConfiguration = AiConfiguration(),
    val operation: AiSettingsOperation = AiSettingsOperation.LOADING,
    val saved: Boolean = false,
    val result: AiProbeResult? = null,
    val storageFailed: Boolean = false,
) {
    val busy: Boolean get() = operation != AiSettingsOperation.IDLE
}

sealed interface AiSettingsAction {
    data class Provider(val value: AiProvider) : AiSettingsAction
    data class Endpoint(val value: String) : AiSettingsAction
    data class Model(val value: String) : AiSettingsAction
    data class Key(val value: String) : AiSettingsAction
    data class LocalHttp(val value: Boolean) : AiSettingsAction
}

/** Independent settings/probe state. Cannot obtain the ride service, database, or trainer controller. */
class AiSettingsController(
    private val scope: CoroutineScope,
    private val store: AiSettingsStore,
    private val probe: AiConnectionProbe,
) {
    private var preferences = AiPreferences()
    private val mutable = MutableStateFlow(AiSettingsUiState())
    val state = mutable.asStateFlow()

    init {
        scope.launch {
            try {
                preferences = store.read()
                mutable.value = AiSettingsUiState(preferences.configuration(), AiSettingsOperation.IDLE,
                    saved = preferences.selected in preferences.configurations)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutable.value = AiSettingsUiState(operation = AiSettingsOperation.IDLE,
                    result = AiProbeResult(AiProblem.STORAGE), storageFailed = true)
            }
        }
    }

    fun edit(action: AiSettingsAction) {
        val current = state.value
        if (current.busy || current.storageFailed) return
        val config = current.configuration
        val changed = when (action) {
            is AiSettingsAction.Provider -> preferences.configuration(action.value)
            is AiSettingsAction.Endpoint -> config.copy(baseUrl = action.value.take(2048))
            is AiSettingsAction.Model -> config.copy(model = action.value.take(200))
            is AiSettingsAction.Key -> config.copy(apiKey = action.value.take(8192))
            is AiSettingsAction.LocalHttp -> config.copy(allowLocalHttp = action.value)
        }
        mutable.value = current.copy(configuration = changed,
            saved = changed == preferences.configurations[changed.provider], result = null)
    }

    fun save() {
        val current = state.value
        if (current.busy || current.storageFailed) return
        current.configuration.problem()?.let { mutable.value = current.copy(result = AiProbeResult(it)); return }
        persist(preferences.copy(selected = current.configuration.provider,
            configurations = preferences.configurations + (current.configuration.provider to current.configuration)))
    }

    /** Tests the current draft, without saving it and without sending ride information. */
    fun test(prompt: String) {
        val current = state.value
        if (current.busy || current.storageFailed) return
        current.configuration.problem()?.let { mutable.value = current.copy(result = AiProbeResult(it)); return }
        mutable.value = current.copy(operation = AiSettingsOperation.TESTING, result = null)
        scope.launch {
            val result = try { probe.test(current.configuration, prompt)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { AiProbeResult(AiProblem.NETWORK) }
            mutable.value = current.copy(operation = AiSettingsOperation.IDLE, result = result)
        }
    }

    fun forget() {
        if (state.value.busy) return
        // On a decryption/load failure, the UI explicitly confirms resetting all AI settings.
        persist(if (state.value.storageFailed) AiPreferences() else preferences.copy(
            selected = state.value.configuration.provider,
            configurations = preferences.configurations - state.value.configuration.provider))
    }

    private fun persist(next: AiPreferences) {
        val current = state.value
        mutable.value = current.copy(operation = AiSettingsOperation.SAVING, result = null)
        scope.launch {
            try {
                store.save(next)
                preferences = next
                mutable.value = AiSettingsUiState(next.configuration(), AiSettingsOperation.IDLE,
                    saved = next.selected in next.configurations)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutable.value = current.copy(operation = AiSettingsOperation.IDLE, result = AiProbeResult(AiProblem.STORAGE))
            }
        }
    }
}
