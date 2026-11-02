package com.isaac.ridelab.ai

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

enum class AiProblem { URL, LOCAL_HTTP, MODEL, KEY, STORAGE, NETWORK, TIMEOUT, AUTH, RATE_LIMIT, HTTP, RESPONSE,
    CLEARTEXT, CONNECTION_REFUSED, MODEL_NOT_FOUND, JSON, OUTPUT_LIMIT, EMPTY_RESPONSE, REASONING_ONLY }

/** Cloud providers require HTTPS; local HTTP is restricted and explicitly authorized. */
fun AiConfiguration.problem(): AiProblem? {
    AiEndpointPolicy.problem(AiEndpointPolicy.baseUrl(baseUrl).trim(),
        provider.permitsLocalHttp && allowLocalHttp)?.let { return it }
    if (model.isBlank() || model.length > 200 || model.any { it.isWhitespace() || it.isISOControl() } ||
        model.contains("..") || model.any { it !in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.:/@" }) return AiProblem.MODEL
    if ((provider.requiresKey && apiKey.isBlank()) || apiKey.length > 8192 ||
        apiKey.any { it <= ' ' || it > '~' }) return AiProblem.KEY
    return null
}

data class AiProbeResult(val problem: AiProblem? = null, val httpStatus: Int? = null,
    val reply: String? = null, val models: List<String> = emptyList(), val diagnostic: String? = null)

interface AiSettingsStore {
    suspend fun read(): AiPreferences
    suspend fun save(preferences: AiPreferences)
}

fun interface AiConnectionProbe {
    suspend fun test(configuration: AiConfiguration, prompt: String): AiProbeResult
}
