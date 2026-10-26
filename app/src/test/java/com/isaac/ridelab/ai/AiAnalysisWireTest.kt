package com.isaac.ridelab.ai

import com.isaac.ridelab.ai.analysis.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiAnalysisWireTest {
    private fun wire(provider: AiProvider) = AiAnalysisWire(AiConfiguration(provider, model = "chat", apiKey = "key"), "system", "question")
    @Test fun compatibleToolReplayPreservesKimiAndDeepSeekReasoningFields() {
        for (provider in listOf(AiProvider.KIMI, AiProvider.DEEPSEEK, AiProvider.OPENAI)) {
            val wire = wire(provider)
            val request = JSONObject(wire.request(true).body)
            assertEquals("query_sql", request.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"))
            val reply = wire.parse(200, """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,"reasoning_content":"required replay","tool_calls":[{"id":"a","type":"function","function":{"name":"query_sql","arguments":"{\"sql\":\"SELECT 1\"}"}}]}}],"usage":{"prompt_tokens":10,"completion_tokens":20}}""")
            assertEquals("SELECT 1", reply.calls.single().arguments.getString("sql"))
            wire.addResults(reply, listOf("""{"columns":["n"],"rows":[[1]]}"""))
            val messages = JSONObject(wire.request(false).body).getJSONArray("messages")
            assertEquals("required replay", messages.getJSONObject(2).getString("reasoning_content"))
            assertEquals("tool", messages.getJSONObject(3).getString("role"))
            assertEquals("a", messages.getJSONObject(3).getString("tool_call_id"))
            val finalRequest = JSONObject(wire.request(false).body)
            if (provider == AiProvider.KIMI) {
                assertTrue(finalRequest.has("tools")); assertEquals("none", finalRequest.getString("tool_choice"))
            } else assertFalse(finalRequest.has("tools"))
        }
    }
    @Test fun kimiK3StreamsWithBoundedLowEffortAndPreservesTheTokenCap() {
        val config = AiConfiguration(AiProvider.KIMI, model = "kimi-k3", apiKey = "key")
        val request = AiAnalysisWire(config, "system", "question").request(true)
        val body = JSONObject(request.body)
        assertTrue(body.getBoolean("stream"))
        assertTrue(body.getJSONObject("stream_options").getBoolean("include_usage"))
        assertEquals("low", body.getString("reasoning_effort"))
        assertEquals(2048, body.getInt("max_completion_tokens"))
        assertFalse(body.has("max_tokens"))
        assertEquals("text/event-stream", request.headers["Accept"])
        assertEquals("auto", body.getString("tool_choice"))
        val older = JSONObject(AiAnalysisWire(config.copy(model = "kimi-k2.6"), "s", "q").request(true).body)
        assertFalse(older.has("reasoning_effort")); assertEquals(2048, older.getInt("max_tokens"))
        val other = JSONObject(wire(AiProvider.DEEPSEEK).request(true).body)
        assertFalse(other.getBoolean("stream")); assertFalse(other.has("reasoning_effort"))
    }
    @Test fun claudeUsesToolUseAndToolResultBlocks() {
        val wire = wire(AiProvider.ANTHROPIC)
        val request = JSONObject(wire.request(true).body)
        assertEquals("system", request.getString("system"))
        assertTrue(request.getJSONArray("tools").getJSONObject(0).has("input_schema"))
        val reply = wire.parse(200, """{"stop_reason":"tool_use","content":[{"type":"tool_use","id":"a","name":"ride_metrics","input":{"session_id":1}}],"usage":{"input_tokens":12,"output_tokens":9}}""")
        wire.addResults(reply, listOf("""{"session_id":1}"""))
        val result = JSONObject(wire.request(true).body).getJSONArray("messages").getJSONObject(2)
            .getJSONArray("content").getJSONObject(0)
        assertEquals("tool_result", result.getString("type")); assertEquals("a", result.getString("tool_use_id"))
        assertEquals(12L, reply.usage.input)
    }
    @Test fun geminiPreservesThoughtSignatureAndFunctionId() {
        val wire = wire(AiProvider.GEMINI)
        val request = JSONObject(wire.request(true).body)
        assertTrue(request.has("systemInstruction"))
        assertTrue(request.getJSONArray("tools").getJSONObject(0).has("functionDeclarations"))
        val reply = wire.parse(200, """{"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[{"thoughtSignature":"signature","functionCall":{"id":"f1","name":"query_sql","args":{"sql":"SELECT 1"}}}]}}],"usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":9,"thoughtsTokenCount":3}}""")
        wire.addResults(reply, listOf("""{"rows":[[1]]}"""))
        val contents = JSONObject(wire.request(true).body).getJSONArray("contents")
        assertEquals("signature", contents.getJSONObject(1).getJSONArray("parts").getJSONObject(0).getString("thoughtSignature"))
        assertEquals("f1", contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getJSONObject("functionResponse").getString("id"))
        assertEquals(12L, reply.usage.output)
    }
    @Test fun errorsAndTokenExhaustionDoNotBecomeSuccessfulReports() {
        try { wire(AiProvider.OPENAI).parse(401, "secret reflected response"); fail()
        } catch (error: AiAnalysisException) { assertEquals(AiProblem.AUTH, error.apiProblem); assertFalse(error.toString().contains("secret")) }
        try { wire(AiProvider.OPENAI).parse(200, """{"choices":[{"finish_reason":"length","message":{"content":"partial"}}]}"""); fail()
        } catch (error: AiAnalysisException) { assertEquals(AiAnalysisError.OUTPUT_LIMIT, error.reason) }
        try { wire(AiProvider.GEMINI).parse(200, "not json"); fail()
        } catch (error: AiAnalysisException) { assertEquals(AiProblem.RESPONSE, error.apiProblem) }
    }
    @Test fun requestsAreBoundedAndNoCredentialsEnterMessageBody() {
        for (provider in listOf(AiProvider.OPENAI, AiProvider.ANTHROPIC, AiProvider.GEMINI)) {
            val body = JSONObject(wire(provider).request(true).body)
            val budget = when (provider) {
                AiProvider.OPENAI -> body.getInt("max_completion_tokens")
                AiProvider.ANTHROPIC -> body.getInt("max_tokens")
                else -> body.getJSONObject("generationConfig").getInt("maxOutputTokens")
            }
            assertEquals(2048, budget)
            assertFalse(body.has("api_key"))
        }
    }
}
