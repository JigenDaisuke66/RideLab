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
import java.net.ConnectException
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

internal class AiHttpResponse(val status: Int? = null, val body: String = "", val problem: AiProblem? = null,
    val diagnostic: String? = null)

/** Shared bounded transport for explicit probes and analysis; never retries or follows redirects. */
internal data class AiHttpTimeouts(val connectMillis: Int = 10_000, val readMillis: Int = 30_000,
    val requestMillis: Long = 60_000) {
    init { require(connectMillis > 0 && readMillis > 0 && requestMillis > 0) }
    companion object { val ANALYSIS = AiHttpTimeouts(readMillis = 120_000, requestMillis = 120_000) }
}

internal class AiHttpTransport(private val timeouts: AiHttpTimeouts = AiHttpTimeouts()) {
    suspend fun execute(request: AiWireRequest): AiHttpResponse = withContext(Dispatchers.IO) {
        // Recheck before opening a connection or sending credentials, not just when saving settings.
        AiEndpointPolicy.problem(request.url, request.allowLocalHttp, basePath = false)?.let {
            return@withContext AiHttpResponse(problem = it)
        }
        val connection = URL(request.url).openConnection() as HttpURLConnection
        var responseStatus: Int? = null
        val expired = AtomicBoolean(false)
        val deadline = System.nanoTime() + timeouts.requestMillis * 1_000_000
        // Cancellation and the request deadline close a blocking header/body read as well.
        val closer = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try { delay(timeouts.requestMillis); expired.set(true) }
            finally { connection.disconnect() }
        }
        try {
            val uri = URI(request.url)
            if (uri.scheme == "http" && (uri.host == "localhost" || uri.host.endsWith(".local", true))) {
                if (!InetAddress.getAllByName(uri.host).all { it.isLoopbackAddress || AiEndpointPolicy.privateIpv4(it.hostAddress.orEmpty()) })
                    return@withContext AiHttpResponse(problem = AiProblem.LOCAL_HTTP)
            }
            connection.instanceFollowRedirects = false
            connection.requestMethod = request.method
            connection.connectTimeout = timeouts.connectMillis
            connection.readTimeout = timeouts.readMillis
            request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (request.method == "POST") {
                connection.doOutput = true
                val bytes = request.body.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            coroutineContext.ensureActive()
            val status = connection.responseCode.also { responseStatus = it }
            if (status in 200..299 && connection.contentType.orEmpty().substringBefore(';').trim().equals("text/event-stream", ignoreCase = true)) {
                val content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    AiChatStreamDecoder().read(reader) {
                        coroutineContext.ensureActive()
                        if (System.nanoTime() >= deadline) throw SocketTimeoutException()
                    }
                }
                coroutineContext.ensureActive()
                return@withContext AiHttpResponse(status, content)
            }
            val stream = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?: return@withContext AiHttpResponse(status = status)
            val data = stream.use {
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
        } catch (error: IOException) { AiHttpResponse(responseStatus, problem = networkProblem(error, expired.get()),
            diagnostic = safeDiagnostic(error, request))
        } catch (error: IllegalArgumentException) { AiHttpResponse(responseStatus, problem = AiProblem.JSON, diagnostic = error.javaClass.simpleName)
        } catch (error: IllegalStateException) { AiHttpResponse(responseStatus, problem = AiProblem.JSON, diagnostic = error.javaClass.simpleName)
        } catch (error: org.json.JSONException) { AiHttpResponse(responseStatus, problem = AiProblem.JSON, diagnostic = error.javaClass.simpleName)
        } catch (error: SecurityException) { AiHttpResponse(responseStatus, problem = AiProblem.NETWORK, diagnostic = safeDiagnostic(error, request))
        } finally { closer.cancel(); connection.disconnect() }
    }

    companion object {
        internal fun networkProblem(error: IOException, expired: Boolean = false): AiProblem {
            val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
            return when {
                expired || causes.any { it is SocketTimeoutException } -> AiProblem.TIMEOUT
                causes.any { it.message.orEmpty().contains("cleartext", true) } -> AiProblem.CLEARTEXT
                causes.any { it is ConnectException || it.message.orEmpty().contains("ECONNREFUSED") } -> AiProblem.CONNECTION_REFUSED
                else -> AiProblem.NETWORK
            }
        }

        private fun safeDiagnostic(error: Throwable, request: AiWireRequest): String {
            var text = generateSequence(error) { it.cause }.take(4)
                .joinToString(" → ") { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
            request.headers.filterKeys { it.equals("Authorization", true) || it.contains("api-key", true) }
                .values.flatMap { listOf(it, it.removePrefix("Bearer ")) }.filter(String::isNotEmpty)
                .forEach { text = text.replace(it, "[redacted]") }
            return text.filter { !it.isISOControl() }.take(500)
        }
    }
}
