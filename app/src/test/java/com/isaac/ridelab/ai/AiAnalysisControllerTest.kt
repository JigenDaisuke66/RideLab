package com.isaac.ridelab.ai

import com.isaac.ridelab.ai.analysis.*
import com.isaac.ridelab.ui.ai.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiAnalysisControllerTest {
    private class Store : AiSettingsStore {
        var value = AiPreferences(AiProvider.KIMI, mapOf(AiProvider.KIMI to AiConfiguration(AiProvider.KIMI,
            KimiRegion.CHINA.baseUrl, "model", "saved-key")))
        override suspend fun read() = value
        override suspend fun save(preferences: AiPreferences) { value = preferences }
    }
    private val data = object : AiRideData {
        override suspend fun query(sql: String) = """{"rows":[[130]],"columns":["ftpWatts"],"returned_rows":1}"""
        override suspend fun metrics(sessionId: Long) = "{}"
    }
    @Test fun savedCredentialsAreReusedAndOnlySelectedSkillIsLoaded() = runBlocking {
        var calls = 0; var config: AiConfiguration? = null; var loaded: AiAnalysisSkill? = null
        val model = AiAnalysisModel { configuration, system, _ ->
            config = configuration
            assertTrue(system.contains("zh-CN")); assertTrue(system.contains("user_profile"))
            object : AiModelConversation {
                override suspend fun next(allowTools: Boolean) = if (calls++ == 0)
                    AiAnalysisReply(calls = listOf(AiToolCall("a", "query_sql", JSONObject().put("sql", "SELECT ftpWatts FROM user_profile"))))
                    else AiAnalysisReply("report")
                override fun addResults(reply: AiAnalysisReply, results: List<String>) = Unit
            }
        }
        val controller = AiAnalysisController(this, Store(), AiAnalysisEngine(model, data), { loaded = it; "selected skill only" })
        assertEquals(0, calls)
        controller.skill(AiAnalysisSkill.FTP_EVIDENCE); controller.question("Analyze FTP")
        controller.analyze("zh-CN"); controller.analyze("zh-CN"); yield()
        assertEquals("saved-key", config?.apiKey)
        assertEquals(KimiRegion.CHINA.baseUrl, config?.baseUrl)
        assertEquals(AiAnalysisSkill.FTP_EVIDENCE, loaded)
        assertEquals("report", controller.state.value.answer?.text)
        assertEquals(2, calls)
        assertFalse(controller.state.value.running)
    }
    @Test fun noSavedConfigurationDoesNotQueryOrSend() = runBlocking {
        val store = Store().apply { value = AiPreferences() }
        val engine = AiAnalysisEngine(AiAnalysisModel { _, _, _ -> error("Must not send") }, data)
        val controller = AiAnalysisController(this, store, engine, { "skill" })
        controller.question("question"); controller.analyze("en"); yield()
        assertEquals(AiAnalysisError.CONFIGURATION, controller.state.value.error)
        assertFalse(controller.state.value.running)
    }
    @Test fun cancellationPreventsNewOverlappingAnalysisAndLateReport() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val model = AiAnalysisModel { _, _, _ -> object : AiModelConversation {
            override suspend fun next(allowTools: Boolean): AiAnalysisReply { started.complete(Unit); awaitCancellation() }
            override fun addResults(reply: AiAnalysisReply, results: List<String>) = Unit
        } }
        val controller = AiAnalysisController(this, Store(), AiAnalysisEngine(model, data), { "skill" })
        controller.question("question"); controller.analyze("en"); started.await()
        controller.cancel(); controller.analyze("en"); yield()
        assertFalse(controller.state.value.running)
        assertEquals(AiAnalysisError.CANCELLED, controller.state.value.error)
        assertNull(controller.state.value.answer)
    }
    @Test fun timeoutAfterSuccessfulQueryKeepsEvidenceAndIdentifiesTheModelRequest() = runBlocking {
        var requests = 0
        val model = AiAnalysisModel { _, _, _ -> object : AiModelConversation {
            override suspend fun next(allowTools: Boolean): AiAnalysisReply {
                if (++requests == 1) return AiAnalysisReply(calls = listOf(AiToolCall("a", "query_sql", JSONObject().put("sql", "SELECT 1"))))
                throw AiAnalysisException(AiAnalysisError.API, AiProblem.TIMEOUT)
            }
            override fun addResults(reply: AiAnalysisReply, results: List<String>) = Unit
        } }
        val controller = AiAnalysisController(this, Store(), AiAnalysisEngine(model, data), { "skill" })
        controller.question("q"); controller.analyze("zh-CN"); yield()
        assertEquals(2, requests); assertEquals(2, controller.state.value.requestIndex)
        assertEquals(1, controller.state.value.traces.size)
        assertEquals(AiProblem.TIMEOUT, controller.state.value.apiProblem)
        assertNull(controller.state.value.answer); assertFalse(controller.state.value.running)
    }
    @Test fun totalTimeLimitIsDistinctFromASocketTimeoutAndDoesNotRetry() = runBlocking {
        var requests = 0
        val model = AiAnalysisModel { _, _, _ -> object : AiModelConversation {
            override suspend fun next(allowTools: Boolean): AiAnalysisReply { requests++; awaitCancellation() }
            override fun addResults(reply: AiAnalysisReply, results: List<String>) = Unit
        } }
        val controller = AiAnalysisController(this, Store(), AiAnalysisEngine(model, data), { "skill" }, timeoutMillis = 30)
        controller.question("q"); controller.analyze("en")
        delay(100)
        assertEquals(1, requests); assertEquals(AiAnalysisError.TIME_LIMIT, controller.state.value.error)
        assertNull(controller.state.value.apiProblem); assertFalse(controller.state.value.running)
    }
}
