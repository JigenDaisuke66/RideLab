package com.isaac.ridelab.ai

import java.net.URI
import java.util.Locale

/** Local HTTP policy shared by validation and the final network boundary. No DNS lookups. */
internal object AiEndpointPolicy {
    fun baseUrl(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        val uri = try { URI(trimmed) } catch (_: Exception) { return input }
        // Only normalize the unambiguous compatible-API model-list URL, never query parameters.
        return if (uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.orEmpty().endsWith("/v1/models")) trimmed.removeSuffix("/models") else input
    }

    fun problem(url: String, allowLocalHttp: Boolean, basePath: Boolean = true): AiProblem? {
        val uri = try { URI(url) } catch (_: Exception) { return AiProblem.URL }
        if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null || uri.port !in -1..65535 || uri.port == 0 ||
            (basePath && uri.rawPath.orEmpty().contains('%')) ||
            uri.path.orEmpty().split('/').any { it == "." || it == ".." }) return AiProblem.URL
        if (uri.scheme == "http" && (!allowLocalHttp || !isLocalHost(uri.host))) return AiProblem.LOCAL_HTTP
        return null
    }

    private fun isLocalHost(host: String): Boolean {
        val name = host.lowercase(Locale.ROOT)
        if (name == "localhost" || name == "127.0.0.1" || (name.endsWith(".local") && name.length > 6)) return true
        return privateIpv4(name)
    }

    fun privateIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { part -> part.isEmpty() || part.length > 3 ||
                part.any { it !in '0'..'9' } || (part.length > 1 && part[0] == '0') || part.toInt() !in 0..255 }) return false
        val octets = parts.map(String::toInt)
        return octets[0] == 10 || (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }
}
