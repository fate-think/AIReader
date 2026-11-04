package com.example.aireader

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ApiProfilesRepositoryTest {
    private val cipher = object : ApiKeyCipher {
        override fun encrypt(value: String) = "cipher:" + value.reversed()
        override fun decrypt(value: String): String {
            require(value.startsWith("cipher:"))
            return value.removePrefix("cipher:").reversed()
        }
    }

    private suspend fun withRepository(block: suspend (ApiProfilesRepository,
        androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> Unit) {
        val file = File.createTempFile("aireader-profiles-", ".preferences_pb")
        file.delete()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            block(ApiProfilesRepository(store, cipher), store)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    private fun richConfig(model: String) = ApiConfig(
        baseUrl = "https://example.com/v1", modelId = model, temperature = 0.8,
        maxOutputTokens = 8192, streaming = false, sendTemperature = false,
        useMaxCompletionTokens = true, allowHttp = false,
        network = NetworkStabilitySettings(false, 4, 45, 300, true),
        reasoning = ReasoningSettings(true, ReasoningEffort.MAX))

    @Test fun oldSettingsAndCiphertextMigrateExactlyOnce() = runBlocking { withRepository { repo, store ->
        val legacyConfig = richConfig("legacy-model")
        val legacyJson = JSONObject()
            .put("baseUrl", legacyConfig.baseUrl).put("modelId", legacyConfig.modelId)
            .put("temperature", legacyConfig.temperature).put("maxOutputTokens", legacyConfig.maxOutputTokens)
            .put("streaming", legacyConfig.streaming).put("sendTemperature", legacyConfig.sendTemperature)
            .put("useMaxCompletionTokens", legacyConfig.useMaxCompletionTokens)
            .put("allowHttp", legacyConfig.allowHttp).put("autoRetry", legacyConfig.network.autoRetry)
            .put("maxRetries", legacyConfig.network.maxRetries)
            .put("connectTimeoutSeconds", legacyConfig.network.connectTimeoutSeconds)
            .put("readTimeoutSeconds", legacyConfig.network.readTimeoutSeconds)
            .put("retryHttp504", legacyConfig.network.retryHttp504)
            .put("sendReasoningEffort", legacyConfig.reasoning.sendReasoningEffort)
            .put("reasoningEffort", legacyConfig.reasoning.reasoningEffort.apiValue).toString()
        val oldCiphertext = cipher.encrypt("legacy-key")
        store.edit {
            it[stringPreferencesKey("settings")] = legacyJson
            it[stringPreferencesKey("api_key_encrypted")] = oldCiphertext
        }
        repo.migrateIfNeeded()
        val first = repo.catalogs.first()
        assertEquals(1, first.profiles.size)
        assertEquals(first.profiles.single().id, first.selectedId)
        assertEquals(legacyConfig, first.selected.config)
        assertEquals("legacy-key", repo.loadKey(first.selectedId).value)
        val saved = store.data.first()
        assertEquals(legacyJson, saved[stringPreferencesKey("settings")])
        assertEquals(oldCiphertext, saved[stringPreferencesKey("api_key_encrypted")])
        val snapshot = saved[stringPreferencesKey("api_profiles_v1")]!!
        assertEquals(oldCiphertext, JSONObject(snapshot).getJSONArray("profiles")
            .getJSONObject(0).getString("encryptedKey"))
        assertFalse(snapshot.contains("legacy-key"))
        repo.migrateIfNeeded()
        assertEquals(snapshot, store.data.first()[stringPreferencesKey("api_profiles_v1")])
    } }

    @Test fun damagedSnapshotIsNeverReplacedOrWritten() = runBlocking { withRepository { repo, store ->
        store.edit { it[stringPreferencesKey("api_profiles_v1")] = "{broken" }
        try { repo.migrateIfNeeded(); fail("Expected damaged snapshot to fail") }
        catch (_: org.json.JSONException) { }
        assertEquals("{broken", store.data.first()[stringPreferencesKey("api_profiles_v1")])
    } }

    @Test fun failedLegacyMigrationLeavesOriginalFieldsUntouched() = runBlocking { withRepository { repo, store ->
        store.edit {
            it[stringPreferencesKey("settings")] = "{broken"
            it[stringPreferencesKey("api_key_encrypted")] = "encrypted-old-key"
        }
        try { repo.migrateIfNeeded(); fail("Expected invalid legacy JSON to fail") }
        catch (_: org.json.JSONException) { }
        val preferences = store.data.first()
        assertEquals("{broken", preferences[stringPreferencesKey("settings")])
        assertEquals("encrypted-old-key", preferences[stringPreferencesKey("api_key_encrypted")])
        assertNull(preferences[stringPreferencesKey("api_profiles_v1")])
    } }

    @Test fun addEditSelectCopyDeleteKeepProfilesAndKeysIndependent() = runBlocking { withRepository { repo, store ->
        repo.migrateIfNeeded()
        val original = repo.catalogs.first().selectedId
        val a = repo.add("A", richConfig("model-A"), " key-A\r\n")
        val bConfig = richConfig("model-B").copy(reasoning = ReasoningSettings(false, ReasoningEffort.HIGH))
        val b = repo.add("B", bConfig, "key-B")
        val ids = repo.catalogs.first().profiles.map { it.id }
        assertEquals(3, ids.toSet().size)
        assertEquals("key-A", repo.loadKey(a).value)
        assertEquals("key-B", repo.loadKey(b).value)
        assertEquals(bConfig, repo.catalogs.first().selected.config)
        assertEquals(ReasoningEffort.MAX, repo.catalogs.first().profiles.first { it.id == a }
            .config.reasoning.reasoningEffort)
        repo.save(a, "Renamed A", richConfig("edited-A"), null, false)
        assertEquals("key-A", repo.loadKey(a).value)
        assertEquals("key-B", repo.loadKey(b).value)
        assertEquals("model-B", repo.catalogs.first().profiles.first { it.id == b }.config.modelId)
        repo.select(a)
        assertEquals(a, ApiProfilesRepository(store, cipher).catalogs.first().selectedId)
        val copy = repo.copy(a, "Copy A")
        assertNotEquals(a, copy)
        assertEquals("edited-A", repo.catalogs.first().selected.config.modelId)
        assertFalse(repo.catalogs.first().selected.hasApiKey)
        assertNull(repo.loadKey(copy).value)
        repo.save(a, "Renamed A", richConfig("edited-A"), null, true)
        assertNull(repo.loadKey(a).value)
        assertEquals("key-B", repo.loadKey(b).value)
        repo.delete(b) // Nonselected deletion keeps the selected copy.
        assertEquals(copy, repo.catalogs.first().selectedId)
        repo.delete(copy) // Selected deletion falls back to first saved profile.
        assertEquals(original, repo.catalogs.first().selectedId)
        repo.delete(a)
        try { repo.delete(original); fail("Must retain one profile") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, repo.catalogs.first().profiles.size)
    } }

    @Test fun undecryptableKeyRequiresReentryAndPreservesCiphertext() = runBlocking { withRepository { repo, store ->
        repo.migrateIfNeeded()
        val id = repo.add("A", ApiConfig(), "key-A")
        val before = store.data.first()[stringPreferencesKey("api_profiles_v1")]
        val brokenCipher = object : ApiKeyCipher {
            override fun encrypt(value: String) = value
            override fun decrypt(value: String): String = error("Keystore unavailable")
        }
        val loaded = ApiProfilesRepository(store, brokenCipher).loadKey(id)
        assertNull(loaded.value)
        assertTrue(loaded.needsReentry)
        assertEquals(before, store.data.first()[stringPreferencesKey("api_profiles_v1")])
    } }

    @Test fun concurrentAddsUseLatestAtomicSnapshotAndLeaveOtherSettingsUntouched() = runBlocking {
        withRepository { repo, store ->
            store.edit {
                it[intPreferencesKey("preprocess_para_once")] = 7
                it[stringPreferencesKey("prompt_catalog_v2")] = "mode-stays"
            }
            repo.migrateIfNeeded()
            val jobs = (1..20).map { index -> launch(Dispatchers.Default) { repo.add("Profile $index") } }
            jobs.forEach { it.join() }
            repo.select(repo.catalogs.first().profiles[1].id)
            val catalog = repo.catalogs.first()
            assertEquals(21, catalog.profiles.size)
            assertEquals(21, catalog.profiles.map { it.id }.toSet().size)
            assertEquals(7, PreprocessSettingsStore(store).options.first().paraOnce)
            assertEquals("mode-stays", store.data.first()[stringPreferencesKey("prompt_catalog_v2")])
        }
    }

    @Test fun selectedProfileDrivesRequestAndConnectionWithoutChangingExistingRequest() = runBlocking {
        withRepository { repo, _ ->
            repo.migrateIfNeeded()
            val server = MockWebServer()
            server.start()
            try {
                val base = server.url("/v1").toString()
                val a = repo.add("A", richConfig("model-A").copy(baseUrl = base, allowHttp = true), "key-A")
                val b = repo.add("B", richConfig("model-B").copy(baseUrl = base, allowHttp = true,
                    reasoning = ReasoningSettings(false, ReasoningEffort.HIGH)), "key-B")
                val client = OpenAiCompatibleClient(allowHttpForTests = true)
                repo.select(a)
                val aProfile = repo.catalogs.first().selected
                val inFlightRequest = client.buildRequest(aProfile.config, repo.loadKey(a).value!!, "prompt\n\narticle")
                repo.select(b)
                val bProfile = repo.catalogs.first().selected
                val bRequest = client.buildRequest(bProfile.config, repo.loadKey(b).value!!, "other")
                val aBody = Buffer().also { inFlightRequest.body!!.writeTo(it) }
                val bBody = Buffer().also { bRequest.body!!.writeTo(it) }
                assertEquals("Bearer key-A", inFlightRequest.header("Authorization"))
                val aJson = JSONObject(aBody.readUtf8())
                assertEquals("model-A", aJson.getString("model"))
                assertEquals("max", aJson.getString("reasoning_effort"))
                assertEquals("Bearer key-B", bRequest.header("Authorization"))
                val bJson = JSONObject(bBody.readUtf8())
                assertEquals("model-B", bJson.getString("model"))
                assertFalse(bJson.has("reasoning_effort"))
                server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"OK"}}]}"""))
                client.testConnection(bProfile.config, repo.loadKey(b).value!!)
                val recorded = server.takeRequest()
                assertEquals("Bearer key-B", recorded.getHeader("Authorization"))
                assertEquals("model-B", JSONObject(recorded.body.readUtf8()).getString("model"))
            } finally { server.shutdown() }
        }
    }
}
