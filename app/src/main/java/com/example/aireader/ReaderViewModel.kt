package com.example.aireader

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore("reader_settings")
private val modesKey = stringPreferencesKey("modes")
private val settingsKey = stringPreferencesKey("settings")
private val secretKey = stringPreferencesKey("api_key_encrypted")

data class ReadingMode(
    val id: String,
    val name: String,
    val prompt: String,
    val builtIn: Boolean,
    val assetName: String? = null
)

data class ApiSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelId: String = "",
    val temperature: String = "0.7",
    val maxOutputTokens: String = "2048",
    val streaming: Boolean = true
)

data class ReaderState(
    val modes: List<ReadingMode> = emptyList(),
    val selectedModeId: String? = null,
    val article: String = "",
    val preprocessOptions: PreprocessOptions = PreprocessOptions(),
    val settings: ApiSettings = ApiSettings(),
    val preparedUserMessage: String? = null,
    val result: String = "尚未生成结果。当前尚未连接 AI 服务。",
    val loaded: Boolean = false
)

private val builtIns = listOf(
    "builtin.keypoints.v1" to "信息点提取V1.md",
    "builtin.keypoints.v2" to "信息点提取V2.md",
    "builtin.full_analysis.v1" to "全文分析总结V1.md",
    "builtin.full_analysis.v2" to "全文分析总结V2.md"
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val preprocessSettings = PreprocessSettingsStore(context.dataStore)
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        val prefs = context.dataStore.data.first()
        val saved = runCatching { JSONArray(prefs[modesKey] ?: "[]") }.getOrDefault(JSONArray())
        val savedModes = (0 until saved.length()).mapNotNull { index ->
            runCatching {
                val item = saved.getJSONObject(index)
                ReadingMode(item.getString("id"), item.getString("name"), item.getString("prompt"),
                    item.optBoolean("builtIn"), item.optString("assetName").ifBlank { null })
            }.getOrNull()
        }
        val defaults = builtIns.map { (id, file) ->
            savedModes.firstOrNull { it.id == id } ?: ReadingMode(
                id, file.removeSuffix(".md"), context.assets.open("prompts/$file").bufferedReader(Charsets.UTF_8).use { it.readText() },
                true, file
            )
        }
        val settingsJson = runCatching { JSONObject(prefs[settingsKey] ?: "{}") }.getOrDefault(JSONObject())
        val settings = ApiSettings(
            settingsJson.optString("baseUrl"),
            runCatching { decrypt(prefs[secretKey] ?: "") }.getOrDefault(""),
            settingsJson.optString("modelId"),
            settingsJson.optString("temperature", "0.7"),
            settingsJson.optString("maxOutputTokens", "2048"),
            settingsJson.optBoolean("streaming", true)
        )
        _state.value = _state.value.copy(
            modes = defaults + savedModes.filter { !it.builtIn && builtIns.none { pair -> pair.first == it.id } },
            selectedModeId = defaults.first().id,
            preprocessOptions = preprocessSettings.options.first(),
            settings = settings, loaded = true
        )
    }

    fun selectMode(id: String) { _state.value = _state.value.copy(selectedModeId = id) }
    fun setArticle(value: String) { _state.value = _state.value.copy(article = value, preparedUserMessage = null) }
    fun clearArticle() = setArticle("")
    fun processedArticle(): String = ArticlePreprocessor.process(_state.value.article, _state.value.preprocessOptions)

    fun savePreprocessOptions(options: PreprocessOptions) {
        _state.value = _state.value.copy(preprocessOptions = options, preparedUserMessage = null)
        viewModelScope.launch { preprocessSettings.save(options) }
    }

    fun startReading() {
        val mode = _state.value.modes.firstOrNull { it.id == _state.value.selectedModeId } ?: return
        val message = buildUserMessage(mode.prompt, processedArticle())
        _state.value = _state.value.copy(
            preparedUserMessage = message,
            result = "已准备好预处理文章和单条 User Message。当前尚未连接 AI 服务，未发送请求或生成 AI 结果。"
        )
    }

    fun saveMode(id: String?, name: String, prompt: String) {
        if (name.isBlank() || prompt.isBlank()) return
        val current = _state.value.modes
        val updated = if (id == null) current + ReadingMode(UUID.randomUUID().toString(), name.trim(), prompt, false)
            else current.map { if (it.id == id) it.copy(name = name.trim(), prompt = prompt) else it }
        _state.value = _state.value.copy(modes = updated)
        persistModes(updated)
    }

    fun deleteMode(id: String) {
        val mode = _state.value.modes.firstOrNull { it.id == id } ?: return
        if (mode.builtIn) return
        val updated = _state.value.modes.filterNot { it.id == id }
        _state.value = _state.value.copy(modes = updated,
            selectedModeId = if (_state.value.selectedModeId == id) updated.firstOrNull()?.id else _state.value.selectedModeId)
        persistModes(updated)
    }

    fun restoreDefault(id: String) {
        val mode = _state.value.modes.firstOrNull { it.id == id && it.builtIn } ?: return
        val file = mode.assetName ?: return
        val original = context.assets.open("prompts/$file").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val updated = _state.value.modes.map { if (it.id == id) it.copy(name = file.removeSuffix(".md"), prompt = original) else it }
        _state.value = _state.value.copy(modes = updated)
        persistModes(updated)
    }

    private fun persistModes(modes: List<ReadingMode>) = viewModelScope.launch {
        val array = JSONArray()
        modes.forEach { mode ->
            array.put(JSONObject().put("id", mode.id).put("name", mode.name).put("prompt", mode.prompt)
                .put("builtIn", mode.builtIn).put("assetName", mode.assetName ?: ""))
        }
        context.dataStore.edit { it[modesKey] = array.toString() }
    }

    fun saveSettings(value: ApiSettings) {
        _state.value = _state.value.copy(settings = value)
        viewModelScope.launch {
            val json = JSONObject().put("baseUrl", value.baseUrl).put("modelId", value.modelId)
                .put("temperature", value.temperature).put("maxOutputTokens", value.maxOutputTokens)
                .put("streaming", value.streaming)
            context.dataStore.edit {
                it[settingsKey] = json.toString()
                it[secretKey] = encrypt(value.apiKey)
            }
        }
    }

    private fun getKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("aireader_api_key", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("aireader_api_key",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
