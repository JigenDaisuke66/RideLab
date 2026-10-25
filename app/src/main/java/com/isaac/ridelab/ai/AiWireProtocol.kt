package com.isaac.ridelab.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** A credential-bearing wire request. Deliberately not a data class with a generated toString. */
internal class AiWireRequest(val url: String, val headers: Map<String, String>, val body: String)

internal object AiWireProtocol {
    fun request(config: AiConfiguration, prompt: String): AiWireRequest {
        require(config.problem() == null)
        val base = config.baseUrl.trim().trimEnd('/')
        val headers = mutableMapOf("Content-Type" to "application/json", "Accept" to "application/json")
        val message = JSONObject().put("role", "user").put("content", prompt)
        val body = JSONObject()
        val path = when (config.provider.protocol) {
            AiProtocol.CHAT_COMPLETIONS -> {
                if (config.apiKey.isNotEmpty()) headers["Authorization"] = "Bearer ${config.apiKey}"
                body.put("model", config.model).put("messages", JSONArray().put(message)).put("stream", false)
                body.put(if (config.provider == AiProvider.OPENAI) "max_completion_tokens" else "max_tokens", 256)
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
        return AiWireRequest(base + path, headers, body.toString())
    }

    /** HTTP success alone is insufficient: require an actual non-empty assistant text response. */
    fun response(protocol: AiProtocol, status: Int, body: String): AiProbeResult {
        if (status !in 200..299) return AiProbeResult(when (status) {
            401, 403 -> AiProblem.AUTH
            429 -> AiProblem.RATE_LIMIT
            else -> AiProblem.HTTP
        }, status)
        return try {
            val json = JSONObject(body)
            val text = when (protocol) {
                AiProtocol.CHAT_COMPLETIONS -> json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").opt("content") as? String
                AiProtocol.ANTHROPIC_MESSAGES -> textParts(json.getJSONArray("content"), "text", typed = true)
                AiProtocol.GEMINI -> textParts(json.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts"), "text")
            }
            AiProbeResult(if (text.isNullOrBlank() || json.has("error")) AiProblem.RESPONSE else null, status)
        } catch (_: Exception) { AiProbeResult(AiProblem.RESPONSE, status) }
    }

    private fun textParts(parts: JSONArray, field: String, typed: Boolean = false): String = buildString {
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought", false)) continue
            if (!typed || part.optString("type") == "text") append(part.opt(field) as? String ?: "")
        }
    }
}
