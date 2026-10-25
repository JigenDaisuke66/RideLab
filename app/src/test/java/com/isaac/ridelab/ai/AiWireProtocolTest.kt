package com.isaac.ridelab.ai

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiWireProtocolTest {
    private fun config(provider: AiProvider) = AiConfiguration(provider, model = "test-model", apiKey = "test-key")

    @Test fun compatibleProvidersKeepTheirBasePathsAndBearerAuthentication() {
        AiProvider.entries.filter { it.protocol == AiProtocol.CHAT_COMPLETIONS && it.requiresKey }.forEach {
            val request = AiWireProtocol.request(config(it).copy(baseUrl = it.baseUrl + "/"), "test \"quoted\"\n你好")
            assertEquals(it.baseUrl + "/chat/completions", request.url)
            assertEquals("Bearer test-key", request.headers["Authorization"])
            val body = JSONObject(request.body)
            assertEquals("test-model", body.getString("model"))
            assertFalse(body.getBoolean("stream"))
            assertEquals("test \"quoted\"\n你好", body.getJSONArray("messages").getJSONObject(0).getString("content"))
            assertFalse(request.url.contains("test-key"))
            assertFalse(body.has("tools"))
        }
    }

    @Test fun openAiUsesCompletionBudgetAndDisablesStorage() {
        val body = JSONObject(AiWireProtocol.request(config(AiProvider.OPENAI), "probe").body)
        assertEquals(256, body.getInt("max_completion_tokens"))
        assertFalse(body.has("max_tokens"))
        assertFalse(body.getBoolean("store"))
    }

    @Test fun claudeUsesMessagesAndVersionHeader() {
        val request = AiWireProtocol.request(config(AiProvider.ANTHROPIC), "probe")
        assertEquals("https://api.anthropic.com/v1/messages", request.url)
        assertEquals("test-key", request.headers["x-api-key"])
        assertEquals("2023-06-01", request.headers["anthropic-version"])
        assertEquals(256, JSONObject(request.body).getInt("max_tokens"))
        assertNull(request.headers["Authorization"])
    }

    @Test fun geminiUsesHeaderKeyNotQueryAndEncodesModelPath() {
        val request = AiWireProtocol.request(config(AiProvider.GEMINI).copy(model = "models/gemini-model"), "probe")
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-model:generateContent", request.url)
        assertEquals("test-key", request.headers["x-goog-api-key"])
        val body = JSONObject(request.body)
        assertEquals("probe", body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
            .getJSONObject(0).getString("text"))
        assertEquals(256, body.getJSONObject("generationConfig").getInt("maxOutputTokens"))
    }

    @Test fun unauthenticatedLocalServerDoesNotReceiveFakeCredential() {
        val request = AiWireProtocol.request(AiConfiguration(AiProvider.OLLAMA, model = "local:8b", allowLocalHttp = true), "probe")
        assertFalse(request.headers.containsKey("Authorization"))
    }

    @Test fun eachProtocolRequiresActualTextAndSkipsThoughtsOrToolOnlyResponses() {
        val valid = mapOf(
            AiProtocol.CHAT_COMPLETIONS to """{"choices":[{"message":{"content":"OK"}}]}""",
            AiProtocol.ANTHROPIC_MESSAGES to """{"content":[{"type":"thinking","thinking":"..."},{"type":"text","text":"OK"}]}""",
            AiProtocol.GEMINI to """{"candidates":[{"content":{"parts":[{"thought":true,"text":"thinking"},{"text":"OK"}]}}]}""",
        )
        valid.forEach { (protocol, body) -> assertNull(AiWireProtocol.response(protocol, 200, body).problem) }
        listOf("{}", "not json", "<html>OK</html>", """{"error":{"message":"failed"}}""").forEach { body ->
            AiProtocol.entries.forEach { assertEquals(AiProblem.RESPONSE, AiWireProtocol.response(it, 200, body).problem) }
        }
        assertEquals(AiProblem.RESPONSE, AiWireProtocol.response(AiProtocol.CHAT_COMPLETIONS, 200,
            """{"choices":[{"message":{"content":null,"tool_calls":[]}}]}""").problem)
        assertEquals(AiProblem.RESPONSE, AiWireProtocol.response(AiProtocol.GEMINI, 200,
            """{"candidates":[{"content":{"parts":[{"thought":true,"text":"thinking"}]}}]}""").problem)
    }

    @Test fun failuresKeepStructuredStatusWithoutExposingServerErrorOrKeys() {
        mapOf(401 to AiProblem.AUTH, 403 to AiProblem.AUTH, 429 to AiProblem.RATE_LIMIT,
            400 to AiProblem.HTTP, 302 to AiProblem.HTTP, 500 to AiProblem.HTTP).forEach { (status, problem) ->
            val result = AiWireProtocol.response(AiProtocol.CHAT_COMPLETIONS, status, "secret server text")
            assertEquals(problem, result.problem)
            assertEquals(status, result.httpStatus)
            assertFalse(result.toString().contains("secret"))
        }
    }
}
