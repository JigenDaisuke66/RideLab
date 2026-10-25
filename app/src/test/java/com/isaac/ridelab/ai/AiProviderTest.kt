package com.isaac.ridelab.ai

import org.junit.Assert.*
import org.junit.Test

class AiProviderTest {
    private fun config(provider: AiProvider = AiProvider.OPENAI) = AiConfiguration(provider,
        model = "chat-model", apiKey = if (provider.requiresKey) "test-key" else "")

    @Test fun allCloudPresetsAreValidAndRequireKeys() {
        AiProvider.entries.filter { it.requiresKey }.forEach {
            assertNull(it.name, config(it).problem())
            assertEquals(AiProblem.KEY, config(it).copy(apiKey = "").problem())
            assertEquals(AiProblem.LOCAL_HTTP, config(it).copy(baseUrl = "http://localhost:11434/v1", allowLocalHttp = true).problem())
        }
    }

    @Test fun noProviderInventsAModel() {
        AiProvider.entries.filter { it.requiresKey }.forEach {
            assertEquals(AiProblem.MODEL, AiConfiguration(it).problem())
        }
    }

    @Test fun localHttpRequiresExplicitConsentAndMatchesPlatformExceptions() {
        val local = config(AiProvider.OLLAMA)
        assertEquals(AiProblem.LOCAL_HTTP, local.problem())
        listOf("127.0.0.1", "localhost", "10.0.2.2", "my-computer.local").forEach {
            assertNull(local.copy(baseUrl = "http://$it:11434/v1", allowLocalHttp = true).problem())
        }
        listOf("192.168.1.10", "example.com", "local.evil.com", "localhost.evil.com", "127.0.0.2").forEach {
            assertEquals(it, AiProblem.LOCAL_HTTP, local.copy(baseUrl = "http://$it/v1", allowLocalHttp = true).problem())
        }
        assertNull(local.copy(baseUrl = "https://local.example/v1").problem())
    }

    @Test fun rejectCredentialUrlsQueriesFragmentsAndHeaderInjection() {
        listOf("ftp://example.com", "https://key@example.com/v1", "https://example.com?key=secret",
            "https://example.com#key", "https://example.com:0/v1", "https://example.com/a/../v1",
            "https://example.com/%2e%2e/v1", "not a URL").forEach {
            assertEquals(it, AiProblem.URL, config().copy(baseUrl = it).problem())
        }
        assertEquals(AiProblem.KEY, config().copy(apiKey = "key\r\nX: injected").problem())
    }

    @Test fun modelIdsAllowProviderNamespacesButNotUrlInjection() {
        listOf("org/model:8b", "models/gemini-model", "model@revision").forEach {
            assertNull(config().copy(model = it).problem())
        }
        listOf("", "../model", "model?key=secret", "model name").forEach {
            assertEquals(AiProblem.MODEL, config().copy(model = it).problem())
        }
    }

    @Test fun configurationsDoNotExposeKeysInToString() {
        assertFalse(config().toString().contains("test-key"))
        assertFalse(AiPreferences(configurations = mapOf(AiProvider.OPENAI to config())).toString().contains("test-key"))
    }
}
