package com.isaac.ridelab.ai.analysis

import com.isaac.ridelab.ai.AiConfiguration
import com.isaac.ridelab.ai.AiProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

enum class AiAnalysisError { CONFIGURATION, API, OUTPUT_LIMIT, BUDGET, NO_EVIDENCE, STORAGE, CANCELLED, TIME_LIMIT, INTERNAL }
class AiAnalysisException(val reason: AiAnalysisError, val apiProblem: AiProblem? = null, val httpStatus: Int? = null) : Exception(reason.name)
data class AiTokenUsage(val input: Long? = null, val output: Long? = null) {
    operator fun plus(other: AiTokenUsage) = AiTokenUsage(
        if (input != null && other.input != null) input + other.input else null,
        if (output != null && other.output != null) output + other.output else null)
}
data class AiToolCall(val id: String, val name: String, val arguments: JSONObject)
data class AiAnalysisReply(val text: String = "", val calls: List<AiToolCall> = emptyList(),
    val usage: AiTokenUsage = AiTokenUsage(), val native: JSONObject? = null)
data class AiToolTrace(val name: String, val arguments: String, val error: String?, val cached: Boolean,
    val returnedRows: Int? = null, val truncated: Boolean = false)
data class AiAnalysisAnswer(val text: String, val traces: List<AiToolTrace>, val usage: AiTokenUsage, val requests: Int)

interface AiModelConversation {
    suspend fun next(allowTools: Boolean): AiAnalysisReply
    fun addResults(reply: AiAnalysisReply, results: List<String>)
}
fun interface AiAnalysisModel {
    fun start(configuration: AiConfiguration, system: String, question: String): AiModelConversation
}

/** One question per run: no hidden chat-history growth, retries, cross-run caches or command tools. */
class AiAnalysisEngine(private val model: AiAnalysisModel, private val data: AiRideData) {
    suspend fun analyze(config: AiConfiguration, system: String, question: String,
        onRequest: (Int, Boolean) -> Unit = { _, _ -> },
        progress: (List<AiToolTrace>) -> Unit = {}): AiAnalysisAnswer {
        val conversation = model.start(config, system, question)
        val traces = mutableListOf<AiToolTrace>()
        val cache = mutableMapOf<String, String>()
        var usage = AiTokenUsage(0, 0)
        var evidence = false
        for (turn in 0 until 5) {
            coroutineContext.ensureActive()
            val allowTools = turn < 4 && traces.size < 6
            onRequest(turn + 1, allowTools)
            val reply = conversation.next(allowTools)
            usage += reply.usage
            if (reply.calls.isEmpty()) {
                if (!evidence) throw AiAnalysisException(AiAnalysisError.NO_EVIDENCE)
                if (reply.text.isBlank()) throw AiAnalysisException(AiAnalysisError.API, AiProblem.RESPONSE)
                return AiAnalysisAnswer(reply.text, traces.toList(), usage, turn + 1)
            }
            if (!allowTools || reply.calls.size > 6 - traces.size) throw AiAnalysisException(AiAnalysisError.BUDGET)
            val results = mutableListOf<String>()
            for (call in reply.calls) {
                coroutineContext.ensureActive()
                val cacheKey = call.name + ":" + call.arguments.toString()
                val cached = cache.containsKey(cacheKey)
                val result = cache[cacheKey] ?: execute(call).also { cache[cacheKey] = it }
                val json = JSONObject(result)
                val error = json.optString("error").takeIf { it.isNotEmpty() }
                // An empty successful query is evidence too: "no recorded rides" must be answerable.
                if (error == null && (json.has("session_id") || json.has("rows"))) evidence = true
                traces += AiToolTrace(call.name, call.arguments.toString(), error, cached,
                    if (json.has("returned_rows")) json.getInt("returned_rows") else null, json.optBoolean("truncated"))
                results += result
                progress(traces.toList())
            }
            conversation.addResults(reply, results)
        }
        throw AiAnalysisException(AiAnalysisError.BUDGET)
    }

    private suspend fun execute(call: AiToolCall): String = try {
        when (call.name) {
            "query_sql" -> {
                val sql = call.arguments.get("sql") as? String
                if (sql == null || !AiSqlPolicy.valid(sql)) toolError("READ_ONLY_SELECT_REQUIRED") else data.query(sql)
            }
            "ride_metrics" -> {
                val id = call.arguments.get("session_id") as? Number
                if (id == null || id.toLong() <= 0 || id.toDouble() != id.toLong().toDouble()) toolError("INVALID_SESSION_ID")
                else data.metrics(id.toLong())
            }
            else -> toolError("UNKNOWN_TOOL")
        }
    } catch (error: CancellationException) { throw error
    } catch (_: Exception) { toolError("TOOL_ERROR_CHECK_ARGUMENTS_OR_DATA") }
}
