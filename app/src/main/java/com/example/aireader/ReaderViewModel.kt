package com.example.aireader

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Call

private val Context.dataStore by preferencesDataStore("reader_settings")

enum class ReadingStatus { Idle, Loading, Streaming, Completed, Cancelled, Error }

data class ApiSettings(
    val baseUrl: String = "",
    val modelId: String = "",
    val temperature: String = "0.3",
    val maxOutputTokens: String = "4096",
    val streaming: Boolean = true,
    val sendTemperature: Boolean = true,
    val useMaxCompletionTokens: Boolean = false,
    val hasApiKey: Boolean = false,
    val keyNeedsReentry: Boolean = false
)

data class ReaderState(
    val modes: List<ReadingMode> = emptyList(),
    val selectedModeId: String? = null,
    val article: String = "",
    val preprocessOptions: PreprocessOptions = PreprocessOptions(),
    val settings: ApiSettings = ApiSettings(),
    val result: String = "",
    val status: ReadingStatus = ReadingStatus.Idle,
    val readingError: String? = null,
    val settingsMessage: String? = null,
    val testingConnection: Boolean = false,
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
    private val apiSettingsRepository = ApiSettingsRepository(context.dataStore, AndroidKeystoreApiKeyCipher())
    private val client = OpenAiCompatibleClient()
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state
    private var apiKey: String? = null
    private var readingJob: Job? = null
    @Volatile private var readingCall: Call? = null
    private var testJob: Job? = null
    @Volatile private var testCall: Call? = null
    private val requestGeneration = RequestGeneration()
    private var accumulated = StringBuilder()

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        try {
            val loadedSettings = apiSettingsRepository.load()
            apiKey = loadedSettings.apiKey
            val options = preprocessSettings.options.first()
            promptRepository.migrateIfNeeded()
            _state.value = _state.value.copy(settings = toUiSettings(loadedSettings), preprocessOptions = options)
            promptRepository.catalogs.collect { catalog ->
                _state.value = _state.value.copy(
                    modes = catalog.modes, selectedModeId = catalog.selectedModeId, loaded = true
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(
                storageError = "无法读取已保存数据，请检查应用数据。不会覆盖现有提示词。", loaded = true
            )
        }
    }

    private fun toUiSettings(loaded: LoadedApiSettings): ApiSettings = ApiSettings(
        baseUrl = loaded.config.baseUrl,
        modelId = loaded.config.modelId,
        temperature = loaded.config.temperature.toString(),
        maxOutputTokens = loaded.config.maxOutputTokens.toString(),
        streaming = loaded.config.streaming,
        sendTemperature = loaded.config.sendTemperature,
        useMaxCompletionTokens = loaded.config.useMaxCompletionTokens,
        hasApiKey = !loaded.apiKey.isNullOrEmpty(),
        keyNeedsReentry = loaded.requiresKeyReentry
    )

    private fun configFrom(settings: ApiSettings): ApiConfig {
        val parsedTemperature = settings.temperature.toDoubleOrNull()?.takeIf { it.isFinite() }
        val temperature = parsedTemperature
            ?: if (settings.sendTemperature) throw ApiException("Temperature 必须是有效数字。") else 0.3
        val maxTokens = settings.maxOutputTokens.toIntOrNull()
            ?: throw ApiException("Max Output Tokens 必须是整数。")
        if (settings.sendTemperature && temperature !in 0.0..2.0) {
            throw ApiException("Temperature 应在 0 到 2 之间。")
        }
        if (maxTokens <= 0) throw ApiException("Max Output Tokens 必须大于 0。")
        return ApiConfig(settings.baseUrl.trim(), settings.modelId.trim(), temperature, maxTokens,
            settings.streaming, settings.sendTemperature, settings.useMaxCompletionTokens)
    }

    fun clearStorageError() { _state.value = _state.value.copy(storageError = null) }
    fun selectMode(id: String) = viewModelScope.launch {
        try { promptRepository.select(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.value = _state.value.copy(storageError = "无法保存阅读模式选择。") }
    }
    fun setArticle(value: String) { _state.value = _state.value.copy(article = value) }
    fun clearArticle() = setArticle("")
    fun processedArticle(): String = ArticlePreprocessor.process(_state.value.article, _state.value.preprocessOptions)

    fun savePreprocessOptions(options: PreprocessOptions) {
        _state.value = _state.value.copy(preprocessOptions = options)
        viewModelScope.launch { preprocessSettings.save(options) }
    }

    fun finalRequestPreview(): FinalRequestPreview? = finalRequestPreview(_state.value)

    private fun finalRequestPreview(snapshot: ReaderState): FinalRequestPreview? {
        val mode = snapshot.modes.firstOrNull { it.id == snapshot.selectedModeId } ?: return null
        val processed = ArticlePreprocessor.process(snapshot.article, snapshot.preprocessOptions)
        return FinalRequestPreview(mode.name, mode.prompt, processed, buildUserMessage(mode.prompt, processed))
    }

    fun startReading() {
        if (readingJob?.isActive == true) return
        val snapshot = _state.value
        if (snapshot.article.isBlank()) { readingError("请先输入文章。"); return }
        val request = finalRequestPreview(snapshot) ?: run { readingError("请选择有效的阅读模式。"); return }
        val key = apiKey?.takeIf { it.isNotBlank() } ?: run { readingError("请先配置 API Key。"); return }
        val config = try { configFrom(snapshot.settings) }
        catch (error: Exception) { readingError(ApiErrorMapper.message(error)); return }
        try { client.validate(config, key) }
        catch (error: Exception) { readingError(ApiErrorMapper.message(error)); return }

        val generation = requestGeneration.next()
        accumulated = StringBuilder()
        _state.value = snapshot.copy(result = "", status = ReadingStatus.Loading, readingError = null)
        readingJob = viewModelScope.launch {
            var lastUpdate = 0L
            try {
                client.complete(config, key, request.userMessage, onCallCreated = { call ->
                    if (requestGeneration.isCurrent(generation)) readingCall = call else call.cancel()
                }).collect { event ->
                    if (!requestGeneration.isCurrent(generation)) return@collect
                    when (event) {
                        is ReadingEvent.Text -> {
                            accumulated.append(event.value)
                            val now = System.nanoTime()
                            if (now - lastUpdate >= 50_000_000L) {
                                _state.value = _state.value.copy(result = accumulated.toString(), status = ReadingStatus.Streaming)
                                lastUpdate = now
                            }
                        }
                        ReadingEvent.Finished -> {
                            _state.value = _state.value.copy(result = accumulated.toString(), status = ReadingStatus.Completed)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                if (requestGeneration.isCurrent(generation)) {
                    _state.value = _state.value.copy(result = accumulated.toString(), status = ReadingStatus.Cancelled)
                }
            } catch (error: Exception) {
                if (requestGeneration.isCurrent(generation)) {
                    _state.value = _state.value.copy(result = accumulated.toString(), status = ReadingStatus.Error,
                        readingError = ApiErrorMapper.message(error))
                }
            } finally {
                if (requestGeneration.isCurrent(generation)) {
                    readingCall = null
                    readingJob = null
                }
            }
        }
    }

    private fun readingError(message: String) {
        _state.value = _state.value.copy(status = ReadingStatus.Error, readingError = message)
    }

    fun stopReading() {
        if (readingJob?.isActive != true) return
        requestGeneration.invalidate()
        readingCall?.cancel()
        readingJob?.cancel()
        readingCall = null
        readingJob = null
        _state.value = _state.value.copy(result = accumulated.toString(), status = ReadingStatus.Cancelled,
            readingError = null)
    }

    fun saveSettings(settings: ApiSettings, newKey: String, deleteKey: Boolean) {
        viewModelScope.launch {
            try {
                val config = configFrom(settings)
                val loaded = apiSettingsRepository.save(config, newKey.takeIf { it.isNotBlank() }, deleteKey)
                apiKey = loaded.apiKey
                _state.value = _state.value.copy(settings = toUiSettings(loaded), settingsMessage = "API 配置已保存。")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _state.value = _state.value.copy(settingsMessage = ApiErrorMapper.message(error))
            }
        }
    }

    fun testConnection(settings: ApiSettings, newKey: String, deleteKey: Boolean) {
        if (testJob?.isActive == true) return
        val key = if (newKey.isNotBlank()) newKey else if (deleteKey) null else apiKey
        if (key.isNullOrBlank()) {
            _state.value = _state.value.copy(settingsMessage = "请先输入 API Key，再测试连接。")
            return
        }
        val config = try { configFrom(settings) }
        catch (error: Exception) {
            _state.value = _state.value.copy(settingsMessage = ApiErrorMapper.message(error))
            return
        }
        _state.value = _state.value.copy(testingConnection = true, settingsMessage = null)
        testJob = viewModelScope.launch {
            try {
                client.testConnection(config, key, onCallCreated = { testCall = it })
                _state.value = _state.value.copy(settingsMessage = "连接成功：密钥、模型和响应格式可用。")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _state.value = _state.value.copy(settingsMessage = "测试失败：${ApiErrorMapper.message(error)}")
            } finally {
                testCall = null
                testJob = null
                _state.value = _state.value.copy(testingConnection = false)
            }
        }
    }

    suspend fun saveMode(id: String?, name: String, prompt: String): Boolean = try {
        if (id == null) promptRepository.add(name, prompt) else promptRepository.save(id, name, prompt)
        true
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) {
        _state.value = _state.value.copy(storageError = "提示词保存失败，原内容已保留。")
        false
    }

    fun deleteMode(id: String) = viewModelScope.launch {
        try { promptRepository.delete(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.value = _state.value.copy(storageError = "提示词删除失败。") }
    }

    fun restoreDefault(id: String) = viewModelScope.launch {
        try { promptRepository.restoreDefault(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.value = _state.value.copy(storageError = "恢复默认提示词失败。") }
    }

    override fun onCleared() {
        readingCall?.cancel()
        testCall?.cancel()
        super.onCleared()
    }
}
