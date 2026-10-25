package com.isaac.ridelab.ai

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Uses an isolated cache directory, never the user's settings file or real API credentials. */
class EncryptedAiSettingsStoreTest {
    @Test fun encryptedRoundTripRetainsIndependentProfilesAndDetectsTampering() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.cacheDir, "ai-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getNoBackupFilesDir() = directory }
        try {
            val preferences = AiPreferences(AiProvider.DEEPSEEK, mapOf(
                AiProvider.OPENAI to AiConfiguration(model = "model-a", apiKey = "secret-key-a"),
                AiProvider.DEEPSEEK to AiConfiguration(AiProvider.DEEPSEEK, model = "model-b", apiKey = "secret-key-b")))
            EncryptedAiSettingsStore(context).save(preferences)
            assertEquals(preferences, EncryptedAiSettingsStore(context).read())
            val file = File(directory, "ai-providers.bin")
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("secret-key"))
            val bytes = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            file.writeBytes(bytes)
            var rejected = false
            try { EncryptedAiSettingsStore(context).read() } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
            EncryptedAiSettingsStore(context).save(AiPreferences())
            assertEquals(AiPreferences(), EncryptedAiSettingsStore(context).read())
        } finally { directory.deleteRecursively() }
    }
}
