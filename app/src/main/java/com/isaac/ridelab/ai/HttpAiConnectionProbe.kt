package com.isaac.ridelab.ai

/** Explicit connection probe only. It cannot read ride data or invoke analysis tools. */
class HttpAiConnectionProbe(private val log: (String, Int?, AiProblem?) -> Unit = { _, _, _ -> }) : AiConnectionProbe {
    override suspend fun test(configuration: AiConfiguration, prompt: String): AiProbeResult {
        configuration.problem()?.let { return AiProbeResult(it) }
        val transport = AiHttpTransport(if (configuration.provider.permitsLocalHttp) AiHttpTimeouts.ANALYSIS else AiHttpTimeouts())
        var models = emptyList<String>()
        if (configuration.provider == AiProvider.CUSTOM) {
            val listing = transport.execute(AiWireProtocol.modelsRequest(configuration))
            log("GET models", listing.status, listing.problem)
            listing.problem?.let { return AiProbeResult(it, listing.status, diagnostic = listing.diagnostic) }
            if (listing.status !in 200..299) return AiWireProtocol.response(configuration.provider.protocol,
                listing.status ?: 0, listing.body)
            models = try { AiWireProtocol.modelIds(listing.body) }
                catch (_: Exception) { return AiProbeResult(AiProblem.JSON, listing.status) }
            if (configuration.model !in models) return AiProbeResult(AiProblem.MODEL_NOT_FOUND, listing.status, models = models)
        }
        val response = transport.execute(AiWireProtocol.request(configuration, prompt))
        val result = response.problem?.let { AiProbeResult(it, response.status, diagnostic = response.diagnostic) }
            ?: AiWireProtocol.response(configuration.provider.protocol, response.status ?: 0, response.body)
        log("POST chat/completions", response.status, result.problem)
        return result.copy(models = models)
    }
}
