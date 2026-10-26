package com.isaac.ridelab.ai

/** Explicit connection probe only. It cannot read ride data or invoke analysis tools. */
class HttpAiConnectionProbe : AiConnectionProbe {
    override suspend fun test(configuration: AiConfiguration, prompt: String): AiProbeResult {
        configuration.problem()?.let { return AiProbeResult(it) }
        val response = AiHttpTransport().execute(AiWireProtocol.request(configuration, prompt))
        response.problem?.let { return AiProbeResult(it, response.status) }
        return AiWireProtocol.response(configuration.provider.protocol, response.status ?: 0, response.body)
    }
}
