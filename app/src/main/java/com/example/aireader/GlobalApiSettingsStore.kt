package com.example.aireader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/** One atomic DataStore snapshot, independent of the selected API profile. */
data class GlobalApiSettings(
    val allowHttp: Boolean = false,
    val sendMaxTokens: Boolean = true,
    val maxOutputTokens: Int = 8192,
    val streaming: Boolean = true,
    val reasoning: ReasoningSettings = ReasoningSettings(true, ReasoningEffort.MEDIUM),
    val network: NetworkStabilitySettings = NetworkStabilitySettings()
) {
    fun validate() {
        require(maxOutputTokens > 0) { "Max Output Tokens 必须大于 0。" }
        network.validate()
    }

    fun applyTo(profile: ApiConfig): ApiConfig = profile.copy(
        allowHttp = allowHttp, sendMaxTokens = sendMaxTokens,
        maxOutputTokens = maxOutputTokens, streaming = streaming,
        reasoning = reasoning, network = network)
}

class GlobalApiSettingsStore(private val dataStore: DataStore<Preferences>) {
    private val key = stringPreferencesKey("global_api_settings_v1")

    val settings: Flow<GlobalApiSettings> = dataStore.data.map { preferences ->
        decode(requireNotNull(preferences[key]) { "全局 API 参数尚未初始化。" })
    }

    suspend fun initializeIfNeeded() {
        dataStore.edit { preferences ->
            val existing = preferences[key]
            if (existing == null) preferences[key] = encode(GlobalApiSettings())
            else decode(existing) // Damaged settings must never be silently overwritten.
        }
    }

    suspend fun save(settings: GlobalApiSettings) {
        settings.validate()
        dataStore.edit { preferences ->
            decode(requireNotNull(preferences[key]) { "全局 API 参数尚未初始化。" })
            preferences[key] = encode(settings)
        }
    }

    private fun encode(value: GlobalApiSettings): String = JSONObject()
        .put("version", 1)
        .put("allowHttp", value.allowHttp)
        .put("sendMaxTokens", value.sendMaxTokens)
        .put("maxOutputTokens", value.maxOutputTokens)
        .put("streaming", value.streaming)
        .put("sendReasoningEffort", value.reasoning.sendReasoningEffort)
        .put("reasoningEffort", value.reasoning.reasoningEffort.apiValue)
        .put("autoRetry", value.network.autoRetry)
        .put("retryHttp504", value.network.retryHttp504)
        .put("maxRetries", value.network.maxRetries)
        .put("connectTimeoutSeconds", value.network.connectTimeoutSeconds)
        .put("readTimeoutSeconds", value.network.readTimeoutSeconds).toString()

    private fun decode(raw: String): GlobalApiSettings {
        val json = JSONObject(raw)
        require(json.getInt("version") == 1) { "未知全局 API 参数版本。" }
        val required = listOf("allowHttp", "sendMaxTokens", "maxOutputTokens", "streaming",
            "sendReasoningEffort", "reasoningEffort", "autoRetry", "retryHttp504",
            "maxRetries", "connectTimeoutSeconds", "readTimeoutSeconds")
        require(required.all(json::has)) { "全局 API 参数字段不完整。" }
        return GlobalApiSettings(
            allowHttp = json.getBoolean("allowHttp"),
            sendMaxTokens = json.getBoolean("sendMaxTokens"),
            maxOutputTokens = json.getInt("maxOutputTokens"),
            streaming = json.getBoolean("streaming"),
            reasoning = ReasoningSettings(json.getBoolean("sendReasoningEffort"),
                requireNotNull(ReasoningEffort.entries.firstOrNull {
                    it.apiValue == json.getString("reasoningEffort")
                }) { "无效的思考深度参数。" }),
            network = NetworkStabilitySettings(json.getBoolean("autoRetry"),
                json.getInt("maxRetries"), json.getInt("connectTimeoutSeconds"),
                json.getInt("readTimeoutSeconds"), json.getBoolean("retryHttp504"))
        ).also { it.validate() }
    }
}
