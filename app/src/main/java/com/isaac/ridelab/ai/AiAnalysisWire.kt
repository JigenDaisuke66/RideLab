package com.isaac.ridelab.ai

import com.isaac.ridelab.ai.analysis.*
import org.json.JSONArray
import org.json.JSONObject

/** Native tool envelopes, preserving reasoning_content / thought signatures during tool replay. */
internal class AiAnalysisWire(private val config: AiConfiguration, private val system: String, question: String) {
    private val messages = JSONArray()
    init {
        when (config.provider.protocol) {
            AiProtocol.CHAT_COMPLETIONS -> messages.put(JSONObject().put("role", "system").put("content", system))
            else -> Unit
        }
        messages.put(when (config.provider.protocol) {
            AiProtocol.GEMINI -> JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", question)))
            else -> JSONObject().put("role", "user").put("content", question)
        })
    }

    fun request(allowTools: Boolean): AiWireRequest {
        val base = AiWireProtocol.request(config, "")
        val body = JSONObject(base.body)
        when (config.provider.protocol) {
            AiProtocol.CHAT_COMPLETIONS -> {
                body.put("messages", messages).put(if (config.provider == AiProvider.OPENAI) "max_completion_tokens" else "max_tokens", 2048)
                if (allowTools) body.put("tools", tools(AiProtocol.CHAT_COMPLETIONS))
                if (config.provider == AiProvider.KIMI) {
                    body.put("stream", true).put("stream_options", JSONObject().put("include_usage", true))
                    // Keep tool definitions and complete assistant reasoning during final-turn replay.
                    body.put("tools", tools(AiProtocol.CHAT_COMPLETIONS)).put("tool_choice", if (allowTools) "auto" else "none")
                    if (config.model.lowercase(java.util.Locale.ROOT).startsWith("kimi-k3")) {
                        // K3 always thinks; its documented default is max. Bounded data analysis uses low.
                        body.remove("max_tokens")
                        body.put("max_completion_tokens", 2048).put("reasoning_effort", "low")
                    }
                }
            }
            AiProtocol.ANTHROPIC_MESSAGES -> {
                body.put("messages", messages).put("system", system).put("max_tokens", 2048)
                if (allowTools) body.put("tools", tools(AiProtocol.ANTHROPIC_MESSAGES))
            }
            AiProtocol.GEMINI -> {
                body.put("contents", messages).put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                body.put("generationConfig", JSONObject().put("maxOutputTokens", 2048))
                if (allowTools) body.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools(AiProtocol.GEMINI))))
            }
        }
        if (body.toString().length > 60_000) throw AiAnalysisException(AiAnalysisError.BUDGET)
        return AiWireRequest(base.url, if (config.provider == AiProvider.KIMI)
            base.headers + ("Accept" to "text/event-stream") else base.headers, body.toString())
    }

    fun parse(status: Int, body: String): AiAnalysisReply {
        if (status !in 200..299) {
            val failure = AiWireProtocol.response(config.provider.protocol, status, "")
            throw AiAnalysisException(AiAnalysisError.API, failure.problem, status)
        }
        try {
            val json = JSONObject(body)
            if (json.has("error")) throw AiAnalysisException(AiAnalysisError.API, AiProblem.RESPONSE, status)
            val calls = mutableListOf<AiToolCall>()
            val text = StringBuilder()
            val native: JSONObject
            val usage: AiTokenUsage
            fun JSONObject.longOrNull(name: String): Long? = (opt(name) as? Number)?.toLong()?.takeIf { it >= 0 }
            when (config.provider.protocol) {
                AiProtocol.CHAT_COMPLETIONS -> {
                    val choice = json.getJSONArray("choices").getJSONObject(0)
                    if (choice.optString("finish_reason") == "length") throw AiAnalysisException(AiAnalysisError.OUTPUT_LIMIT)
                    native = choice.getJSONObject("message").put("role", "assistant")
                    text.append(native.opt("content") as? String ?: "")
                    val tools = native.optJSONArray("tool_calls") ?: JSONArray()
                    for (i in 0 until tools.length()) {
                        val call = tools.getJSONObject(i)
                        check(call.getString("type") == "function")
                        val function = call.getJSONObject("function")
                        calls += AiToolCall(call.getString("id"), function.getString("name"), JSONObject(function.getString("arguments")))
                    }
                    usage = json.optJSONObject("usage")?.let { AiTokenUsage(it.longOrNull("prompt_tokens"), it.longOrNull("completion_tokens")) } ?: AiTokenUsage()
                }
                AiProtocol.ANTHROPIC_MESSAGES -> {
                    if (json.optString("stop_reason") == "max_tokens") throw AiAnalysisException(AiAnalysisError.OUTPUT_LIMIT)
                    val content = json.getJSONArray("content")
                    native = JSONObject().put("role", "assistant").put("content", content)
                    for (i in 0 until content.length()) {
                        val part = content.getJSONObject(i)
                        when (part.optString("type")) {
                            "text" -> text.append(part.getString("text"))
                            "tool_use" -> calls += AiToolCall(part.getString("id"), part.getString("name"), part.getJSONObject("input"))
                        }
                    }
                    usage = json.optJSONObject("usage")?.let { AiTokenUsage(it.longOrNull("input_tokens"), it.longOrNull("output_tokens")) } ?: AiTokenUsage()
                }
                AiProtocol.GEMINI -> {
                    val candidate = json.getJSONArray("candidates").getJSONObject(0)
                    if (candidate.optString("finishReason") == "MAX_TOKENS") throw AiAnalysisException(AiAnalysisError.OUTPUT_LIMIT)
                    native = candidate.getJSONObject("content")
                    val parts = native.getJSONArray("parts")
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (!part.optBoolean("thought")) text.append(part.opt("text") as? String ?: "")
                        part.optJSONObject("functionCall")?.let { call ->
                            calls += AiToolCall(call.optString("id"), call.getString("name"), call.optJSONObject("args") ?: JSONObject())
                        }
                    }
                    usage = json.optJSONObject("usageMetadata")?.let { AiTokenUsage(it.longOrNull("promptTokenCount"),
                        it.longOrNull("candidatesTokenCount")?.let { output -> output + (it.longOrNull("thoughtsTokenCount") ?: 0) }) } ?: AiTokenUsage()
                }
            }
            check(calls.size <= 6)
            return AiAnalysisReply(text.toString(), calls, usage, native)
        } catch (error: AiAnalysisException) { throw error
        } catch (_: Exception) { throw AiAnalysisException(AiAnalysisError.API, AiProblem.RESPONSE, status) }
    }

    fun addResults(reply: AiAnalysisReply, results: List<String>) {
        check(results.size == reply.calls.size)
        messages.put(checkNotNull(reply.native))
        when (config.provider.protocol) {
            AiProtocol.CHAT_COMPLETIONS -> reply.calls.zip(results).forEach { (call, result) ->
                messages.put(JSONObject().put("role", "tool").put("tool_call_id", call.id).put("content", result))
            }
            AiProtocol.ANTHROPIC_MESSAGES -> messages.put(JSONObject().put("role", "user").put("content", JSONArray().apply {
                reply.calls.zip(results).forEach { (call, result) -> put(JSONObject().put("type", "tool_result")
                    .put("tool_use_id", call.id).put("content", result).put("is_error", JSONObject(result).has("error"))) }
            }))
            AiProtocol.GEMINI -> messages.put(JSONObject().put("role", "user").put("parts", JSONArray().apply {
                reply.calls.zip(results).forEach { (call, result) ->
                    val response = JSONObject().put("name", call.name).put("response", JSONObject(result))
                    if (call.id.isNotBlank()) response.put("id", call.id)
                    put(JSONObject().put("functionResponse", response))
                }
            }))
        }
    }

    private fun tools(protocol: AiProtocol): JSONArray = JSONArray().apply {
        val definitions = listOf(
            Triple("query_sql", "Read-only SQLite SELECT/CTE over ride_sessions, ride_samples, user_profile. Prefer aggregates; max 100 rows. No writes.",
                JSONObject().put("type", "object").put("properties", JSONObject().put("sql", JSONObject().put("type", "string")))
                    .put("required", JSONArray().put("sql"))),
            Triple("ride_metrics", "Exact existing app NP/IF/TSS, best efforts, zones, energy and sensor coverage for one session. Historical FTP/weight only.",
                JSONObject().put("type", "object").put("properties", JSONObject().put("session_id", JSONObject().put("type", "integer")))
                    .put("required", JSONArray().put("session_id"))),
        )
        definitions.forEach { (name, description, parameters) ->
            put(when (protocol) {
                AiProtocol.CHAT_COMPLETIONS -> JSONObject().put("type", "function").put("function", JSONObject()
                    .put("name", name).put("description", description).put("parameters", parameters))
                AiProtocol.ANTHROPIC_MESSAGES -> JSONObject().put("name", name).put("description", description).put("input_schema", parameters)
                AiProtocol.GEMINI -> JSONObject().put("name", name).put("description", description).put("parameters", parameters)
            })
        }
    }
}

enum class AiRequestPhase { STARTED, FINISHED, CANCELLED }
data class AiRequestDiagnostic(val provider: AiProvider, val request: Int, val phase: AiRequestPhase,
    val elapsedMillis: Long = 0, val httpStatus: Int? = null, val problem: AiProblem? = null)

class HttpAiAnalysisModel(private val diagnostic: (AiRequestDiagnostic) -> Unit = {}) : AiAnalysisModel {
    override fun start(configuration: AiConfiguration, system: String, question: String): AiModelConversation {
        configuration.problem()?.let { throw AiAnalysisException(AiAnalysisError.CONFIGURATION, it) }
        val wire = AiAnalysisWire(configuration, system, question)
        return object : AiModelConversation {
            private var request = 0
            override suspend fun next(allowTools: Boolean): AiAnalysisReply {
                val index = ++request
                val start = System.nanoTime()
                diagnostic(AiRequestDiagnostic(configuration.provider, index, AiRequestPhase.STARTED))
                try {
                    val response = AiHttpTransport(AiHttpTimeouts.ANALYSIS).execute(wire.request(allowTools))
                    diagnostic(AiRequestDiagnostic(configuration.provider, index, AiRequestPhase.FINISHED,
                        (System.nanoTime() - start) / 1_000_000, response.status, response.problem))
                    response.problem?.let { throw AiAnalysisException(AiAnalysisError.API, it, response.status) }
                    return wire.parse(response.status ?: 0, response.body)
                } catch (error: kotlinx.coroutines.CancellationException) {
                    diagnostic(AiRequestDiagnostic(configuration.provider, index, AiRequestPhase.CANCELLED,
                        (System.nanoTime() - start) / 1_000_000))
                    throw error
                }
            }
            override fun addResults(reply: AiAnalysisReply, results: List<String>) = wire.addResults(reply, results)
        }
    }
}
