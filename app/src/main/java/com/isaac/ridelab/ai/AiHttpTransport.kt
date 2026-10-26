package com.isaac.ridelab.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineStart
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

internal class AiHttpResponse(val status: Int? = null, val body: String = "", val problem: AiProblem? = null)

/** Shared bounded transport for explicit probes and analysis; never retries or follows redirects. */
internal data class AiHttpTimeouts(val connectMillis: Int = 10_000, val readMillis: Int = 30_000,
    val requestMillis: Long = 60_000) {
    init { require(connectMillis > 0 && readMillis > 0 && requestMillis > 0) }
    companion object { val ANALYSIS = AiHttpTimeouts(readMillis = 120_000, requestMillis = 120_000) }
}

internal class AiHttpTransport(private val timeouts: AiHttpTimeouts = AiHttpTimeouts()) {
    suspend fun execute(request: AiWireRequest): AiHttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        val expired = AtomicBoolean(false)
        val deadline = System.nanoTime() + timeouts.requestMillis * 1_000_000
        // Cancellation and the request deadline close a blocking header/body read as well.
        val closer = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try { delay(timeouts.requestMillis); expired.set(true) }
            finally { connection.disconnect() }
        }
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = timeouts.connectMillis
            connection.readTimeout = timeouts.readMillis
            connection.doOutput = true
            request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            val bytes = request.body.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            coroutineContext.ensureActive()
            val status = connection.responseCode
            if (status !in 200..299) return@withContext AiHttpResponse(status = status)
            if (connection.contentType.orEmpty().substringBefore(';').trim().equals("text/event-stream", ignoreCase = true)) {
                val content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    AiChatStreamDecoder().read(reader) {
                        coroutineContext.ensureActive()
                        if (System.nanoTime() >= deadline) throw SocketTimeoutException()
                    }
                }
                coroutineContext.ensureActive()
                return@withContext AiHttpResponse(status, content)
            }
            val data = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (output.size() <= 65_536) {
                    coroutineContext.ensureActive()
                    val remaining = (deadline - System.nanoTime()) / 1_000_000
                    if (remaining <= 0) throw SocketTimeoutException()
                    connection.readTimeout = remaining.coerceAtMost(timeouts.readMillis.toLong()).toInt().coerceAtLeast(1)
                    val count = stream.read(buffer, 0, minOf(buffer.size, 65_537 - output.size()))
                    if (count == -1) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            coroutineContext.ensureActive()
            if (data.size > 65_536) AiHttpResponse(status, problem = AiProblem.RESPONSE)
            else AiHttpResponse(status, data.toString(Charsets.UTF_8))
        } catch (error: CancellationException) { throw error
        } catch (_: SocketTimeoutException) { AiHttpResponse(problem = AiProblem.TIMEOUT)
        } catch (_: IOException) { AiHttpResponse(problem = if (expired.get()) AiProblem.TIMEOUT else AiProblem.NETWORK)
        } catch (_: IllegalArgumentException) { AiHttpResponse(problem = AiProblem.RESPONSE)
        } catch (_: IllegalStateException) { AiHttpResponse(problem = AiProblem.RESPONSE)
        } catch (_: org.json.JSONException) { AiHttpResponse(problem = AiProblem.RESPONSE)
        } catch (_: SecurityException) { AiHttpResponse(problem = AiProblem.NETWORK)
        } finally { closer.cancel(); connection.disconnect() }
    }
}
