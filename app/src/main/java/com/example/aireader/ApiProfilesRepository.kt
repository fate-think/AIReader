package com.example.aireader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ApiProfile(val id: String, val name: String, val config: ApiConfig, val hasApiKey: Boolean)
data class ApiProfileCatalog(val profiles: List<ApiProfile>, val selectedId: String) {
    val selected: ApiProfile get() = profiles.first { it.id == selectedId }
}
data class LoadedProfileKey(val value: String?, val needsReentry: Boolean)

private data class StoredProfile(val id: String, val name: String, val config: ApiConfig,
                                 val encryptedKey: String?) {
    fun toPublicProfile() = ApiProfile(id, name, config, !encryptedKey.isNullOrEmpty())
}
private data class StoredCatalog(val profiles: List<StoredProfile>, val selectedId: String) {
    fun toPublicCatalog() = ApiProfileCatalog(profiles.map { it.toPublicProfile() }, selectedId)
}

/** All profile mutations decode the latest snapshot inside DataStore.edit. */
class ApiProfilesRepository(private val dataStore: DataStore<Preferences>, private val cipher: ApiKeyCipher) {
    private val profilesKey = stringPreferencesKey("api_profiles_v1")
    private val legacySettingsKey = stringPreferencesKey("settings")
    private val legacySecretKey = stringPreferencesKey("api_key_encrypted")

    val catalogs: Flow<ApiProfileCatalog> = dataStore.data.map { preferences ->
        requireNotNull(preferences[profilesKey]) { "API 配置尚未迁移。" }
    }.distinctUntilChanged().map { decode(it).toPublicCatalog() }

    suspend fun migrateIfNeeded() {
        dataStore.edit { preferences ->
            if (preferences[profilesKey] != null) {
                decode(preferences[profilesKey]!!) // Never replace a damaged snapshot.
                return@edit
            }
            val legacy = preferences[legacySettingsKey]?.let { JSONObject(it) } ?: JSONObject()
            val profile = StoredProfile(UUID.randomUUID().toString(), "默认 API 配置",
                decodeLegacyConfig(legacy), preferences[legacySecretKey])
            preferences[profilesKey] = encode(StoredCatalog(listOf(profile), profile.id))
            // Keep both legacy fields until migration has been validated on devices.
        }
    }

    suspend fun loadSelectedKey(): LoadedProfileKey = loadKey(catalogs.first().selectedId)

    suspend fun loadKey(id: String): LoadedProfileKey {
        val snapshot = current()
        val encrypted = snapshot.profiles.firstOrNull { it.id == id }?.encryptedKey
            ?: return LoadedProfileKey(null, false)
        return try { LoadedProfileKey(ApiKeyNormalizer.normalize(cipher.decrypt(encrypted)), false) }
        catch (_: Exception) { LoadedProfileKey(null, true) }
    }

    suspend fun select(id: String) = mutate { snapshot ->
        require(snapshot.profiles.any { it.id == id }) { "API 配置不存在。" }
        snapshot.copy(selectedId = id)
    }

    suspend fun add(name: String, config: ApiConfig = ApiConfig(), newKey: String? = null): String {
        val cleanName = validName(name)
        config.network.validate()
        val encrypted = encryptIfEntered(newKey)
        val id = UUID.randomUUID().toString()
        mutate { snapshot -> snapshot.copy(
            profiles = snapshot.profiles + StoredProfile(id, cleanName, config, encrypted), selectedId = id) }
        return id
    }

    suspend fun save(id: String, name: String, config: ApiConfig, newKey: String?, deleteKey: Boolean) {
        val cleanName = validName(name)
        config.network.validate()
        val encrypted = encryptIfEntered(newKey)
        mutate { snapshot ->
            require(snapshot.profiles.any { it.id == id }) { "API 配置不存在。" }
            snapshot.copy(profiles = snapshot.profiles.map { profile ->
                if (profile.id != id) profile else profile.copy(name = cleanName, config = config,
                    encryptedKey = when {
                        encrypted != null -> encrypted
                        deleteKey -> null
                        else -> profile.encryptedKey
                    })
            })
        }
    }

    suspend fun copy(id: String, name: String): String {
        val cleanName = validName(name)
        val newId = UUID.randomUUID().toString()
        mutate { snapshot ->
            val source = snapshot.profiles.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("API 配置不存在。")
            snapshot.copy(profiles = snapshot.profiles + source.copy(id = newId, name = cleanName,
                encryptedKey = null), selectedId = newId)
        }
        return newId
    }

    suspend fun delete(id: String) = mutate { snapshot ->
        require(snapshot.profiles.size > 1) { "至少保留一套 API 配置。" }
        require(snapshot.profiles.any { it.id == id }) { "API 配置不存在。" }
        val remaining = snapshot.profiles.filterNot { it.id == id }
        snapshot.copy(profiles = remaining,
            selectedId = if (snapshot.selectedId == id) remaining.first().id else snapshot.selectedId)
    }

    private suspend fun current(): StoredCatalog =
        decode(requireNotNull(dataStore.data.first()[profilesKey]) { "API 配置尚未迁移。" })

    private suspend fun mutate(change: (StoredCatalog) -> StoredCatalog) {
        dataStore.edit { preferences ->
            val snapshot = decode(requireNotNull(preferences[profilesKey]) { "API 配置尚未迁移。" })
            preferences[profilesKey] = encode(change(snapshot))
        }
    }

    private fun validName(value: String): String = value.trim().also {
        require(it.isNotEmpty()) { "配置名称不能为空。" }
    }

    private fun encryptIfEntered(value: String?): String? = value?.takeIf { it.isNotBlank() }
        ?.let { cipher.encrypt(ApiKeyNormalizer.normalize(it)) }

    private fun decode(raw: String): StoredCatalog {
        val root = JSONObject(raw)
        require(root.getInt("version") == 1) { "未知 API 配置版本。" }
        val rows = root.getJSONArray("profiles")
        require(rows.length() > 0) { "API 配置列表为空。" }
        val profiles = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val id = row.getString("id")
            val name = validName(row.getString("name"))
            val config = decodeConfig(row.getJSONObject("config"), strict = true)
            val encrypted = row.optString("encryptedKey").takeIf { it.isNotEmpty() }
            StoredProfile(id, name, config, encrypted)
        }
        require(profiles.map { it.id }.toSet().size == profiles.size) { "API 配置 ID 重复。" }
        val selectedId = root.getString("selectedId")
        require(profiles.any { it.id == selectedId }) { "当前 API 配置不存在。" }
        return StoredCatalog(profiles, selectedId)
    }

    private fun encode(snapshot: StoredCatalog): String = JSONObject()
        .put("version", 1).put("selectedId", snapshot.selectedId)
        .put("profiles", JSONArray().apply { snapshot.profiles.forEach { profile ->
            put(JSONObject().put("id", profile.id).put("name", profile.name)
                .put("config", encodeConfig(profile.config))
                .put("encryptedKey", profile.encryptedKey ?: ""))
        } }).toString()

    private fun decodeLegacyConfig(json: JSONObject): ApiConfig = decodeConfig(json, strict = false)

    private fun decodeConfig(json: JSONObject, strict: Boolean): ApiConfig {
        if (strict) require(listOf("baseUrl", "modelId", "temperature", "maxOutputTokens",
                "streaming", "sendTemperature", "useMaxCompletionTokens", "allowHttp",
                "autoRetry", "maxRetries", "connectTimeoutSeconds", "readTimeoutSeconds",
                "retryHttp504", "sendReasoningEffort", "reasoningEffort").all { json.has(it) }) {
            "API 配置字段不完整。"
        }
        return ApiConfig(
            baseUrl = json.optString("baseUrl"), modelId = json.optString("modelId"),
            temperature = json.optString("temperature", "0.3").toDoubleOrNull()
                ?.takeIf { it.isFinite() } ?: 0.3,
            maxOutputTokens = json.optString("maxOutputTokens", "4096").toIntOrNull() ?: 4096,
            streaming = json.optBoolean("streaming", true),
            sendTemperature = json.optBoolean("sendTemperature", true),
            useMaxCompletionTokens = json.optBoolean("useMaxCompletionTokens", false),
            allowHttp = json.optBoolean("allowHttp", false),
            network = NetworkStabilitySettings(
                json.optBoolean("autoRetry", true), json.optInt("maxRetries", 2).coerceIn(0, 5),
                json.optInt("connectTimeoutSeconds", 20).coerceIn(10, 60),
                json.optInt("readTimeoutSeconds", 180).coerceIn(30, 600),
                json.optBoolean("retryHttp504", false)),
            reasoning = ReasoningSettings(json.optBoolean("sendReasoningEffort", false),
                ReasoningEffort.fromApiValue(json.optString("reasoningEffort", "max"))))
    }

    private fun encodeConfig(config: ApiConfig): JSONObject = JSONObject()
        .put("baseUrl", config.baseUrl).put("modelId", config.modelId)
        .put("temperature", config.temperature).put("maxOutputTokens", config.maxOutputTokens)
        .put("streaming", config.streaming).put("sendTemperature", config.sendTemperature)
        .put("useMaxCompletionTokens", config.useMaxCompletionTokens).put("allowHttp", config.allowHttp)
        .put("autoRetry", config.network.autoRetry).put("maxRetries", config.network.maxRetries)
        .put("connectTimeoutSeconds", config.network.connectTimeoutSeconds)
        .put("readTimeoutSeconds", config.network.readTimeoutSeconds)
        .put("retryHttp504", config.network.retryHttp504)
        .put("sendReasoningEffort", config.reasoning.sendReasoningEffort)
        .put("reasoningEffort", config.reasoning.reasoningEffort.apiValue)
}
