package com.example.aireader

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ApiKeyCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
}

class AndroidKeystoreApiKeyCipher : ApiKeyCipher {
    private val alias = "aireader_api_key"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    override fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12) { "Invalid encrypted API Key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}

data class LoadedApiSettings(
    val config: ApiConfig,
    val apiKey: String?,
    val requiresKeyReentry: Boolean
)

class ApiSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val cipher: ApiKeyCipher
) {
    private val settingsKey = stringPreferencesKey("settings")
    private val secretKey = stringPreferencesKey("api_key_encrypted")

    suspend fun load(): LoadedApiSettings {
        val preferences = dataStore.data.first()
        val saved = JSONObject(preferences[settingsKey] ?: "{}")
        val config = ApiConfig(
            baseUrl = saved.optString("baseUrl"),
            modelId = saved.optString("modelId"),
            temperature = saved.optString("temperature", "0.3").toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.3,
            maxOutputTokens = saved.optString("maxOutputTokens", "4096").toIntOrNull() ?: 4096,
            streaming = saved.optBoolean("streaming", true),
            sendTemperature = saved.optBoolean("sendTemperature", true),
            useMaxCompletionTokens = saved.optBoolean("useMaxCompletionTokens", false),
            allowHttp = saved.optBoolean("allowHttp", false),
            network = NetworkStabilitySettings(
                autoRetry = saved.optBoolean("autoRetry", true),
                maxRetries = saved.optInt("maxRetries", 2).coerceIn(0, 5),
                connectTimeoutSeconds = saved.optInt("connectTimeoutSeconds", 20).coerceIn(10, 60),
                readTimeoutSeconds = saved.optInt("readTimeoutSeconds", 180).coerceIn(30, 600),
                retryHttp504 = saved.optBoolean("retryHttp504", false)
            ),
            reasoning = ReasoningSettings(
                sendReasoningEffort = saved.optBoolean("sendReasoningEffort", false),
                reasoningEffort = ReasoningEffort.fromApiValue(saved.optString("reasoningEffort", "max"))
            )
        )
        val encrypted = preferences[secretKey].orEmpty()
        if (encrypted.isEmpty()) return LoadedApiSettings(config, null, false)
        return try { LoadedApiSettings(config, ApiKeyNormalizer.normalize(cipher.decrypt(encrypted)), false) }
        catch (_: Exception) { LoadedApiSettings(config, null, true) }
    }

    suspend fun save(config: ApiConfig, newApiKey: String?, deleteApiKey: Boolean): LoadedApiSettings {
        config.network.validate()
        val encryptedNewKey = newApiKey?.takeIf { it.isNotBlank() }
            ?.let { cipher.encrypt(ApiKeyNormalizer.normalize(it)) }
        val json = JSONObject()
            .put("baseUrl", config.baseUrl).put("modelId", config.modelId)
            .put("temperature", config.temperature).put("maxOutputTokens", config.maxOutputTokens)
            .put("streaming", config.streaming).put("sendTemperature", config.sendTemperature)
            .put("useMaxCompletionTokens", config.useMaxCompletionTokens)
            .put("allowHttp", config.allowHttp)
            .put("autoRetry", config.network.autoRetry)
            .put("maxRetries", config.network.maxRetries)
            .put("connectTimeoutSeconds", config.network.connectTimeoutSeconds)
            .put("readTimeoutSeconds", config.network.readTimeoutSeconds)
            .put("retryHttp504", config.network.retryHttp504)
            .put("sendReasoningEffort", config.reasoning.sendReasoningEffort)
            .put("reasoningEffort", config.reasoning.reasoningEffort.apiValue)
        dataStore.edit { preferences ->
            preferences[settingsKey] = json.toString()
            when {
                encryptedNewKey != null -> preferences[secretKey] = encryptedNewKey
                deleteApiKey -> preferences.remove(secretKey)
            }
        }
        return load()
    }
}
