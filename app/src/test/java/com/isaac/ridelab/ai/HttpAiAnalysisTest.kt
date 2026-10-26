package com.isaac.ridelab.ai

import com.isaac.ridelab.ai.analysis.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

/** Complete tool loop over real HTTP, with a synthetic local provider and fake read-only data. */
class HttpAiAnalysisTest {
    @Test fun kimiK3SixToolsThenSlowFinalStreamProducesReportWithoutDuplicateQueries() = runBlocking {
        var requests = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> exchange.use {
            val body = JSONObject(it.requestBody.readBytes().toString(Charsets.UTF_8))
            requests++
            assertTrue(body.getBoolean("stream")); assertEquals("low", body.getString("reasoning_effort"))
            if (requests == 2) {
                assertEquals("none", body.getString("tool_choice"))
                assertTrue(body.has("tools"))
                assertEquals("reasoning for replay", body.getJSONArray("messages").getJSONObject(2).getString("reasoning_content"))
                // A report is slower than a short connection probe; do not use that probe budget.
                Thread.sleep(180)
            }
            it.responseHeaders.add("Content-Type", "text/event-stream")
            it.sendResponseHeaders(200, 0)
            fun frame(json: String) { it.responseBody.write("data: $json\n\n".toByteArray(Charsets.UTF_8)); it.responseBody.flush() }
            if (requests == 1) {
                frame("""{"choices":[{"delta":{"reasoning_content":"reasoning for replay"},"finish_reason":null}]}""")
                val tools = org.json.JSONArray()
                for (i in 0 until 6) {
                    val name = if (i < 2) "query_sql" else "ride_metrics"
                    val args = if (i < 2) JSONObject().put("sql", "SELECT $i AS id") else JSONObject().put("session_id", i)
                    tools.put(JSONObject().put("index", i).put("id", "call$i").put("type", "function")
                        .put("function", JSONObject().put("name", name).put("arguments", args.toString())))
                }
                frame(JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("delta", JSONObject().put("tool_calls", tools))
                    .put("finish_reason", "tool_calls"))).toString())
            } else frame("""{"choices":[{"delta":{"content":"已根据骑行数据生成报告"},"finish_reason":"stop"}]}""")
            frame("""{"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":10}}""")
            frame("[DONE]")
        } }
        server.start()
        try {
            var sqlReads = 0; var metricReads = 0
            val config = AiConfiguration(AiProvider.KIMI, model = "kimi-k3", apiKey = "synthetic-key")
            // Exercise production Kimi wire/transport/engine against a synthetic loopback endpoint.
            val model = AiAnalysisModel { configuration, system, question ->
                val wire = AiAnalysisWire(configuration, system, question)
                object : AiModelConversation {
                    override suspend fun next(allowTools: Boolean): AiAnalysisReply {
                        val native = wire.request(allowTools)
                        val local = AiWireRequest("http://127.0.0.1:${server.address.port}/v1/chat/completions", native.headers, native.body)
                        val response = AiHttpTransport(AiHttpTimeouts(readMillis = 2000, requestMillis = 3000)).execute(local)
                        assertNull(response.problem)
                        return wire.parse(response.status!!, response.body)
                    }
                    override fun addResults(reply: AiAnalysisReply, results: List<String>) = wire.addResults(reply, results)
                }
            }
            val data = object : AiRideData {
                override suspend fun query(sql: String): String { sqlReads++; return """{"columns":["id"],"rows":[[1]],"returned_rows":1}""" }
                override suspend fun metrics(sessionId: Long): String { metricReads++; return """{"session_id":$sessionId,"np_w":100}""" }
            }
            val stages = mutableListOf<Pair<Int, Boolean>>()
            val answer = AiAnalysisEngine(model, data).analyze(config, "system", "question", onRequest = { n, tools -> stages += n to tools })
            assertEquals("已根据骑行数据生成报告", answer.text)
            assertEquals(2, requests); assertEquals(2, sqlReads); assertEquals(4, metricReads)
            assertEquals(6, answer.traces.size); assertEquals(listOf(1 to true, 2 to false), stages)
            assertEquals(20L, answer.usage.input); assertEquals(20L, answer.usage.output)
        } finally { server.stop(0) }
    }
    @Test fun explicitQuestionQueriesOnceAndReturnsEvidenceWithoutSendingKeyInMessages() = runBlocking {
        val bodies = mutableListOf<JSONObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> exchange.use {
            val request = JSONObject(it.requestBody.readBytes().toString(Charsets.UTF_8))
            bodies += request
            val response = if (bodies.size == 1)
                """{"choices":[{"message":{"role":"assistant","tool_calls":[{"id":"sql1","type":"function","function":{"name":"query_sql","arguments":"{\"sql\":\"SELECT ftpWatts, bodyWeightKg FROM user_profile\"}"}}]}}],"usage":{"prompt_tokens":30,"completion_tokens":12}}"""
                else """{"choices":[{"message":{"content":"FTP 130 W; weight 65 kg. Current settings, not historical snapshots."}}],"usage":{"prompt_tokens":60,"completion_tokens":20}}"""
            val bytes = response.toByteArray()
            it.sendResponseHeaders(200, bytes.size.toLong()); it.responseBody.write(bytes)
        } }
        server.start()
        try {
            var reads = 0
            val data = object : AiRideData {
                override suspend fun query(sql: String): String {
                    reads++; assertEquals("SELECT ftpWatts, bodyWeightKg FROM user_profile", sql)
                    return """{"columns":["ftpWatts","bodyWeightKg"],"rows":[[130,65]],"returned_rows":1,"truncated":false}"""
                }
                override suspend fun metrics(sessionId: Long) = error("Not needed")
            }
            val config = AiConfiguration(AiProvider.CUSTOM, "http://127.0.0.1:${server.address.port}/v1",
                "test-model", "secret-test-key", true)
            val answer = AiAnalysisEngine(HttpAiAnalysisModel(), data).analyze(config, "system", "Read my profile")
            assertEquals(1, reads); assertEquals(2, bodies.size)
            assertTrue(answer.text.contains("130 W"))
            assertEquals(90L, answer.usage.input); assertEquals(32L, answer.usage.output)
            assertTrue(bodies[1].getJSONArray("messages").getJSONObject(3).getString("content").contains("130"))
            assertFalse(bodies.any { it.toString().contains("secret-test-key") })
        } finally { server.stop(0) }
    }
}
