package com.isaac.ridelab.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** A credential-bearing wire request. Deliberately not a data class with a generated toString. */
internal class AiWireRequest(val url: String, val headers: Map<String, String>, val body: String,
    val allowLocalHttp: Boolean = false, val method: String = "POST")

internal object AiWireProtocol {
    fun request(config: AiConfiguration, prompt: String): AiWireRequest {
        require(config.problem() == null)
        val base = AiEndpointPolicy.baseUrl(config.baseUrl).trim().trimEnd('/')
        val headers = mutableMapOf("Content-Type" to "application/json", "Accept" to "application/json")
        val message = JSONObject().put("role", "user").put("content", prompt)
        val body = JSONObject()
        val path = when (config.provider.protocol) {
            AiProtocol.CHAT_COMPLETIONS -> {
                if (config.apiKey.isNotEmpty()) headers["Authorization"] = "Bearer ${config.apiKey}"
                body.put("model", config.model).put("messages", JSONArray().put(message)).put("stream", false)
                if (config.provider.permitsLocalHttp) {
                    body.put("stream", true).put("stream_options", JSONObject().put("include_usage", true))
                }
                // Local reasoning models may spend the short probe budget entirely on thinking.
                body.put(if (config.provider == AiProvider.OPENAI) "max_completion_tokens" else "max_tokens",
                    if (config.provider.permitsLocalHttp) 2048 else 256)
                if (config.provider == AiProvider.OPENAI) body.put("store", false)
                "/chat/completions"
            }
            AiProtocol.ANTHROPIC_MESSAGES -> {
                headers["x-api-key"] = config.apiKey
                headers["anthropic-version"] = "2023-06-01"
                body.put("model", config.model).put("max_tokens", 256)
                    .put("messages", JSONArray().put(message))
                "/messages"
            }
            AiProtocol.GEMINI -> {
                headers["x-goog-api-key"] = config.apiKey
                body.put("contents", JSONArray().put(JSONObject().put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                    .put("generationConfig", JSONObject().put("maxOutputTokens", 256))
                val model = URLEncoder.encode(config.model.removePrefix("models/"), "UTF-8")
                "/models/$model:generateContent"
            }
        }
        return AiWireRequest(base + path, headers, body.toString(), config.provider.permitsLocalHttp && config.allowLocalHttp)
    }

    fun modelsRequest(config: AiConfiguration): AiWireRequest {
        require(config.problem() == null && config.provider.protocol == AiProtocol.CHAT_COMPLETIONS)
        val headers = mutableMapOf("Accept" to "application/json")
        if (config.apiKey.isNotEmpty()) headers["Authorization"] = "Bearer ${config.apiKey}"
        return AiWireRequest(AiEndpointPolicy.baseUrl(config.baseUrl).trim().trimEnd('/') + "/models",
            headers, "", config.provider.permitsLocalHttp && config.allowLocalHttp, method = "GET")
    }

    fun modelIds(body: String): List<String> {
        val data = JSONObject(body).getJSONArray("data")
        require(data.length() <= 1000)
        return (0 until data.length()).map { data.getJSONObject(it).getString("id").also { id ->
            require(id.isNotBlank() && id.length <= 200 && id.none(Char::isISOControl))
        } }.distinct()
    }

    /** HTTP success alone is insufficient: require an actual non-empty assistant text response. */
    fun response(protocol: AiProtocol, status: Int, body: String): AiProbeResult {
        if (status !in 200..299) return AiProbeResult(httpProblem(status, body), status)
        return try {
            val json = JSONObject(body)
            if (json.has("error")) return AiProbeResult(AiProblem.RESPONSE, status)
            var failure: AiProblem? = null
            val text = when (protocol) {
                AiProtocol.CHAT_COMPLETIONS -> {
                    val choice = json.getJSONArray("choices").getJSONObject(0)
                    val message = choice.getJSONObject("message")
                    val content = message.opt("content") as? String
                    failure = when {
                        choice.optString("finish_reason") == "length" -> AiProblem.OUTPUT_LIMIT
                        content.isNullOrBlank() && listOf("reasoning", "reasoning_content").any {
                            (message.opt(it) as? String)?.isNotBlank() == true } -> AiProblem.REASONING_ONLY
                        content.isNullOrBlank() -> AiProblem.EMPTY_RESPONSE
                        else -> null
                    }
                    content
                }
                AiProtocol.ANTHROPIC_MESSAGES -> {
                    if (json.optString("stop_reason") == "max_tokens") failure = AiProblem.OUTPUT_LIMIT
                    textParts(json.getJSONArray("content"), "text", typed = true)
                }
                AiProtocol.GEMINI -> {
                    val candidate = json.getJSONArray("candidates").getJSONObject(0)
                    if (candidate.optString("finishReason") == "MAX_TOKENS") failure = AiProblem.OUTPUT_LIMIT
                    textParts(candidate.getJSONObject("content").getJSONArray("parts"), "text")
                }
            }
            if (json.has("error")) failure = AiProblem.RESPONSE
            else if (text.isNullOrBlank() && failure == null) failure = AiProblem.EMPTY_RESPONSE
            AiProbeResult(failure, status, reply = if (failure == null) text?.trim() else null)
        } catch (_: Exception) { AiProbeResult(AiProblem.JSON, status) }
    }

    fun httpProblem(status: Int, body: String): AiProblem {
        // Classify a bounded error envelope, never display or log raw server text.
        val error = try { JSONObject(body).optJSONObject("error") } catch (_: Exception) { null }
        val message = error?.optString("message").orEmpty().lowercase()
        if (error?.optString("code") in listOf("model_not_found", "ModelNotFound") ||
            (status in listOf(400, 404) && message.contains("model") &&
                listOf("does not exist", "not found", "unknown model").any(message::contains))) return AiProblem.MODEL_NOT_FOUND
        return when (status) {
            401, 403 -> AiProblem.AUTH
            429 -> AiProblem.RATE_LIMIT
            else -> AiProblem.HTTP
        }
    }

    private fun textParts(parts: JSONArray, field: String, typed: Boolean = false): String = buildString {
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought", false)) continue
            if (!typed || part.optString("type") == "text") append(part.opt(field) as? String ?: "")
        }
    }
}
