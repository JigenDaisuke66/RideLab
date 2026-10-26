package com.isaac.ridelab.ai

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiChatStreamDecoderTest {
    private fun read(vararg data: String) = JSONObject(AiChatStreamDecoder().read(data.joinToString("") { "data: $it\r\n\r\n" }.reader()))
    @Test fun assemblesTextReasoningAndFinalUsageWithoutExposingReasoningAsAnswer() {
        val json = read("""{"choices":[{"index":0,"delta":{"reasoning_content":"private thinking"},"finish_reason":null}]}""",
            """{"choices":[{"index":0,"delta":{"content":"功率 "},"finish_reason":null}]}""",
            """{"choices":[{"index":0,"delta":{"content":"100 W"},"finish_reason":"stop"}]}""",
            """{"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":20}}""", "[DONE]")
        val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        assertEquals("功率 100 W", message.getString("content"))
        assertEquals("private thinking", message.getString("reasoning_content"))
        assertEquals(20, json.getJSONObject("usage").getInt("completion_tokens"))
    }
    @Test fun fragmentedToolArgumentsNamesAndIdsAreCompleteBeforeParsingOrExecution() {
        val json = read("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call","type":"function","function":{"name":"query_","arguments":"{\"sql\":\"SELECT "}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"1","function":{"name":"sql","arguments":"1\"}"}}]},"finish_reason":"tool_calls"}]}""", "[DONE]")
        val call = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getJSONArray("tool_calls").getJSONObject(0)
        assertEquals("call1", call.getString("id"))
        assertEquals("query_sql", call.getJSONObject("function").getString("name"))
        assertEquals("SELECT 1", JSONObject(call.getJSONObject("function").getString("arguments")).getString("sql"))
    }
    @Test fun heartbeatsAndMultiLineDataAreAccepted() {
        val stream = ": keep-alive\n\nevent: message\ndata: {\ndata: \"choices\":[{\"delta\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
        assertTrue(AiChatStreamDecoder().read(stream.reader()).contains("OK"))
    }
    @Test fun interruptedMissingFinishInvalidJsonAndOversizedStreamsFail() {
        listOf("data: {\"choices\":[]}\n\n", "data: [DONE]\n\n", "data: not-json\n\n",
            "data: "+"x".repeat(70_000)).forEach { stream ->
            var rejected = false
            try { AiChatStreamDecoder().read(stream.reader()) } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
        }
    }
    @Test fun outputExhaustionIsPreservedForTheExistingParser() {
        val json = read("""{"choices":[{"delta":{"reasoning_content":"budget used"},"finish_reason":"length"}]}""", "[DONE]")
        assertEquals("length", json.getJSONArray("choices").getJSONObject(0).getString("finish_reason"))
    }
}
