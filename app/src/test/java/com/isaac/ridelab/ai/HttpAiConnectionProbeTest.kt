package com.isaac.ridelab.ai

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

/** Real loopback HTTP with synthetic responses; never contacts a model provider. */
class HttpAiConnectionProbeTest {
    private fun server(modelId: String = "local-model", block: (HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange -> exchange.use {
                if (it.requestMethod == "GET" && it.requestURI.path == "/v1/models")
                    it.respond(200, """{"data":[{"id":"$modelId"}]}""") else block(it)
            } }
            start()
        }
    private fun config(server: HttpServer) = AiConfiguration(AiProvider.CUSTOM,
        "http://127.0.0.1:${server.address.port}/v1", "local-model", "test-key", allowLocalHttp = true)
    private fun HttpExchange.respond(status: Int, text: String) {
        val bytes = text.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.write(bytes)
    }

    @Test fun explicitTestPostsOnlyTheProbeWithExpectedHeaders() = runBlocking {
        var requests = 0
        var requestBody = ""
        var path = ""
        var authorization: String? = null
        val server = server {
            requests++; path = it.requestURI.path
            authorization = it.requestHeaders.getFirst("Authorization")
            requestBody = it.requestBody.readBytes().toString(Charsets.UTF_8)
            it.respond(200, """{"choices":[{"message":{"content":"OK"}}]}""")
        }
        try {
            val result = HttpAiConnectionProbe().test(config(server), "probe-only")
            assertNull(result.problem)
            assertEquals(1, requests)
            assertEquals("/v1/chat/completions", path)
            assertEquals("Bearer test-key", authorization)
            assertTrue(requestBody.contains("probe-only"))
            assertFalse(requestBody.contains("test-key"))
            assertFalse(requestBody.contains("ride"))
        } finally { server.stop(0) }
    }

    @Test fun redirectsAreNotFollowedAndCannotForwardCredentials() = runBlocking {
        var calls = 0
        val server = server {
            calls++
            it.responseHeaders.add("Location", "/redirected")
            it.respond(302, "redirect")
        }
        try {
            val result = HttpAiConnectionProbe().test(config(server), "probe")
            assertEquals(AiProblem.HTTP, result.problem)
            assertEquals(302, result.httpStatus)
            assertEquals(1, calls)
        } finally { server.stop(0) }
    }

    @Test fun malformedSuccessAndOversizedResponsesAreNotSuccessfulConnections() = runBlocking {
        for (body in listOf("<html>login page</html>", "x".repeat(70_000))) {
            val server = server { it.respond(200, body) }
            try {
                assertEquals(if (body.length > 65_536) AiProblem.RESPONSE else AiProblem.JSON,
                    HttpAiConnectionProbe().test(config(server), "probe").problem)
            } finally { server.stop(0) }
        }
    }

    @Test fun httpErrorsDoNotExposeUntrustedResponseText() = runBlocking {
        val server = server { it.respond(401, "reflected test-key secret") }
        try {
            val result = HttpAiConnectionProbe().test(config(server), "probe")
            assertEquals(AiProblem.AUTH, result.problem)
            assertFalse(result.toString().contains("test-key"))
        } finally { server.stop(0) }
    }

    @Test fun rejectedLocalPermissionPreventsAnyNetworkCall() = runBlocking {
        var calls = 0
        val server = server { calls++; it.respond(200, "unexpected") }
        try {
            val result = HttpAiConnectionProbe().test(config(server).copy(allowLocalHttp = false), "probe")
            assertEquals(AiProblem.LOCAL_HTTP, result.problem)
            assertEquals(0, calls)
        } finally { server.stop(0) }
    }

    @Test fun exactModelIdAndAssistantReplyAreVerifiedWithoutAnApiKey() = runBlocking {
        var chats = 0
        val stages = mutableListOf<String>()
        val server = server("qwen-local") {
            chats++
            assertEquals("POST", it.requestMethod)
            assertEquals("/v1/chat/completions", it.requestURI.path)
            assertNull(it.requestHeaders.getFirst("Authorization"))
            val body = org.json.JSONObject(it.requestBody.readBytes().toString(Charsets.UTF_8))
            assertEquals("qwen-local", body.getString("model"))
            assertEquals(2048, body.getInt("max_tokens"))
            it.respond(200, """{"choices":[{"finish_reason":"stop","message":{"content":"OK","reasoning":"hidden"}}]}""")
        }
        try {
            val selected = config(server).copy(model = "qwen-local", apiKey = "")
            val probe = HttpAiConnectionProbe { stage, _, _ -> stages += stage }
            val result = probe.test(selected, "probe-only")
            assertNull(result.problem); assertEquals("OK", result.reply)
            assertEquals(listOf("qwen-local"), result.models)
            assertEquals(listOf("GET models", "POST chat/completions"), stages)
            val wrong = probe.test(selected.copy(model = "qwen"), "probe-only")
            assertEquals(AiProblem.MODEL_NOT_FOUND, wrong.problem)
            assertEquals(listOf("qwen-local"), wrong.models)
            assertEquals(1, chats)
        } finally { server.stop(0) }
    }
}
