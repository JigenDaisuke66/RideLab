package com.isaac.ridelab.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** All profiles (including keys) are encrypted and excluded from Android/cloud/device backups. */
class EncryptedAiSettingsStore(context: Context) : AiSettingsStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "ai-providers.bin"))

    override suspend fun read(): AiPreferences = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists()) return@withContext AiPreferences()
        check(file.baseFile.length() <= 262_144)
        val bytes = file.readFully()
        check(bytes.size >= 29 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(AAD)
        val json = JSONObject(cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8))
        check(json.getInt("version") == 1)
        val profiles = json.getJSONArray("profiles")
        val configurations = buildMap {
            for (i in 0 until profiles.length()) {
                val profile = profiles.getJSONObject(i)
                val provider = AiProvider.valueOf(profile.getString("provider"))
                put(provider, AiConfiguration(provider, profile.getString("url"), profile.getString("model"),
                    profile.getString("key"), profile.getBoolean("localHttp")))
            }
        }
        AiPreferences(AiProvider.valueOf(json.getString("selected")), configurations)
    }

    override suspend fun save(preferences: AiPreferences) = withContext(Dispatchers.IO) {
        val profiles = JSONArray()
        preferences.configurations.forEach { (provider, config) ->
            check(provider == config.provider && config.problem() == null)
            profiles.put(JSONObject().put("provider", provider.name).put("url", config.baseUrl.trim())
                .put("model", config.model).put("key", config.apiKey).put("localHttp", config.allowLocalHttp))
        }
        val plain = JSONObject().put("version", 1).put("selected", preferences.selected.name)
            .put("profiles", profiles).toString().toByteArray(Charsets.UTF_8)
        check(plain.size <= 240_000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(AAD)
        val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
        val stream = file.startWrite()
        try { stream.write(encrypted); file.finishWrite(stream)
        } catch (error: Exception) { file.failWrite(stream); throw error }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    private companion object {
        const val ALIAS = "ridelab_ai_settings_v1"
        val AAD = "RideLab.ai.settings.v1".toByteArray(Charsets.UTF_8)
    }
}
