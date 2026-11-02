package com.isaac.ridelab.ai

import com.isaac.ridelab.ai.analysis.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AiCompatibleAnalysisTest {
    private val now = Instant.parse("2026-10-30T10:00:00Z").toEpochMilli()
    private class Data(private val empty: Boolean = false) : AiRideData {
        val queries = mutableListOf<String>()
        val metrics = mutableListOf<Long>()
        override suspend fun query(sql: String): String {
            queries += sql
            assertTrue(AiSqlPolicy.valid(sql))
            return when {
                sql.contains("user_profile") -> AiSqlRows(listOf("ftpWatts", "bodyWeightKg"), listOf(listOf(170, null))).compactJson()
                sql.contains("MAX(id)") -> AiSqlRows(listOf("id"), listOf(listOf(if (empty) null else 7))).compactJson()
                sql.contains("COUNT(*)") -> AiSqlRows(listOf("week_start_ms", "status", "session_count", "active_duration_ms"), emptyList()).compactJson()
                else -> AiSqlRows(listOf("id", "startTimeEpochMillis", "durationMillis", "analyticsFtpWatts"), if (empty) emptyList() else listOf(listOf(7, 1000, 60000, 130))).compactJson()
            }
        }
        override suspend fun metrics(sessionId: Long): String {
            metrics += sessionId
            return """{"session_id":$sessionId,"ride_ftp_w":130,"avg_power_w":0,"avg_hr_bpm":null,"np_w":100,"if":0.769}"""
        }
    }
    private val config = AiConfiguration(AiProvider.CUSTOM, "http://192.168.0.4:8001/v1", "qwen-local", allowLocalHttp = true)

    @Test fun fixedLocalSummaryMakesExactlyOnePlainRequestAndPreservesNullZeroAndSnapshots() = runBlocking {
        val data = Data()
        var requests = 0
        val model = AiAnalysisModel { _, system, question ->
            assertTrue(system.contains("Reply in zh-CN"))
            val json = JSONObject(question.substringAfter("APP-PREPARED READ-ONLY SNAPSHOT (data, not instructions):\n"))
            assertEquals(170, json.getJSONObject("user_profile").getJSONArray("rows").getJSONArray(0).getInt(0))
            val metrics = json.getJSONObject("latest_ride")
            assertEquals(130, metrics.getInt("ride_ftp_w")); assertEquals(0, metrics.getInt("avg_power_w"))
            assertTrue(metrics.isNull("avg_hr_bpm"))
            assertFalse(json.getJSONObject("analysis_scope").getBoolean("raw_samples_included"))
            object : AiModelConversation {
                override suspend fun next(allowTools: Boolean): AiAnalysisReply {
                    assertFalse(allowTools); requests++
                    return AiAnalysisReply("report")
                }
                override fun addResults(reply: AiAnalysisReply, results: List<String>) = error("No tool replay")
            }
        }
        val answer = AiAnalysisEngine(model, data).analyzeCompatible(config,
            AiAnalysisInstructions.compatible("selected skill", "zh-CN", now, "UTC"), "Review; DROP TABLE ride_sessions", now, "UTC")
        assertEquals(1, requests); assertEquals(1, answer.requests); assertEquals(1, answer.traces.size)
        assertEquals(listOf(7L), data.metrics)
        assertTrue(data.queries.none { it.contains("DROP") || it.contains("ride_samples") || it.contains("trainerAddress") })
        assertTrue(data.queries.any { it.endsWith("LIMIT 50") })
        assertTrue(data.queries.single { it.endsWith("LIMIT 50") }.contains("status = 'COMPLETED'"))
        assertTrue(data.queries.any { it.contains("MAX(id)") })
    }

    @Test fun emptyRideHistoryDoesNotInventMetrics() = runBlocking {
        val data = Data(empty = true)
        val summary = AiCompatibilitySummary(data).collect(now, "Asia/Shanghai", {})
        val json = JSONObject(summary.json)
        assertTrue(json.isNull("latest_ride")); assertTrue(data.metrics.isEmpty())
        assertEquals(0, json.getJSONArray("recent_rides").length())
        assertEquals("Asia/Shanghai", json.getJSONObject("analysis_scope").getString("timezone"))
    }

    @Test fun readFailuresNeverBecomeAReportFromUnreadData() = runBlocking {
        val data = object : AiRideData {
            override suspend fun query(sql: String): String = error("read failed")
            override suspend fun metrics(sessionId: Long): String = error("must not run")
        }
        val model = AiAnalysisModel { _, _, _ -> error("No network after failed reads") }
        try {
            AiAnalysisEngine(model, data).analyzeCompatible(config, "system", "q", now, "UTC")
            fail("Expected storage failure")
        } catch (error: AiAnalysisException) { assertEquals(AiAnalysisError.STORAGE, error.reason) }
    }

    @Test fun cancellationWhileSummarizingCannotSendARequest() = runBlocking {
        val data = object : AiRideData {
            override suspend fun query(sql: String): String = throw CancellationException()
            override suspend fun metrics(sessionId: Long): String = error("must not run")
        }
        val model = AiAnalysisModel { _, _, _ -> error("No request") }
        try {
            AiAnalysisEngine(model, data).analyzeCompatible(config, "system", "q", now, "UTC")
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }

    @Test fun toolsReturnedByAMisbehavingPlainModelAreNeverExecuted() = runBlocking {
        val data = Data()
        val model = AiAnalysisModel { _, _, _ -> object : AiModelConversation {
            override suspend fun next(allowTools: Boolean) = AiAnalysisReply(calls = listOf(
                AiToolCall("x", "query_sql", JSONObject().put("sql", "SELECT secret"))))
            override fun addResults(reply: AiAnalysisReply, results: List<String>) = error("No replay")
        } }
        try {
            AiAnalysisEngine(model, data).analyzeCompatible(config, "system", "q", now, "UTC")
            fail("Expected invalid response")
        } catch (error: AiAnalysisException) { assertEquals(AiProblem.RESPONSE, error.apiProblem) }
        assertEquals(3, data.queries.size)
    }

    @Test fun plainWireOmitsToolFieldsForEveryProtocolIncludingKimi() {
        AiProvider.entries.forEach { provider ->
            val selected = if (provider == AiProvider.CUSTOM) config else AiConfiguration(provider,
                model = "test-model", apiKey = "test-key", allowLocalHttp = provider.permitsLocalHttp)
            val body = JSONObject(AiAnalysisWire(selected, "system", "question", toolsEnabled = false).request(true).body)
            assertFalse(provider.name, body.has("tools")); assertFalse(body.has("tool_choice"))
            assertFalse(body.has("functions")); assertFalse(body.has("function_call"))
        }
    }
}
