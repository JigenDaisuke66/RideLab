package com.isaac.ridelab.ai

import com.isaac.ridelab.ui.ai.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AiSettingsControllerTest {
    @Test fun switchingKimiRegionKeepsKeyAndModelAndRestoresSavedRegion() = runBlocking {
        val store = Store()
        val probe = AiConnectionProbe { _, _ -> fail("No automatic request"); AiProbeResult() }
        val controller = AiSettingsController(this, store, probe); yield()
        controller.edit(AiSettingsAction.Provider(AiProvider.KIMI))
        setup(controller, "kimi-model")
        controller.edit(AiSettingsAction.Endpoint(KimiRegion.CHINA.baseUrl))
        assertEquals("key", controller.state.value.configuration.apiKey)
        controller.save(); yield()
        val restored = AiSettingsController(this, store, probe); yield()
        assertEquals(KimiRegion.CHINA.baseUrl, restored.state.value.configuration.baseUrl)
        assertEquals("kimi-model", restored.state.value.configuration.model)
        restored.edit(AiSettingsAction.Endpoint(KimiRegion.INTERNATIONAL.baseUrl))
        assertEquals("key", restored.state.value.configuration.apiKey)
    }
    private class Store(var value: AiPreferences = AiPreferences()) : AiSettingsStore {
        var writes = 0
        var failRead = false
        var failSave = false
        override suspend fun read(): AiPreferences { check(!failRead); return value }
        override suspend fun save(preferences: AiPreferences) { check(!failSave); value = preferences; writes++ }
    }
    private fun setup(controller: AiSettingsController, model: String = "chat") {
        controller.edit(AiSettingsAction.Model(model))
        controller.edit(AiSettingsAction.Key("key"))
    }

    @Test fun providersSaveIndependentlyAndRestoreWithoutNetworkCalls() = runBlocking {
        val store = Store(); var requests = 0
        val probe = AiConnectionProbe { _, _ -> requests++; AiProbeResult() }
        val controller = AiSettingsController(this, store, probe); yield()
        setup(controller, "model-a"); controller.save(); yield()
        controller.edit(AiSettingsAction.Provider(AiProvider.DEEPSEEK))
        setup(controller, "model-b"); controller.save(); yield()
        controller.edit(AiSettingsAction.Provider(AiProvider.OPENAI))
        assertEquals("model-a", controller.state.value.configuration.model)
        assertEquals("model-b", store.value.configuration(AiProvider.DEEPSEEK).model)
        val restored = AiSettingsController(this, store, probe); yield()
        assertEquals(AiProvider.DEEPSEEK, restored.state.value.configuration.provider)
        assertEquals(0, requests)
        assertNull(restored.state.value.result)
    }

    @Test fun testingUsesDraftDoesNotSaveAndPreventsOverlappingRequests() = runBlocking {
        val store = Store(); var requests = 0; var modelSent = ""
        val finish = CompletableDeferred<Unit>()
        val controller = AiSettingsController(this, store, AiConnectionProbe { config, _ ->
            requests++; modelSent = config.model; finish.await(); AiProbeResult()
        }); yield(); setup(controller)
        controller.test("probe"); controller.test("duplicate"); yield()
        controller.edit(AiSettingsAction.Provider(AiProvider.GEMINI)); controller.save()
        assertEquals(1, requests)
        assertEquals(AiProvider.OPENAI, controller.state.value.configuration.provider)
        assertEquals(0, store.writes)
        finish.complete(Unit); yield()
        assertEquals("chat", modelSent)
        assertNull(controller.state.value.result?.problem)
        assertNotNull(controller.state.value.result)
        assertFalse(controller.state.value.saved)
    }

    @Test fun badInputNeverSavesOrSends() = runBlocking {
        val store = Store(); var requests = 0
        val controller = AiSettingsController(this, store, AiConnectionProbe { _, _ -> requests++; AiProbeResult() }); yield()
        controller.test("probe"); controller.save()
        assertEquals(AiProblem.MODEL, controller.state.value.result?.problem)
        assertEquals(0, store.writes); assertEquals(0, requests)
    }

    @Test fun changedConfigurationClearsOldSuccessAndUnsentEditsDoNotPersist() = runBlocking {
        val store = Store()
        val controller = AiSettingsController(this, store, AiConnectionProbe { _, _ -> AiProbeResult() }); yield()
        setup(controller); controller.save(); yield(); controller.test("probe"); yield()
        assertNotNull(controller.state.value.result)
        controller.edit(AiSettingsAction.Model("changed"))
        assertNull(controller.state.value.result)
        assertFalse(controller.state.value.saved)
        controller.edit(AiSettingsAction.Provider(AiProvider.DEEPSEEK))
        controller.edit(AiSettingsAction.Provider(AiProvider.OPENAI))
        assertEquals("chat", controller.state.value.configuration.model)
    }

    @Test fun readFailureRequiresExplicitResetRatherThanSilentlyDroppingCredentials() = runBlocking {
        val store = Store().apply { failRead = true }
        val controller = AiSettingsController(this, store, AiConnectionProbe { _, _ -> fail("Must not send"); AiProbeResult() }); yield()
        assertTrue(controller.state.value.storageFailed)
        controller.save(); controller.test("probe"); assertEquals(0, store.writes)
        controller.forget(); yield()
        assertFalse(controller.state.value.storageFailed)
        assertTrue(store.value.configurations.isEmpty())
    }

    @Test fun saveFailureIsExplicitAndDoesNotReportSaved() = runBlocking {
        val store = Store().apply { failSave = true }
        val controller = AiSettingsController(this, store, AiConnectionProbe { _, _ -> AiProbeResult() }); yield()
        setup(controller); controller.save(); yield()
        assertEquals(AiProblem.STORAGE, controller.state.value.result?.problem)
        assertFalse(controller.state.value.saved)
    }

    @Test fun forgetClearsOnlySelectedProviderAndNeverTestsConnection() = runBlocking {
        val store = Store(AiPreferences(configurations = mapOf(
            AiProvider.OPENAI to AiConfiguration(model = "a", apiKey = "key-a"),
            AiProvider.DEEPSEEK to AiConfiguration(AiProvider.DEEPSEEK, model = "b", apiKey = "key-b"))))
        val controller = AiSettingsController(this, store, AiConnectionProbe { _, _ -> fail("Must not send"); AiProbeResult() }); yield()
        controller.forget(); yield()
        assertFalse(store.value.configurations.containsKey(AiProvider.OPENAI))
        assertEquals("key-b", store.value.configuration(AiProvider.DEEPSEEK).apiKey)
        assertEquals("", controller.state.value.configuration.apiKey)
    }

    @Test fun probeFailuresAndTimeoutsStayTypedAndCanBeRetriedExplicitly() = runBlocking {
        for (problem in listOf(AiProblem.TIMEOUT, AiProblem.AUTH, AiProblem.RATE_LIMIT)) {
            var requests = 0
            val controller = AiSettingsController(this, Store(), AiConnectionProbe { _, _ -> requests++; AiProbeResult(problem) }); yield()
            setup(controller); controller.test("probe"); yield()
            assertEquals(problem, controller.state.value.result?.problem)
            assertEquals(1, requests)
            assertFalse(controller.state.value.busy)
        }
    }
}
