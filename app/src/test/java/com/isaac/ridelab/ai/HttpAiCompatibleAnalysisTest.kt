package com.isaac.ridelab.ai

import com.isaac.ridelab.ai.analysis.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress

class HttpAiCompatibleAnalysisTest {
    private class EmptyHistory : AiRideData {
        var reads = 0
        override suspend fun query(sql: String): String {
            reads++
            return if (sql.contains("user_profile")) AiSqlRows(listOf("ftpWatts", "bodyWeightKg"),
                listOf(listOf(150, null))).compactJson() else AiSqlRows(listOf("id"), emptyList()).compactJson()
        }
        override suspend fun metrics(sessionId: Long): String = error("No ride means no metrics")
    }

    @Test fun localServerWithoutToolSupportAcceptsOneOrdinaryRequestAfterLocalReads() = runBlocking {
        val data = EmptyHistory()
        var calls = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> exchange.use {
            calls++
            assertEquals("POST", it.requestMethod)
            assertEquals("/v1/chat/completions", it.requestURI.path)
            assertEquals(2, data.reads)
            val body = JSONObject(it.requestBody.readBytes().toString(Charsets.UTF_8))
            assertFalse(body.has("tools")); assertFalse(body.has("tool_choice"))
            assertEquals("unit-model", body.getString("model"))
            val bytes = """{"choices":[{"finish_reason":"stop","message":{"content":"No completed ride available."}}]}""".toByteArray()
            it.sendResponseHeaders(200, bytes.size.toLong()); it.responseBody.write(bytes)
        } }
        server.start()
        try {
            val config = AiConfiguration(AiProvider.CUSTOM, "http://127.0.0.1:${server.address.port}/v1",
                "unit-model", allowLocalHttp = true)
            val engine = AiAnalysisEngine(HttpAiAnalysisModel(), data)
            val result = engine.analyzeCompatible(config, "Use provided data only", "Review latest ride", 1_800_000_000_000, "UTC")
            assertEquals("No completed ride available.", result.text)
            assertEquals(1, result.requests); assertEquals(1, calls)
        } finally { server.stop(0) }
    }

    /** Explicitly opt-in; only synthetic empty-history data goes to the supplied endpoint. */
    @Test fun optInLivePlainAnalysis(): Unit = runBlocking {
        assumeTrue("Live test not requested", System.getenv("RIDELAB_AI_COMPAT_LIVE") == "1")
        val endpoint = requireNotNull(System.getenv("RIDELAB_AI_COMPAT_ENDPOINT"))
        val model = requireNotNull(System.getenv("RIDELAB_AI_COMPAT_MODEL"))
        val now = System.currentTimeMillis()
        val engine = AiAnalysisEngine(HttpAiAnalysisModel(), EmptyHistory())
        val result = engine.analyzeCompatible(AiConfiguration(AiProvider.CUSTOM, endpoint, model, allowLocalHttp = true),
            AiAnalysisInstructions.compatible("State limitations without fitness guesses.", "zh-CN", now, "UTC"),
            "This is a synthetic integration fixture, not a real user. Explain briefly why no recorded ride can be analyzed.", now, "UTC")
        assertFalse(result.text.isBlank()); assertEquals(1, result.requests)
        println("Live compatible analysis succeeded: requests=${result.requests}, answerChars=${result.text.length}")
    }
}
