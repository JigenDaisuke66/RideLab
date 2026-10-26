package com.isaac.ridelab.ai

import com.isaac.ridelab.ai.analysis.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiAnalysisEngineTest {
    private val config = AiConfiguration(model = "chat", apiKey = "key")
    private fun sql(value: String = "SELECT id FROM ride_sessions") = AiToolCall("id", "query_sql", JSONObject().put("sql", value))
    private class Data : AiRideData {
        var queries = 0; var metrics = 0
        override suspend fun query(sql: String): String { queries++; return """{"columns":["id"],"rows":[[1]],"returned_rows":1,"truncated":false}""" }
        override suspend fun metrics(sessionId: Long): String { metrics++; return """{"session_id":1,"ride_ftp_w":130}""" }
    }
    private class Model(private val replies: List<AiAnalysisReply>) : AiAnalysisModel {
        var requests = 0; val results = mutableListOf<String>(); val permissions = mutableListOf<Boolean>()
        override fun start(configuration: AiConfiguration, system: String, question: String) = object : AiModelConversation {
            override suspend fun next(allowTools: Boolean): AiAnalysisReply {
                permissions += allowTools; return replies[requests++.coerceAtMost(replies.lastIndex)]
            }
            override fun addResults(reply: AiAnalysisReply, results: List<String>) { this@Model.results += results }
        }
    }
    @Test fun queriesThenProducesAnswerWithActualUsage() = runBlocking {
        val model = Model(listOf(AiAnalysisReply(calls = listOf(sql()), usage = AiTokenUsage(10, 5)),
            AiAnalysisReply("Evidence-based report", usage = AiTokenUsage(20, 8))))
        val data = Data()
        val answer = AiAnalysisEngine(model, data).analyze(config, "system", "question")
        assertEquals("Evidence-based report", answer.text)
        assertEquals(30L, answer.usage.input); assertEquals(13L, answer.usage.output)
        assertEquals(2, answer.requests); assertEquals(1, data.queries)
    }
    @Test fun identicalQueriesReuseOnlyWithinThisRun() = runBlocking {
        val model = Model(listOf(AiAnalysisReply(calls = listOf(sql(), sql())), AiAnalysisReply("report")))
        val data = Data(); val result = AiAnalysisEngine(model, data).analyze(config, "system", "question")
        assertEquals(1, data.queries); assertEquals(2, result.traces.size)
        assertFalse(result.traces[0].cached); assertTrue(result.traces[1].cached)
        assertNull(result.usage.input)
    }
    @Test fun writeSqlAndUnknownToolsNeverReachDataRepository() = runBlocking {
        val model = Model(listOf(AiAnalysisReply(calls = listOf(sql("DELETE FROM ride_sessions"),
            AiToolCall("x", "set_target_power", JSONObject()))), AiAnalysisReply("unfounded report")))
        val data = Data()
        try { AiAnalysisEngine(model, data).analyze(config, "system", "question"); fail("No evidence")
        } catch (error: AiAnalysisException) { assertEquals(AiAnalysisError.NO_EVIDENCE, error.reason) }
        assertEquals(0, data.queries); assertEquals(0, data.metrics)
        assertTrue(model.results.all { JSONObject(it).has("error") })
    }
    @Test fun modelWithoutToolUseDoesNotGetToPretendItReadData() = runBlocking {
        try { AiAnalysisEngine(Model(listOf(AiAnalysisReply("Your FTP is 200"))), Data()).analyze(config, "s", "q"); fail()
        } catch (error: AiAnalysisException) { assertEquals(AiAnalysisError.NO_EVIDENCE, error.reason) }
    }
    @Test fun emptySuccessfulQueryCanExplainThatThereAreNoRecordedRides() = runBlocking {
        val empty = object : AiRideData {
            override suspend fun query(sql: String) = """{"columns":["id"],"rows":[],"returned_rows":0}"""
            override suspend fun metrics(sessionId: Long) = "{}"
        }
        val model = Model(listOf(AiAnalysisReply(calls = listOf(sql())), AiAnalysisReply("No recorded rides")))
        assertEquals("No recorded rides", AiAnalysisEngine(model, empty).analyze(config, "s", "q").text)
    }
    @Test fun toolBudgetStopsUncontrolledLoops() = runBlocking {
        val model = Model(listOf(AiAnalysisReply(calls = List(7) { sql("SELECT $it") })))
        val data = Data()
        try { AiAnalysisEngine(model, data).analyze(config, "s", "q"); fail()
        } catch (error: AiAnalysisException) { assertEquals(AiAnalysisError.BUDGET, error.reason) }
        assertEquals(1, model.requests); assertEquals(0, data.queries)
    }
    @Test fun nativeMetricsToolDoesNotNeedRawSamplesInModelContext() = runBlocking {
        val model = Model(listOf(AiAnalysisReply(calls = listOf(AiToolCall("m", "ride_metrics", JSONObject().put("session_id", 1)))),
            AiAnalysisReply("report")))
        val data = Data(); AiAnalysisEngine(model, data).analyze(config, "s", "q")
        assertEquals(1, data.metrics); assertEquals(0, data.queries)
        assertTrue(model.results.single().contains("ride_ftp_w"))
    }
}
