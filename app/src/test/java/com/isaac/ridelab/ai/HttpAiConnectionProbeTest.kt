package com.isaac.ridelab.ai

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

/** Real loopback HTTP with synthetic responses; never contacts a model provider. */
class HttpAiConnectionProbeTest {
    private fun server(block: (HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange -> exchange.use { block(it) } }
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
                assertEquals(AiProblem.RESPONSE, HttpAiConnectionProbe().test(config(server), "probe").problem)
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
}
