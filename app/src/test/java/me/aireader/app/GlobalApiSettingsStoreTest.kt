package me.aireader.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GlobalApiSettingsStoreTest {
    @Test fun globalValuesOverrideLegacyProfileValuesButKeepTemperature() {
        val profile = ApiConfig(baseUrl = "https://example.com/v1", modelId = "model-a",
            temperature = 0.8, sendTemperature = false, maxOutputTokens = 123,
            streaming = false, allowHttp = true,
            reasoning = ReasoningSettings(false, ReasoningEffort.MAX),
            network = NetworkStabilitySettings(false, 5, 60, 600, true))
        val request = GlobalApiSettings().applyTo(profile)
        assertEquals(profile.baseUrl, request.baseUrl)
        assertEquals(profile.modelId, request.modelId)
        assertEquals(0.8, request.temperature, 0.0)
        assertFalse(request.sendTemperature)
        assertFalse(request.allowHttp)
        assertEquals(8192, request.maxOutputTokens)
        assertTrue(request.sendMaxTokens)
        assertTrue(request.streaming)
        assertEquals(ReasoningSettings(true, ReasoningEffort.MEDIUM), request.reasoning)
        assertEquals(NetworkStabilitySettings(), request.network)
    }

    @Test fun initializesOnceWithSpecifiedDefaultsAndKeepsChanges() = runBlocking {
        val file = File.createTempFile("aireader-global-", ".preferences_pb").apply { delete() }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val store = GlobalApiSettingsStore(dataStore)
            dataStore.edit { it[stringPreferencesKey("api_profiles_v1")] = "unchanged" }
            store.initializeIfNeeded()
            val defaults = store.settings.first()
            assertEquals(GlobalApiSettings(), defaults)
            assertFalse(defaults.allowHttp)
            assertTrue(defaults.sendMaxTokens)
            assertEquals(8192, defaults.maxOutputTokens)
            assertTrue(defaults.streaming)
            assertEquals(ReasoningSettings(true, ReasoningEffort.MEDIUM), defaults.reasoning)
            assertEquals(NetworkStabilitySettings(), defaults.network)
            val edited = defaults.copy(sendMaxTokens = false, maxOutputTokens = 4096,
                reasoning = ReasoningSettings(false, ReasoningEffort.HIGH),
                network = defaults.network.copy(maxRetries = 3))
            store.save(edited)
            store.initializeIfNeeded()
            assertEquals(edited, GlobalApiSettingsStore(dataStore).settings.first())
            assertEquals("unchanged", dataStore.data.first()[stringPreferencesKey("api_profiles_v1")])
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test fun corruptedSnapshotIsNotOverwritten() = runBlocking {
        val file = File.createTempFile("aireader-global-bad-", ".preferences_pb").apply { delete() }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val key = stringPreferencesKey("global_api_settings_v1")
            dataStore.edit { it[key] = "{broken" }
            val error = runCatching { GlobalApiSettingsStore(dataStore).initializeIfNeeded() }.exceptionOrNull()
            assertTrue(error != null)
            assertEquals("{broken", dataStore.data.first()[key])
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
