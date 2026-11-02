package com.isaac.ridelab.ai

import org.junit.Assert.*
import org.junit.Test

class AiEndpointPolicyTest {
    @Test fun rfc1918RangesExcludePublicAndAmbiguousAddresses() {
        listOf("10.0.0.1", "10.255.255.254", "172.16.0.1", "172.31.255.254", "192.168.0.4", "192.168.99.5").forEach {
            assertTrue(it, AiEndpointPolicy.privateIpv4(it))
            assertNull(AiEndpointPolicy.problem("http://$it:8001/v1", true))
            assertEquals(AiProblem.LOCAL_HTTP, AiEndpointPolicy.problem("http://$it:8001/v1", false))
        }
        listOf("172.15.255.255", "172.32.0.1", "192.169.0.1", "8.8.8.8", "169.254.1.1", "100.64.0.1",
            "192.168.00.4", "3232235524", "0xC0A80004", "192.168.256.4", "192.168.0.4.evil.com", "::1").forEach {
            assertFalse(it, AiEndpointPolicy.privateIpv4(it))
        }
    }
    @Test fun modelListLinkKeepsHostPortAndVersionPrefix() {
        listOf("http://192.168.0.4:8001/v1/models", " http://192.168.0.4:8001/v1/models/ ")
            .forEach { assertEquals("http://192.168.0.4:8001/v1", AiEndpointPolicy.baseUrl(it)) }
        assertEquals("https://example.com/api/v1", AiEndpointPolicy.baseUrl("https://example.com/api/v1/models"))
    }

    @Test fun unrelatedPathsAndUnsafeUrlsAreNeverSilentlyRewritten() {
        listOf("https://example.com/v1", "https://example.com/models", "https://example.com/v1/models/model",
            "https://example.com/v1/models?utm_source=chatgpt.com", "https://example.com/v1/models#fragment",
            "https://secret@example.com/v1/models", "not a URL").forEach {
            assertEquals(it, AiEndpointPolicy.baseUrl(it))
        }
    }

    @Test fun finalWirePathsAllowEncodedModelNamesButBasePathsStillRejectEncoding() {
        val url = "https://example.com/v1/models/org%2Fmodel:generateContent"
        assertNull(AiEndpointPolicy.problem(url, false, basePath = false))
        assertEquals(AiProblem.URL, AiEndpointPolicy.problem(url, false))
        assertEquals(AiProblem.URL, AiEndpointPolicy.problem("https://example.com/v1?key=secret", false, basePath = false))
    }
}
