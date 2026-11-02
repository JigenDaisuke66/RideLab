package com.isaac.ridelab.ai

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress

class AiHttpTransportTest {
    private fun server(block: (HttpExchange) -> Unit) = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> exchange.use { try { block(it) } catch (_: IOException) { /* Peer cancelled. */ } } }
        start()
    }
    private fun request(server: HttpServer) = AiWireRequest("http://127.0.0.1:${server.address.port}/v1/chat/completions",
        mapOf("Content-Type" to "application/json"), "{}", allowLocalHttp = true)
    private fun HttpExchange.reply(body: String) {
        val bytes = body.toByteArray(); sendResponseHeaders(200, bytes.size.toLong()); responseBody.write(bytes)
    }
    @Test fun analysisWaitBudgetIsIndependentOfConnectionProbe() {
        assertEquals(30_000, AiHttpTimeouts().readMillis)
        assertEquals(120_000, AiHttpTimeouts.ANALYSIS.readMillis)
        assertEquals(120_000L, AiHttpTimeouts.ANALYSIS.requestMillis)
    }
    @Test fun missingConsentAndNonLocalHttpAreRejectedBeforeConnecting() = runBlocking {
        var calls = 0
        val peer = server { calls++; it.reply("unexpected") }
        try {
            val request = AiWireRequest("http://127.0.0.1:${peer.address.port}/v1/chat/completions",
                mapOf("Authorization" to "Bearer synthetic-key"), "{}")
            assertEquals(AiProblem.LOCAL_HTTP, AiHttpTransport().execute(request).problem)
            assertEquals(0, calls)
            listOf("http://example.com/v1/chat/completions", "http://198.51.100.1:8001/v1/chat/completions").forEach {
                assertEquals(AiProblem.LOCAL_HTTP, AiHttpTransport().execute(
                    AiWireRequest(it, emptyMap(), "{}", allowLocalHttp = true)).problem)
            }
        } finally { peer.stop(0) }
    }
    @Test fun slowFirstByteCanOutliveProbeStyleBudgetButFitAnalysisBudget() = runBlocking {
        val peer = server { Thread.sleep(180); it.reply("{\"ok\":true}") }
        try {
            val short = AiHttpTransport(AiHttpTimeouts(readMillis = 40, requestMillis = 2000)).execute(request(peer))
            assertEquals(AiProblem.TIMEOUT, short.problem)
            val longer = AiHttpTransport(AiHttpTimeouts(readMillis = 1500, requestMillis = 2000)).execute(request(peer))
            assertNull(longer.problem); assertEquals(200, longer.status)
        } finally { peer.stop(0) }
    }
    @Test fun realSseChunksReassembleChineseAndUsageWithoutRetries() = runBlocking {
        var calls = 0
        val peer = server {
            calls++; it.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
            it.sendResponseHeaders(200, 0)
            listOf(": ping\n\n", "data: {\"choices\":[{\"delta\":{\"content\":\"功率\"},\"finish_reason\":null}]}\n\n",
                "data: {\"choices\":[{\"delta\":{\"content\":\"100 W\"},\"finish_reason\":\"stop\"}]}\n\n",
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":5}}\n\n", "data: [DONE]\n\n").forEach { frame ->
                // Split UTF-8 across writes too, rather than assuming each packet is a full character.
                frame.toByteArray(Charsets.UTF_8).forEach { byte -> it.responseBody.write(byte.toInt()) }
                it.responseBody.flush()
            }
        }
        try {
            val response = AiHttpTransport(AiHttpTimeouts(readMillis = 1000, requestMillis = 2000)).execute(request(peer))
            assertNull(response.problem); assertEquals(1, calls)
            assertEquals("功率100 W", JSONObject(response.body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"))
            assertEquals(5, JSONObject(response.body).getJSONObject("usage").getInt("completion_tokens"))
        } finally { peer.stop(0) }
    }
    @Test fun interruptedStreamIsNotMistakenForSuccessfulReport() = runBlocking {
        val peer = server {
            it.responseHeaders.add("Content-Type", "text/event-stream"); it.sendResponseHeaders(200, 0)
            it.responseBody.write("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n".toByteArray())
        }
        try { assertEquals(AiProblem.JSON, AiHttpTransport().execute(request(peer)).problem) }
        finally { peer.stop(0) }
    }
    @Test fun transportFailuresKeepDistinctCauses() {
        assertEquals(AiProblem.CLEARTEXT, AiHttpTransport.networkProblem(java.net.UnknownServiceException(
            "CLEARTEXT communication not permitted by network security policy")))
        assertEquals(AiProblem.CONNECTION_REFUSED, AiHttpTransport.networkProblem(java.net.ConnectException("ECONNREFUSED")))
        assertEquals(AiProblem.TIMEOUT, AiHttpTransport.networkProblem(java.net.SocketTimeoutException("read timed out")))
        assertEquals(AiProblem.NETWORK, AiHttpTransport.networkProblem(java.net.UnknownHostException("host.invalid")))
    }
    @Test fun keepalivesDoNotExtendTheOverallRequestDeadlineForever() = runBlocking {
        val peer = server {
            it.responseHeaders.add("Content-Type", "text/event-stream"); it.sendResponseHeaders(200, 0)
            repeat(40) { _ -> it.responseBody.write(": ping\n\n".toByteArray()); it.responseBody.flush(); Thread.sleep(20) }
        }
        try {
            val response = AiHttpTransport(AiHttpTimeouts(readMillis = 1000, requestMillis = 150)).execute(request(peer))
            assertEquals(AiProblem.TIMEOUT, response.problem)
        } finally { peer.stop(0) }
    }
}
