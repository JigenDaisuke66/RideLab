package com.isaac.ridelab.ai

import java.net.URI

enum class AiProtocol { CHAT_COMPLETIONS, ANTHROPIC_MESSAGES, GEMINI }

enum class KimiRegion(val baseUrl: String) {
    INTERNATIONAL("https://api.moonshot.ai/v1"), CHINA("https://api.moonshot.cn/v1")
}

/** API transport presets, not model capabilities or permission to control a trainer. */
enum class AiProvider(val protocol: AiProtocol, val baseUrl: String, val requiresKey: Boolean = true) {
    OPENAI(AiProtocol.CHAT_COMPLETIONS, "https://api.openai.com/v1"),
    ANTHROPIC(AiProtocol.ANTHROPIC_MESSAGES, "https://api.anthropic.com/v1"),
    GEMINI(AiProtocol.GEMINI, "https://generativelanguage.googleapis.com/v1beta"),
    DEEPSEEK(AiProtocol.CHAT_COMPLETIONS, "https://api.deepseek.com"),
    QWEN(AiProtocol.CHAT_COMPLETIONS, "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    KIMI(AiProtocol.CHAT_COMPLETIONS, "https://api.moonshot.ai/v1"),
    GLM(AiProtocol.CHAT_COMPLETIONS, "https://open.bigmodel.cn/api/paas/v4"),
    SILICONFLOW(AiProtocol.CHAT_COMPLETIONS, "https://api.siliconflow.cn/v1"),
    OPENROUTER(AiProtocol.CHAT_COMPLETIONS, "https://openrouter.ai/api/v1"),
    OLLAMA(AiProtocol.CHAT_COMPLETIONS, "http://127.0.0.1:11434/v1", false),
    LM_STUDIO(AiProtocol.CHAT_COMPLETIONS, "http://127.0.0.1:1234/v1", false),
    CUSTOM(AiProtocol.CHAT_COMPLETIONS, "", false);

    val permitsLocalHttp: Boolean get() = this in setOf(OLLAMA, LM_STUDIO, CUSTOM)
}

data class AiConfiguration(
    val provider: AiProvider = AiProvider.OPENAI,
    val baseUrl: String = provider.baseUrl,
    val model: String = "",
    val apiKey: String = "",
    val allowLocalHttp: Boolean = false,
) {
    // Configuration must never leak credentials through diagnostic interpolation.
    override fun toString() = "AiConfiguration(provider=$provider, credentials=redacted)"
}

data class AiPreferences(
    val selected: AiProvider = AiProvider.OPENAI,
    val configurations: Map<AiProvider, AiConfiguration> = emptyMap(),
) {
    fun configuration(provider: AiProvider = selected) = configurations[provider] ?: AiConfiguration(provider)
}

enum class AiProblem { URL, LOCAL_HTTP, MODEL, KEY, STORAGE, NETWORK, TIMEOUT, AUTH, RATE_LIMIT, HTTP, RESPONSE }

/** HTTP exceptions must match network_security_config; cloud providers always require HTTPS. */
fun AiConfiguration.problem(): AiProblem? {
    val uri = try { URI(baseUrl.trim()) } catch (_: Exception) { return AiProblem.URL }
    if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
        uri.rawQuery != null || uri.rawFragment != null || uri.port !in -1..65535 || uri.port == 0 ||
        uri.rawPath.orEmpty().contains('%') || uri.path.orEmpty().split('/').any { it == "." || it == ".." }) return AiProblem.URL
    if (uri.scheme == "http" && (!provider.permitsLocalHttp || !allowLocalHttp || !isLocalHttpHost(uri.host)))
        return AiProblem.LOCAL_HTTP
    if (model.isBlank() || model.length > 200 || model.any { it.isWhitespace() || it.isISOControl() } ||
        model.contains("..") || model.any { it !in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.:/@" }) return AiProblem.MODEL
    if ((provider.requiresKey && apiKey.isBlank()) || apiKey.length > 8192 ||
        apiKey.any { it <= ' ' || it > '~' }) return AiProblem.KEY
    return null
}

private fun isLocalHttpHost(host: String): Boolean = host.lowercase() in setOf("localhost", "127.0.0.1", "10.0.2.2") ||
    (host.lowercase().endsWith(".local") && host.length > 6)

data class AiProbeResult(val problem: AiProblem? = null, val httpStatus: Int? = null)

interface AiSettingsStore {
    suspend fun read(): AiPreferences
    suspend fun save(preferences: AiPreferences)
}

fun interface AiConnectionProbe {
    suspend fun test(configuration: AiConfiguration, prompt: String): AiProbeResult
}
