package com.example.aireader

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private fun historyTime(timestamp: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

@Composable
fun ReadingHistoryListScreen(state: ReadingHistoryState, model: ReadingHistoryViewModel,
                             onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmBatch by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val currentIds = state.summaries.mapTo(HashSet<String>()) { it.id }
    val visibleSelected = selected.intersect(currentIds)
    Column(modifier) {
        OutlinedTextField(state.query, model::search, label = { Text("搜索标题或阅读模式") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { confirmBatch = true }, enabled = visibleSelected.isNotEmpty()) {
                Text("删除选中（${visibleSelected.size}）")
            }
            TextButton(onClick = { confirmClearAll = true }, enabled = state.totalCount > 0) {
                Text("清空全部历史")
            }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.summaries.isEmpty()) {
            Text(if (state.query.isBlank()) "还没有阅读历史。成功生成报告后会自动保存在本机。"
                else "没有找到匹配的阅读历史。", modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.summaries, key = { it.id }) { row ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(checked = row.id in visibleSelected,
                                onCheckedChange = { checked -> selected = if (checked) selected + row.id else selected - row.id })
                            Column(Modifier.weight(1f).clickable { onOpen(row.id) }.padding(12.dp)) {
                                Text(row.title, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(historyTime(row.createdAt), style = MaterialTheme.typography.bodySmall)
                                Text("${row.readingModeName} · ${row.modelId}",
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text(row.preview, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmBatch) AlertDialog(onDismissRequest = { confirmBatch = false },
        title = { Text("删除选中的 ${visibleSelected.size} 条历史？") },
        text = { Text("删除后无法恢复。不会影响提示词、API 配置和其他阅读记录。") },
        confirmButton = { TextButton(onClick = {
            model.deleteMany(visibleSelected) { success -> if (success) selected -= visibleSelected }
            confirmBatch = false
        }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmBatch = false }) { Text("取消") } })
    if (confirmClearAll) AlertDialog(onDismissRequest = { confirmClearAll = false },
        title = { Text("确定清空全部阅读历史？") },
        text = { Text("将删除数据库中的所有阅读报告及保存的文章原文，无法恢复。") },
        confirmButton = { TextButton(onClick = {
            model.clearAll { success -> if (success) selected = emptySet() }
            confirmClearAll = false
        }) { Text("清空全部") } },
        dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("取消") } })
}

@Composable
fun ReadingHistoryDetailScreen(state: ReadingHistoryState, model: ReadingHistoryViewModel,
                               onBack: () -> Unit, onPreviewHtml: (String) -> Unit,
                               fontSizePx: Int, modifier: Modifier = Modifier) {
    val record = state.detail
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()
    var showOriginal by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var titleDraft by remember(record?.id, rename) { mutableStateOf(record?.title.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var exportingHtml by remember { mutableStateOf(false) }
    val markdownLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) {
        model.completeMarkdownExport(it)
    }
    val htmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) {
        model.completeHtmlExport(it)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("← 返回阅读历史") }
        if (state.loadingDetail) { CircularProgressIndicator(); return@Column }
        if (record == null) {
            Text(state.message ?: "找不到这条阅读历史。")
            return@Column
        }
        Text(record.title, style = MaterialTheme.typography.titleLarge, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        Text("${historyTime(record.createdAt)} · ${record.readingModeName} · ${record.modelId}",
            style = MaterialTheme.typography.bodySmall)
        Text("API 配置：${record.apiConfigName}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { rename = true }) { Text("重命名") }
            if (record.originalArticle != null) TextButton(onClick = { showOriginal = true }) { Text("查看原文") }
            TextButton(onClick = { confirmDelete = true }) { Text("删除") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(record.markdownResult)) }) { Text("复制") }
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, record.markdownResult)
                }
                try { context.startActivity(Intent.createChooser(send, "分享阅读报告")) }
                catch (_: Exception) { model.reportFailure("无法打开分享面板。") }
            }) { Text("分享") }
            TextButton(onClick = {
                model.prepareMarkdownExport(record.markdownResult)
                markdownLauncher.launch("AIReader-history.md")
            }) { Text("保存 MD") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onPreviewHtml(record.markdownResult) }) { Text("HTML 预览") }
            TextButton(onClick = {
                exportingHtml = true
                scope.launch {
                    try {
                        if (model.prepareHtmlExport(record.markdownResult, fontSizePx)) {
                            htmlLauncher.launch("AIReader-history.html")
                        }
                    } finally { exportingHtml = false }
                }
            }, enabled = !exportingHtml) { Text(if (exportingHtml) "转换中…" else "保存 HTML") }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            MarkdownResult(record.markdownResult, scrollState, streaming = false)
        }
    }
    if (showOriginal && record?.originalArticle != null) AlertDialog(
        onDismissRequest = { showOriginal = false }, title = { Text("原始文章") },
        text = { Text(record.originalArticle,
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showOriginal = false }) { Text("关闭") } })
    if (rename && record != null) AlertDialog(onDismissRequest = { rename = false },
        title = { Text("修改历史标题") },
        text = { OutlinedTextField(titleDraft, { titleDraft = it }, label = { Text("标题") }) },
        confirmButton = { TextButton(onClick = {
            model.rename(record.id, titleDraft)
            rename = false
        }, enabled = titleDraft.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = { rename = false }) { Text("取消") } })
    if (confirmDelete && record != null) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("删除这条阅读历史？") },
        text = { Text("将删除报告及保存的文章原文，无法恢复。") },
        confirmButton = { TextButton(onClick = {
            model.delete(record.id) { success -> if (success) onBack() }
            confirmDelete = false
        }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}
