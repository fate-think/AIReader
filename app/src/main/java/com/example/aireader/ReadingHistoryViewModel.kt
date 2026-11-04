package com.example.aireader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReadingHistoryState(
    val query: String = "",
    val summaries: List<ReadingHistorySummary> = emptyList(),
    val totalCount: Int = 0,
    val detail: ReadingHistoryEntity? = null,
    val loadingDetail: Boolean = false,
    val message: String? = null
)

class ReadingHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository by lazy {
        ReadingHistoryRepository(ReadingHistoryDatabase.get(application).historyDao())
    }
    private val _state = MutableStateFlow(ReadingHistoryState())
    val state: StateFlow<ReadingHistoryState> = _state
    private val query = MutableStateFlow("")
    private var detailJob: Job? = null
    private var pendingMarkdown: String? = null
    private var pendingHtml: String? = null

    init {
        observeSummaries()
        viewModelScope.launch {
            try {
                repository.count().catch { _state.value = _state.value.copy(message = "无法统计阅读历史。") }
                    .collectLatest { count -> _state.value = _state.value.copy(totalCount = count) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.value = _state.value.copy(message = "无法打开阅读历史数据库。") }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeSummaries() = viewModelScope.launch {
        try {
            query.debounce(300).flatMapLatest { text ->
                repository.summaries(text).catch {
                    _state.value = _state.value.copy(message = "无法读取阅读历史，请重试。")
                    emit(emptyList())
                }
            }.collectLatest { rows -> _state.value = _state.value.copy(summaries = rows) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.value = _state.value.copy(message = "无法打开阅读历史数据库。") }
    }

    fun search(value: String) {
        _state.value = _state.value.copy(query = value)
        query.value = value
    }

    fun open(id: String) {
        detailJob?.cancel()
        _state.value = _state.value.copy(detail = null, loadingDetail = true, message = null)
        detailJob = viewModelScope.launch {
            try {
                val record = withContext(Dispatchers.IO) { repository.find(id) }
                _state.value = _state.value.copy(detail = record, loadingDetail = false,
                    message = if (record == null) "这条历史记录已不存在。" else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _state.value = _state.value.copy(loadingDetail = false,
                    message = "无法打开阅读历史，请重试。")
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        detailJob = null
        pendingMarkdown = null
        pendingHtml = null
        _state.value = _state.value.copy(detail = null, loadingDetail = false, message = null)
    }

    fun rename(id: String, title: String) = viewModelScope.launch {
        try {
            val changed = withContext(Dispatchers.IO) { repository.rename(id, title) }
            if (changed) _state.value = _state.value.copy(
                detail = _state.value.detail?.takeIf { it.id == id }?.copy(title = title.trim()) ?: _state.value.detail,
                message = "标题已更新。")
            else _state.value = _state.value.copy(message = "记录已不存在。")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.value = _state.value.copy(message = "修改标题失败，请重试。") }
    }

    fun delete(id: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        try {
            val deleted = withContext(Dispatchers.IO) { repository.delete(id) }
            if (deleted) closeDetail()
            onDone(deleted)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            _state.value = _state.value.copy(message = "删除历史失败，请重试。")
            onDone(false)
        }
    }

    fun deleteMany(ids: Set<String>, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { repository.deleteMany(ids) }
            onDone(true)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            _state.value = _state.value.copy(message = "批量删除失败，请重试。")
            onDone(false)
        }
    }

    fun clearAll(onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { repository.clearAll() }
            onDone(true)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            _state.value = _state.value.copy(message = "清空历史失败，请重试。")
            onDone(false)
        }
    }

    fun prepareMarkdownExport(markdown: String) { pendingMarkdown = markdown }
    fun completeMarkdownExport(uri: Uri?) {
        val markdown = pendingMarkdown
        pendingMarkdown = null
        if (uri == null || markdown == null) return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                    MarkdownExport.write(it, markdown)
                } ?: error("Cannot open document")
            }; _state.value = _state.value.copy(message = "Markdown 已保存。") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.value = _state.value.copy(message = "保存 Markdown 失败。") }
        }
    }

    suspend fun prepareHtmlExport(markdown: String, fontSizePx: Int): Boolean = try {
        pendingHtml = withContext(Dispatchers.Default) {
            MarkdownHtmlConverter().convert(markdown, fontSizePx, HtmlDocumentTheme.AUTO)
        }
        true
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) {
        _state.value = _state.value.copy(message = "生成 HTML 失败。")
        false
    }

    fun completeHtmlExport(uri: Uri?) {
        val html = pendingHtml
        pendingHtml = null
        if (uri == null || html == null) return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                    HtmlExport.write(it, html)
                } ?: error("Cannot open document")
            }; _state.value = _state.value.copy(message = "HTML 已保存。") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.value = _state.value.copy(message = "保存 HTML 失败。") }
        }
    }

    fun reportFailure(message: String) { _state.value = _state.value.copy(message = message) }
}
