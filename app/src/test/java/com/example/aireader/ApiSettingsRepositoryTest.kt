package com.example.aireader

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File

class ApiSettingsRepositoryTest {
    private val cipher = object : ApiKeyCipher {
        override fun encrypt(value: String) = "cipher:" + value.reversed()
        override fun decrypt(value: String): String {
            require(value.startsWith("cipher:"))
            return value.removePrefix("cipher:").reversed()
        }
    }

    @Test fun defaultsMatchRequestedValuesAndKeyIsNotPlaintext() = runBlocking {
        val file = File.createTempFile("aireader-api-", ".preferences_pb")
        file.delete()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val repo = ApiSettingsRepository(store, cipher)
            val defaults = repo.load()
            assertEquals(0.3, defaults.config.temperature, 0.0)
            assertEquals(4096, defaults.config.maxOutputTokens)
            assertTrue(defaults.config.streaming)
            assertTrue(defaults.config.sendTemperature)
            assertFalse(defaults.config.allowHttp)

            repo.save(ApiConfig(baseUrl = "https://example.com/v1", modelId = "model"),
                "  secret-123\r\n", false)
            val preferences = store.data.first()
            assertFalse(preferences[stringPreferencesKey("settings")].orEmpty().contains("secret-123"))
            assertFalse(preferences[stringPreferencesKey("api_key_encrypted")].orEmpty().contains("secret-123"))
            assertEquals("secret-123", cipher.decrypt(preferences[stringPreferencesKey("api_key_encrypted")]!!))
            assertEquals("secret-123", repo.load().apiKey)

            repo.save(ApiConfig(baseUrl = "https://example.com/custom", modelId = "model-2"), null, false)
            assertEquals("secret-123", repo.load().apiKey)
            repo.save(ApiConfig(baseUrl = "http://example.com/v1", modelId = "model-2", allowHttp = true), null, false)
            assertTrue(ApiSettingsRepository(store, cipher).load().config.allowHttp)
            repo.save(ApiConfig(), null, true)
            assertNull(repo.load().apiKey)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test fun undecryptableKeyRequiresReentryWithoutDeletingCiphertext() = runBlocking {
        val file = File.createTempFile("aireader-api-invalid-", ".preferences_pb")
        file.delete()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val repo = ApiSettingsRepository(store, cipher)
            repo.save(ApiConfig(), "secret", false)
            val wrongCipher = object : ApiKeyCipher {
                override fun encrypt(value: String) = value
                override fun decrypt(value: String): String = throw IllegalStateException("key lost")
            }
            val reopened = ApiSettingsRepository(store, wrongCipher).load()
            assertTrue(reopened.requiresKeyReentry)
            assertNull(reopened.apiKey)
            assertTrue(store.data.first()[stringPreferencesKey("api_key_encrypted")].orEmpty().isNotEmpty())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test fun previousSettingsKeepTheirValuesAndGainNewDefaults() = runBlocking {
        val file = File.createTempFile("aireader-api-legacy-", ".preferences_pb")
        file.delete()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            store.edit { preferences ->
                preferences[stringPreferencesKey("settings")] = JSONObject()
                    .put("baseUrl", "https://example.com/v1")
                    .put("modelId", "old-model")
                    .put("temperature", "0.7")
                    .put("maxOutputTokens", "2048")
                    .put("streaming", false).toString()
                preferences[stringPreferencesKey("api_key_encrypted")] = cipher.encrypt("legacy-secret\n")
            }
            val loaded = ApiSettingsRepository(store, cipher).load()
            assertEquals("legacy-secret", loaded.apiKey)
            assertEquals("old-model", loaded.config.modelId)
            assertEquals(0.7, loaded.config.temperature, 0.0)
            assertEquals(2048, loaded.config.maxOutputTokens)
            assertFalse(loaded.config.streaming)
            assertTrue(loaded.config.sendTemperature)
            assertFalse(loaded.config.useMaxCompletionTokens)
            assertFalse(loaded.config.allowHttp)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
