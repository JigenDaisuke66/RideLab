package com.isaac.ridelab.ai

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Live tests are opt-in via aiEndpoint/aiModel runner arguments; never read ride data or saved keys. */
class CompatibleApiInstrumentedTest {
    @Test fun explicitLiveModelListAndChatUseAndroidNetworkStack(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val endpoint = args.getString("aiEndpoint")
        val model = args.getString("aiModel")
        assumeTrue("Live endpoint/model not supplied", !endpoint.isNullOrBlank() && !model.isNullOrBlank())
        val config = AiConfiguration(AiProvider.CUSTOM, endpoint!!, model!!, allowLocalHttp = true)
        val stages = mutableListOf<String>()
        val result = HttpAiConnectionProbe { stage, status, problem ->
            stages += stage
            Log.i("RideLabAi", "Android live test stage=$stage httpStatus=$status problem=$problem")
        }.test(config, "Reply with OK only.")
        assertNull("${result.problem}: ${result.diagnostic}", result.problem)
        assertTrue(model in result.models)
        assertFalse(result.reply.isNullOrBlank())
        assertEquals(listOf("GET models", "POST chat/completions"), stages)
        Log.i("RideLabAi", "Android live test completed model=$model reply=${result.reply}")
    }

    @Test fun disablingConsentAndPublicHttpRemainBlocked() = runBlocking {
        assertEquals(AiProblem.LOCAL_HTTP, HttpAiConnectionProbe().test(AiConfiguration(AiProvider.CUSTOM,
            "http://10.1.2.3:8001/v1", "model"), "must not send").problem)
        assertEquals(AiProblem.LOCAL_HTTP, HttpAiConnectionProbe().test(AiConfiguration(AiProvider.CUSTOM,
            "http://8.8.8.8/v1", "model", allowLocalHttp = true), "must not send").problem)
    }
}
