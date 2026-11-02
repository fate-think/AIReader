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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore("reader_settings")
private val settingsKey = stringPreferencesKey("settings")
private val secretKey = stringPreferencesKey("api_key_encrypted")

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
    val storageError: String? = null,
    val loaded: Boolean = false
)

data class FinalRequestPreview(
    val modeName: String,
    val prompt: String,
    val processedArticle: String,
    val userMessage: String
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val preprocessSettings = PreprocessSettingsStore(context.dataStore)
    private val promptRepository = PromptRepository(context.dataStore) { file ->
        context.assets.open("prompts/$file").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        try {
            val prefs = context.dataStore.data.first()
            val settingsJson = runCatching { JSONObject(prefs[settingsKey] ?: "{}") }.getOrDefault(JSONObject())
            val settings = ApiSettings(
                settingsJson.optString("baseUrl"),
                runCatching { decrypt(prefs[secretKey] ?: "") }.getOrDefault(""),
                settingsJson.optString("modelId"),
                settingsJson.optString("temperature", "0.7"),
                settingsJson.optString("maxOutputTokens", "2048"),
                settingsJson.optBoolean("streaming", true)
            )
            val options = preprocessSettings.options.first()
            promptRepository.migrateIfNeeded()
            _state.value = _state.value.copy(settings = settings, preprocessOptions = options)
            promptRepository.catalogs.collect { catalog ->
                _state.value = _state.value.copy(
                    modes = catalog.modes,
                    selectedModeId = catalog.selectedModeId,
                    preparedUserMessage = null,
                    loaded = true
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            showStorageError(error)
            _state.value = _state.value.copy(loaded = true)
        }
    }

    private fun showStorageError(error: Exception) {
        _state.value = _state.value.copy(storageError = error.message ?: "无法读取或保存提示词数据")
    }

    fun clearStorageError() { _state.value = _state.value.copy(storageError = null) }

    fun selectMode(id: String) = viewModelScope.launch {
        try { promptRepository.select(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showStorageError(error) }
    }
    fun setArticle(value: String) { _state.value = _state.value.copy(article = value, preparedUserMessage = null) }
    fun clearArticle() = setArticle("")
    fun processedArticle(): String = ArticlePreprocessor.process(_state.value.article, _state.value.preprocessOptions)

    fun savePreprocessOptions(options: PreprocessOptions) {
        _state.value = _state.value.copy(preprocessOptions = options, preparedUserMessage = null)
        viewModelScope.launch { preprocessSettings.save(options) }
    }

    fun startReading() {
        val message = finalRequestPreview()?.userMessage ?: return
        _state.value = _state.value.copy(
            preparedUserMessage = message,
            result = "已准备好预处理文章和单条 User Message。当前尚未连接 AI 服务，未发送请求或生成 AI 结果。"
        )
    }

    fun finalRequestPreview(): FinalRequestPreview? {
        val mode = _state.value.modes.firstOrNull { it.id == _state.value.selectedModeId } ?: return null
        val article = processedArticle()
        return FinalRequestPreview(mode.name, mode.prompt, article, buildUserMessage(mode.prompt, article))
    }

    suspend fun saveMode(id: String?, name: String, prompt: String): Boolean {
        return try {
            if (id == null) promptRepository.add(name, prompt) else promptRepository.save(id, name, prompt)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            showStorageError(error)
            false
        }
    }

    fun deleteMode(id: String) = viewModelScope.launch {
        try { promptRepository.delete(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showStorageError(error) }
    }

    fun restoreDefault(id: String) = viewModelScope.launch {
        try { promptRepository.restoreDefault(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showStorageError(error) }
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
