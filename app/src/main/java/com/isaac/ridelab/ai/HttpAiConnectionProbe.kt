package com.isaac.ridelab.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Explicit, bounded probe only. No retries, redirects, ride reads, tools or BLE access. */
class HttpAiConnectionProbe : AiConnectionProbe {
    override suspend fun test(configuration: AiConfiguration, prompt: String): AiProbeResult = withContext(Dispatchers.IO) {
        configuration.problem()?.let { return@withContext AiProbeResult(it) }
        val request = AiWireProtocol.request(configuration, prompt)
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false // Never forward credentials to another endpoint.
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            val bytes = request.body.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            coroutineContext.ensureActive()
            val status = connection.responseCode
            if (status !in 200..299) return@withContext AiWireProtocol.response(configuration.provider.protocol, status, "")
            // Refuse oversized responses; do not log or display untrusted response/error text or headers.
            val data = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                val deadline = System.nanoTime() + 30_000_000_000L
                while (output.size() <= 65_536) {
                    coroutineContext.ensureActive()
                    val remaining = (deadline - System.nanoTime()) / 1_000_000
                    if (remaining <= 0) throw SocketTimeoutException()
                    connection.readTimeout = remaining.coerceAtMost(30_000).toInt().coerceAtLeast(1)
                    val count = stream.read(buffer, 0, minOf(buffer.size, 65_537 - output.size()))
                    if (count == -1) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            coroutineContext.ensureActive()
            if (data.size > 65_536) AiProbeResult(AiProblem.RESPONSE, status)
            else AiWireProtocol.response(configuration.provider.protocol, status, data.toString(Charsets.UTF_8))
        } catch (error: CancellationException) { throw error
        } catch (_: SocketTimeoutException) { AiProbeResult(AiProblem.TIMEOUT)
        } catch (_: IOException) { AiProbeResult(AiProblem.NETWORK)
        } catch (_: SecurityException) { AiProbeResult(AiProblem.NETWORK)
        } finally { connection.disconnect() }
    }
}
