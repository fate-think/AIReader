package com.example.aireader

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

private val Context.dataStore by preferencesDataStore("reader_settings")

enum class ReadingStatus { Idle, Loading, WaitingForRetry, Retrying, Streaming, Completed,
    Cancelled, Interrupted, RetryExhausted, Error }

data class ApiSettings(
    val baseUrl: String = "",
    val modelId: String = "",
    val temperature: String = "0.3",
    val maxOutputTokens: String = "4096",
    val streaming: Boolean = true,
    val sendTemperature: Boolean = true,
    val useMaxCompletionTokens: Boolean = false,
    val allowHttp: Boolean = false,
    val hasApiKey: Boolean = false,
    val keyNeedsReentry: Boolean = false,
    val autoRetry: Boolean = true,
    val maxRetries: String = "2",
    val connectTimeoutSeconds: String = "20",
    val readTimeoutSeconds: String = "180",
    val retryHttp504: Boolean = false,
    val sendReasoningEffort: Boolean = false,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MAX
)

data class ReaderState(
    val modes: List<ReadingMode> = emptyList(),
    val selectedModeId: String? = null,
    val article: String = "",
    val preprocessOptions: PreprocessOptions = PreprocessOptions(),
    val historyOptions: HistorySaveOptions = HistorySaveOptions(),
    val settings: ApiSettings = ApiSettings(),
    val apiProfiles: List<ApiProfile> = emptyList(),
    val selectedApiProfileId: String? = null,
    val apiProfilesError: String? = null,
    val result: String = "",
    val markdownSource: MarkdownSource = MarkdownSource.NONE,
    val markdownViewMode: MarkdownViewMode = MarkdownViewMode.EDIT,
    val status: ReadingStatus = ReadingStatus.Idle,
    val readingError: String? = null,
    val retryMessage: String? = null,
    val settingsMessage: String? = null,
    val resultActionMessage: String? = null,
    val historySaveMessage: String? = null,
    val htmlFontSizePx: Int = 24,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeMessage: String? = null,
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

private data class HtmlCacheEntry(
    val markdown: String,
    val fontSizePx: Int,
    val theme: HtmlDocumentTheme,
    val html: String
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val preprocessSettings = PreprocessSettingsStore(context.dataStore)
    private val htmlSettings = HtmlSettingsStore(context.dataStore)
    private val themeSettings = ThemeSettingsStore(context.dataStore)
    private val historySettings = HistorySettingsStore(context.dataStore)
    private val historyRepository by lazy { ReadingHistoryRepository(ReadingHistoryDatabase.get(context).historyDao()) }
    private val themeSaveMutex = Mutex()
    private val historySaveMutex = Mutex()
    private val htmlConverter = MarkdownHtmlConverter()
    private val promptRepository = PromptRepository(context.dataStore) { file ->
        context.assets.open("prompts/$file").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
    private val apiProfilesRepository = ApiProfilesRepository(context.dataStore, AndroidKeystoreApiKeyCipher())
    private val client = OpenAiCompatibleClient()
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state
    private var selectedApiKey: String? = null
    private var readingJob: Job? = null
    @Volatile private var readingCall: Call? = null
    private var testJob: Job? = null
    @Volatile private var testCall: Call? = null
    private val requestGeneration = RequestGeneration()
    private var accumulated = StringBuilder()
    private var pendingExport: String? = null
    private var pendingHtmlExport: String? = null
    private var cachedHtml: HtmlCacheEntry? = null

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        try {
            try {
                apiProfilesRepository.migrateIfNeeded()
                applyApiCatalog(apiProfilesRepository.catalogs.first())
                viewModelScope.launch {
                    try { apiProfilesRepository.catalogs.collectLatest(::applyApiCatalog) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        _state.value = _state.value.copy(apiProfilesError = "API 配置数据无法读取，原数据未被覆盖。")
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _state.value = _state.value.copy(apiProfilesError = "API 配置迁移或读取失败，原数据未被覆盖。")
            }
            val options = preprocessSettings.options.first()
            val htmlFontSize = htmlSettings.fontSizePx.first()
            val themeMode = themeSettings.mode.first()
            val historyOptions = historySettings.options.first()
            promptRepository.migrateIfNeeded()
            _state.value = _state.value.copy(preprocessOptions = options,
                htmlFontSizePx = htmlFontSize, themeMode = themeMode, historyOptions = historyOptions)
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

    private suspend fun applyApiCatalog(catalog: ApiProfileCatalog) {
        val profile = catalog.selected
        val key = withContext(Dispatchers.IO) { apiProfilesRepository.loadKey(profile.id) }
        selectedApiKey = key.value
        _state.value = _state.value.copy(apiProfiles = catalog.profiles,
            selectedApiProfileId = profile.id,
            settings = toUiSettings(profile.config, key.value != null, key.needsReentry),
            apiProfilesError = null)
    }

    private fun toUiSettings(config: ApiConfig, hasKey: Boolean, keyNeedsReentry: Boolean): ApiSettings = ApiSettings(
        baseUrl = config.baseUrl,
        modelId = config.modelId,
        temperature = config.temperature.toString(),
        maxOutputTokens = config.maxOutputTokens.toString(),
        streaming = config.streaming,
        sendTemperature = config.sendTemperature,
        useMaxCompletionTokens = config.useMaxCompletionTokens,
        allowHttp = config.allowHttp,
        hasApiKey = hasKey,
        keyNeedsReentry = keyNeedsReentry,
        autoRetry = config.network.autoRetry,
        maxRetries = config.network.maxRetries.toString(),
        connectTimeoutSeconds = config.network.connectTimeoutSeconds.toString(),
        readTimeoutSeconds = config.network.readTimeoutSeconds.toString(),
        retryHttp504 = config.network.retryHttp504,
        sendReasoningEffort = config.reasoning.sendReasoningEffort,
        reasoningEffort = config.reasoning.reasoningEffort
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
        val retries = settings.maxRetries.toIntOrNull()?.takeIf { it in 0..5 }
            ?: throw ApiException("最大重试次数须为 0–5。")
        val connect = settings.connectTimeoutSeconds.toIntOrNull()?.takeIf { it in 10..60 }
            ?: throw ApiException("连接超时须为 10–60 秒。")
        val read = settings.readTimeoutSeconds.toIntOrNull()?.takeIf { it in 30..600 }
            ?: throw ApiException("读取超时须为 30–600 秒。")
        return ApiConfig(settings.baseUrl.trim(), settings.modelId.trim(), temperature, maxTokens,
            settings.streaming, settings.sendTemperature, settings.useMaxCompletionTokens,
            settings.allowHttp, NetworkStabilitySettings(settings.autoRetry, retries, connect, read,
                settings.retryHttp504), ReasoningSettings(settings.sendReasoningEffort,
                settings.reasoningEffort))
    }

    fun clearStorageError() { _state.value = _state.value.copy(storageError = null) }
    fun setThemeMode(mode: ThemeMode) {
        _state.value = _state.value.copy(themeMode = mode, themeMessage = null)
        viewModelScope.launch {
            try {
                themeSaveMutex.withLock {
                    if (_state.value.themeMode == mode) themeSettings.save(mode)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (_state.value.themeMode == mode) {
                    _state.value = _state.value.copy(themeMessage = "主题设置保存失败，请重试。")
                }
            }
        }
    }
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

    fun saveHistoryOptions(options: HistorySaveOptions) {
        _state.value = _state.value.copy(historyOptions = options, settingsMessage = null)
        viewModelScope.launch {
            try { historySaveMutex.withLock {
                if (_state.value.historyOptions == options) historySettings.save(options)
            } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (_state.value.historyOptions == options) {
                    val persisted = try { historySettings.options.first() }
                        catch (_: Exception) { options }
                    _state.value = _state.value.copy(historyOptions = persisted,
                        settingsMessage = "阅读历史设置保存失败，请重试。")
                }
            }
        }
    }

    fun finalRequestPreview(): FinalRequestPreview? = finalRequestPreview(_state.value)

    private fun finalRequestPreview(snapshot: ReaderState): FinalRequestPreview? {
        val mode = snapshot.modes.firstOrNull { it.id == snapshot.selectedModeId } ?: return null
        val processed = ArticlePreprocessor.process(snapshot.article, snapshot.preprocessOptions)
        return FinalRequestPreview(mode.name, mode.prompt, processed, buildUserMessage(mode.prompt, processed))
    }

    fun setMarkdown(text: String) {
        val current = _state.value
        if (readingJob?.isActive == true || current.result == text) return
        cachedHtml = null
        _state.value = current.withEditedMarkdown(text)
    }

    fun setMarkdownViewMode(mode: MarkdownViewMode) {
        if (readingJob?.isActive == true) return
        _state.value = _state.value.copy(markdownViewMode = mode)
    }

    fun startReading(confirmedOverwrite: Boolean = false) {
        if (readingJob?.isActive == true) return
        val snapshot = _state.value
        if (!confirmedOverwrite && MarkdownWorkspace.requiresOverwriteConfirmation(
                snapshot.result, snapshot.markdownSource)) return
        if (snapshot.article.isBlank()) { readingError("请先输入文章。"); return }
        val request = finalRequestPreview(snapshot) ?: run { readingError("请选择有效的阅读模式。"); return }
        val key = selectedApiKey?.takeIf { it.isNotBlank() }
            ?: run { readingError("请先为当前 API 配置填写 API Key。"); return }
        val config = try { configFrom(snapshot.settings) }
        catch (error: Exception) { readingError(ApiErrorMapper.message(error)); return }
        try { client.validate(config, key) }
        catch (error: Exception) { readingError(ApiErrorMapper.message(error)); return }

        val mode = snapshot.modes.first { it.id == snapshot.selectedModeId }
        val apiName = snapshot.apiProfiles.firstOrNull { it.id == snapshot.selectedApiProfileId }?.name.orEmpty()
        val historyCapture = HistoryCapture.start(snapshot.article, mode, config.modelId,
            apiName, snapshot.historyOptions)
        val generation = requestGeneration.next()
        accumulated = StringBuilder()
        cachedHtml = null
        _state.value = snapshot.withMarkdownGenerationStarted()
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
                                _state.value = _state.value.withGeneratedMarkdown(
                                    accumulated.toString(), ReadingStatus.Streaming)
                                lastUpdate = now
                            }
                        }
                        is ReadingEvent.RetryWaiting -> _state.value = _state.value.copy(
                            status = ReadingStatus.WaitingForRetry,
                            retryMessage = "服务器繁忙，约 ${event.seconds} 秒后重试（第 ${event.attempt}/${event.maxRetries} 次）。")
                        is ReadingEvent.Retrying -> _state.value = _state.value.copy(
                            status = ReadingStatus.Retrying,
                            retryMessage = "正在重试（第 ${event.attempt}/${event.maxRetries} 次）…")
                        ReadingEvent.RetryExhausted -> _state.value = _state.value.copy(
                            status = ReadingStatus.RetryExhausted, retryMessage = null)
                        ReadingEvent.Finished -> {
                            val finishedReport = accumulated.toString()
                            _state.value = _state.value.withGeneratedMarkdown(
                                finishedReport, ReadingStatus.Completed)
                            historyCapture.onEvent(event, finishedReport)?.let { record ->
                                viewModelScope.launch(Dispatchers.IO) {
                                    try { historyRepository.insertOnce(record) }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) {
                                        withContext(Dispatchers.Main) {
                                            if (requestGeneration.isCurrent(generation)) {
                                                _state.value = _state.value.withHistorySaveFailure()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                if (requestGeneration.isCurrent(generation)) {
                    _state.value = _state.value.withGeneratedMarkdown(
                        accumulated.toString(), ReadingStatus.Cancelled)
                }
            } catch (error: Exception) {
                if (requestGeneration.isCurrent(generation)) {
                    val status = when {
                        accumulated.isNotEmpty() -> ReadingStatus.Interrupted
                        _state.value.status == ReadingStatus.RetryExhausted -> ReadingStatus.RetryExhausted
                        else -> ReadingStatus.Error
                    }
                    _state.value = _state.value.withGeneratedMarkdown(
                        accumulated.toString(), status).copy(readingError = ApiErrorMapper.message(error))
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
        _state.value = _state.value.withGeneratedMarkdown(
            accumulated.toString(), ReadingStatus.Cancelled).copy(readingError = null)
    }

    fun clearResult() {
        if (readingJob?.isActive == true) return
        requestGeneration.invalidate()
        accumulated = StringBuilder()
        cachedHtml = null
        _state.value = _state.value.withClearedMarkdown()
    }

    fun prepareMarkdownExport(): Boolean {
        val report = _state.value.result
        if (report.isEmpty()) return false
        pendingExport = report
        _state.value = _state.value.copy(resultActionMessage = null)
        return true
    }

    fun completeMarkdownExport(uri: Uri?) {
        val report = pendingExport
        pendingExport = null
        if (uri == null) return
        if (report == null) {
            _state.value = _state.value.copy(resultActionMessage = "保存未完成，请重新选择文件。")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        MarkdownExport.write(it, report)
                    } ?: throw IllegalStateException("无法打开目标文件")
                }
                _state.value = _state.value.copy(resultActionMessage = "Markdown 文件已保存。")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _state.value = _state.value.copy(resultActionMessage = "保存失败，请重新选择位置。")
            }
        }
    }

    fun reportShareFailure() {
        _state.value = _state.value.copy(resultActionMessage = "无法打开分享面板。")
    }

    fun setHtmlFontSize(value: Int) {
        if (value !in 14..36) return
        _state.value = _state.value.copy(htmlFontSizePx = value)
        cachedHtml = null
        viewModelScope.launch {
            try { htmlSettings.saveFontSize(value) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _state.value = _state.value.copy(resultActionMessage = "HTML 字体大小保存失败。")
            }
        }
    }

    suspend fun htmlForCurrentResult(fontSizePx: Int = _state.value.htmlFontSizePx,
                                     theme: HtmlDocumentTheme = HtmlDocumentTheme.AUTO): String? {
        val snapshot = _state.value
        if (!MarkdownWorkspace.canUseHtml(snapshot.result, readingJob?.isActive == true)) return null
        cachedHtml?.takeIf { it.markdown == snapshot.result && it.fontSizePx == fontSizePx &&
            it.theme == theme }?.let { return it.html }
        return withContext(Dispatchers.Default) {
            htmlConverter.convert(snapshot.result, fontSizePx, theme)
        }.also { cachedHtml = HtmlCacheEntry(snapshot.result, fontSizePx, theme, it) }
    }

    suspend fun prepareHtmlExport(): Boolean {
        return try {
            val html = htmlForCurrentResult() ?: return false
            pendingHtmlExport = html
            _state.value = _state.value.copy(resultActionMessage = null)
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            _state.value = _state.value.copy(resultActionMessage = "HTML 生成失败。")
            false
        }
    }

    suspend fun browserHtmlUri(): Uri? {
        return try {
            val html = htmlForCurrentResult() ?: return null
            withContext(Dispatchers.IO) {
                val dir = java.io.File(context.cacheDir, "html_reports").apply { mkdirs() }
                val file = java.io.File(dir, "AIReader-${java.util.UUID.randomUUID()}.html")
                file.outputStream().use { HtmlExport.write(it, html) }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            _state.value = _state.value.copy(resultActionMessage = "无法准备浏览器文件，请使用 HTML 预览或保存 HTML。")
            null
        }
    }

    fun completeHtmlExport(uri: Uri?) {
        val html = pendingHtmlExport
        pendingHtmlExport = null
        if (uri == null) return
        if (html == null) {
            _state.value = _state.value.copy(resultActionMessage = "保存未完成，请重新选择文件。")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { HtmlExport.write(it, html) }
                        ?: throw IllegalStateException("无法打开目标文件")
                }
                _state.value = _state.value.copy(resultActionMessage = "HTML 文件已保存。")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _state.value = _state.value.copy(resultActionMessage = "保存 HTML 失败，请重新选择位置。")
            }
        }
    }

    fun reportHtmlFailure(message: String) {
        _state.value = _state.value.copy(resultActionMessage = message)
    }

    fun saveSettings(profileId: String, name: String, settings: ApiSettings,
                     newKey: String, deleteKey: Boolean) {
        viewModelScope.launch {
            try {
                val config = configFrom(settings)
                apiProfilesRepository.save(profileId, name, config, newKey, deleteKey)
                _state.value = _state.value.copy(settingsMessage = "API 配置已保存。")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _state.value = _state.value.copy(settingsMessage = ApiErrorMapper.message(error))
            }
        }
    }

    fun testConnection(settings: ApiSettings, newKey: String, deleteKey: Boolean) {
        if (testJob?.isActive == true) return
        val key = if (newKey.isNotBlank()) newKey else if (deleteKey) null else selectedApiKey
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

    fun selectApiProfile(id: String) = viewModelScope.launch {
        try { apiProfilesRepository.select(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.value = _state.value.copy(settingsMessage = "无法切换 API 配置。") }
    }

    fun addApiProfile() = viewModelScope.launch {
        try { apiProfilesRepository.add("新 API 配置") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.value = _state.value.copy(settingsMessage = "无法新增 API 配置。") }
    }

    fun copyApiProfile(id: String, name: String) = viewModelScope.launch {
        try {
            apiProfilesRepository.copy(id, "$name 副本")
            _state.value = _state.value.copy(settingsMessage = "已复制配置参数；API Key 未复制。")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.value = _state.value.copy(settingsMessage = "无法复制 API 配置。") }
    }

    fun deleteApiProfile(id: String) = viewModelScope.launch {
        try { apiProfilesRepository.delete(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.value = _state.value.copy(settingsMessage = "无法删除 API 配置。") }
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
