package com.example.aireader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(
            android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        setContent {
            val model: ReaderViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                App(state, model)
            }
        }
    }
}

@Composable
private fun App(state: ReaderState, model: ReaderViewModel) {
    var page by rememberSaveable { mutableStateOf(0) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var htmlPreview by remember { mutableStateOf<String?>(null) }
    val appScope = rememberCoroutineScope()
    htmlPreview?.let { html ->
        HtmlPreview(html, state.htmlFontSizePx, onFontSizeChange = { size ->
            model.setHtmlFontSize(size)
            appScope.launch {
                try {
                    model.htmlForCurrentResult(size)?.let {
                        if (htmlPreview != null && model.state.value.htmlFontSizePx == size) htmlPreview = it
                    }
                }
                catch (_: Exception) { model.reportHtmlFailure("HTML 预览刷新失败。") }
            }
        }, onClose = { htmlPreview = null })
        return
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = { Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Text(when { creating || editingId != null -> "编辑阅读模式"; page == 0 -> "AI 阅读助手"; page == 1 -> "提示词管理"; else -> "API 设置" },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.statusBarsPadding().padding(20.dp))
        } },
        bottomBar = { if (!creating && editingId == null) NavigationBar {
            listOf("阅读", "提示词", "设置").forEachIndexed { index, label ->
                NavigationBarItem(selected = page == index, onClick = { page = index },
                    icon = { Text(listOf("◉", "✎", "⚙")[index]) }, label = { Text(label) })
            }
        } }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        when {
            !state.loaded -> Box(modifier) { CircularProgressIndicator() }
            state.modes.isEmpty() -> Text("提示词数据读取失败：${state.storageError ?: "未知错误"}", modifier = modifier)
            creating || editingId != null -> ModeEditor(
                mode = state.modes.firstOrNull { it.id == editingId },
                onSave = { name, prompt -> model.saveMode(editingId, name, prompt) },
                onSaved = { creating = false; editingId = null },
                onCancel = { creating = false; editingId = null },
                onRestore = state.modes.firstOrNull { it.id == editingId && it.builtIn }?.let { mode ->
                    { model.restoreDefault(mode.id); editingId = null }
                },
                onDelete = state.modes.firstOrNull { it.id == editingId && !it.builtIn }?.let { mode ->
                    { model.deleteMode(mode.id); editingId = null }
                },
                modifier = modifier)
            page == 0 -> ReaderPage(state, model, onHtmlPreview = { htmlPreview = it }, modifier = modifier)
            page == 1 -> ModesPage(state, model, onEdit = { editingId = it },
                onAdd = { creating = true }, modifier = modifier)
            else -> SettingsPage(state, model, modifier)
        }
    }
    if (state.modes.isNotEmpty() && state.storageError != null) {
        AlertDialog(onDismissRequest = model::clearStorageError,
            title = { Text("提示词保存失败") }, text = { Text(state.storageError.orEmpty()) },
            confirmButton = { TextButton(onClick = model::clearStorageError) { Text("知道了") } })
    }
}

@Composable
private fun ReaderPage(state: ReaderState, model: ReaderViewModel,
                       onHtmlPreview: (String) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var showPreprocessSettings by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val resultScroll = rememberLazyListState()
    val userDraggingResult by resultScroll.interactionSource.collectIsDraggedAsState()
    var followResult by remember { mutableStateOf(true) }
    var confirmClearResult by remember { mutableStateOf(false) }
    var moreActions by remember { mutableStateOf(false) }
    var htmlBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) {
        model.completeMarkdownExport(it)
    }
    val htmlExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) {
        model.completeHtmlExport(it)
    }
    val active = state.status in setOf(ReadingStatus.Loading, ReadingStatus.WaitingForRetry,
        ReadingStatus.Retrying, ReadingStatus.Streaming)
    val htmlReady = state.status == ReadingStatus.Completed
    LaunchedEffect(state.status) {
        if (state.status == ReadingStatus.Loading) followResult = true
    }
    LaunchedEffect(userDraggingResult, resultScroll) {
        if (userDraggingResult) {
            snapshotFlow {
                val info = resultScroll.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                last != null && last.index == info.totalItemsCount - 1 &&
                    last.offset + last.size <= info.viewportEndOffset + 8
            }.collect { followResult = it }
        }
    }
    LaunchedEffect(state.result, active, followResult) {
        if (active && followResult && state.result.isNotEmpty()) {
            val lastChunk = MarkdownDocument.streamingChunks(state.result).lastIndex
            resultScroll.scrollToItem(lastChunk, 100_000)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("阅读模式", style = MaterialTheme.typography.titleMedium)
        Text("当前模型：${state.settings.modelId.ifBlank { "未配置" }}",
            style = MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(state.modes.firstOrNull { it.id == state.selectedModeId }?.name ?: "选择阅读模式")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Text("内置阅读模式", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium)
                state.modes.filter { it.builtIn }.forEach { mode -> DropdownMenuItem(text = { Text(mode.name) },
                    onClick = { model.selectMode(mode.id); expanded = false }) }
                HorizontalDivider()
                Text("我的自定义提示词", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium)
                state.modes.filterNot { it.builtIn }.forEach { mode -> DropdownMenuItem(text = { Text(mode.name) },
                    onClick = { model.selectMode(mode.id); expanded = false }) }
            }
        }
        OutlinedTextField(value = state.article, onValueChange = model::setArticle,
            label = { Text("文章内容") }, placeholder = { Text("在这里粘贴文章") },
            maxLines = 10,
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 280.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("字符数：${state.article.length}")
            TextButton(onClick = model::clearArticle, enabled = state.article.isNotEmpty()) { Text("清空文章") }
        }
        OutlinedButton(onClick = { showPreprocessSettings = true }, modifier = Modifier.fillMaxWidth()) {
            Text("文章预处理设置")
        }
        OutlinedButton(onClick = { preview = model.processedArticle() },
            enabled = state.article.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("预览处理结果")
        }
        var finalPreview by remember { mutableStateOf<FinalRequestPreview?>(null) }
        OutlinedButton(onClick = { finalPreview = model.finalRequestPreview() },
            enabled = state.article.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("预览最终发送内容")
        }
        Button(onClick = { if (active) model.stopReading() else { keyboard?.hide(); model.startReading() } },
            enabled = active || state.article.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (state.status == ReadingStatus.WaitingForRetry) "取消重试" else if (active) "停止生成" else "开始阅读")
        }
        if (!active && state.status != ReadingStatus.Idle) {
            OutlinedButton(onClick = { keyboard?.hide(); model.startReading() }, enabled = state.article.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text("重新生成") }
        }
        HorizontalDivider()
        Text("AI 阅读结果", style = MaterialTheme.typography.titleMedium)
        Text(when (state.status) {
            ReadingStatus.Idle -> "等待开始"
            ReadingStatus.Loading -> "正在连接…"
            ReadingStatus.WaitingForRetry -> state.retryMessage ?: "等待重试…"
            ReadingStatus.Retrying -> state.retryMessage ?: "正在重试…"
            ReadingStatus.Streaming -> "正在生成…"
            ReadingStatus.Completed -> "已完成"
            ReadingStatus.Cancelled -> "已停止"
            ReadingStatus.Interrupted -> "生成中断"
            ReadingStatus.RetryExhausted -> "重试次数已用尽"
            ReadingStatus.Error -> "请求失败"
        })
        if (active) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.readingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.result.isNotEmpty()) {
            if (active && !followResult) {
                TextButton(onClick = { followResult = true }) { Text("回到底部") }
            }
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                MarkdownResult(state.result, resultScroll, streaming = active)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(state.result)) },
                    modifier = Modifier.weight(1f)) { Text("复制 Markdown") }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { moreActions = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("更多操作")
                    }
                    DropdownMenu(expanded = moreActions, onDismissRequest = { moreActions = false }) {
                        DropdownMenuItem(text = { Text("HTML 预览") }, enabled = htmlReady && !htmlBusy,
                            onClick = {
                                moreActions = false; htmlBusy = true
                                scope.launch {
                                    try { model.htmlForCurrentResult()?.let(onHtmlPreview) }
                                    catch (_: Exception) { model.reportHtmlFailure("HTML 预览生成失败。") }
                                    finally { htmlBusy = false }
                                }
                            })
                        DropdownMenuItem(text = { Text("浏览器打开") }, enabled = htmlReady && !htmlBusy,
                            onClick = {
                                moreActions = false; htmlBusy = true
                                scope.launch {
                                    try {
                                        model.browserHtmlUri()?.let { uri ->
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "text/html")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            try { context.startActivity(intent) }
                                            catch (_: Exception) {
                                                model.reportHtmlFailure("浏览器可能不支持直接打开此文件。可使用 HTML 预览，或保存 HTML 后用支持本地文件的浏览器打开。")
                                            }
                                        }
                                    } finally { htmlBusy = false }
                                }
                            })
                        DropdownMenuItem(text = { Text("保存 HTML") }, enabled = htmlReady && !htmlBusy,
                            onClick = {
                                moreActions = false; htmlBusy = true
                                scope.launch {
                                    try {
                                        if (model.prepareHtmlExport()) htmlExportLauncher.launch("AIReader-阅读报告.html")
                                    } finally { htmlBusy = false }
                                }
                            })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("分享结果") }, onClick = {
                            moreActions = false
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, state.result)
                            }
                            try { context.startActivity(Intent.createChooser(send, "分享阅读结果")) }
                            catch (_: Exception) { model.reportShareFailure() }
                        })
                        DropdownMenuItem(text = { Text("保存 Markdown") }, onClick = {
                            moreActions = false
                            if (model.prepareMarkdownExport()) exportLauncher.launch("AIReader-report.md")
                        })
                        DropdownMenuItem(text = { Text("清空结果") }, enabled = !active,
                            onClick = { moreActions = false; confirmClearResult = true })
                    }
                }
            }
            if (htmlBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("HTML 阅读字号：${state.htmlFontSizePx}px", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { model.setHtmlFontSize(state.htmlFontSizePx - 1) },
                    enabled = state.htmlFontSizePx > 14) { Text("A−") }
                OutlinedButton(onClick = { model.setHtmlFontSize(state.htmlFontSizePx + 1) },
                    enabled = state.htmlFontSizePx < 36) { Text("A+") }
            }
            state.resultActionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        finalPreview?.let { request ->
            FinalRequestPreviewDialog(request, onDismiss = { finalPreview = null })
        }
    }
    if (showPreprocessSettings) PreprocessSettingsDialog(state.preprocessOptions,
        onSave = { model.savePreprocessOptions(it); showPreprocessSettings = false },
        onDismiss = { showPreprocessSettings = false })
    preview?.let { processed ->
        PreviewDialog(processed, state.article.length, onDismiss = { preview = null })
    }
    if (confirmClearResult) ConfirmModeActionDialog("清空阅读结果？", "这会清除当前报告，不影响文章和提示词。",
        onConfirm = { model.clearResult(); confirmClearResult = false },
        onDismiss = { confirmClearResult = false })
}

@Composable
private fun FinalRequestPreviewDialog(request: FinalRequestPreview, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.86f)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("预览最终发送内容", style = MaterialTheme.typography.titleLarge)
                Text(request.userMessage,
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { clipboard.setText(AnnotatedString(request.userMessage)) }) { Text("复制完整内容") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun PreprocessSettingsDialog(initial: PreprocessOptions, onSave: (PreprocessOptions) -> Unit,
                                     onDismiss: () -> Unit) {
    var enabled by remember(initial) { mutableStateOf(initial.enabled) }
    var paraOnce by remember(initial) { mutableStateOf(initial.paraOnce.toString()) }
    var detectTitles by remember(initial) { mutableStateOf(initial.detectTitles) }
    var detectLists by remember(initial) { mutableStateOf(initial.detectNumberedLists) }
    val count = paraOnce.toIntOrNull()
    val validCount = count?.let { it in 1..100 } == true
    AlertDialog(onDismissRequest = onDismiss, title = { Text("文章预处理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("启用预处理")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(value = paraOnce, onValueChange = { paraOnce = it },
                    label = { Text("每组合并段数（1–100）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !validCount, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("自动识别标题")
                    Switch(checked = detectTitles, onCheckedChange = { detectTitles = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("自动识别编号列表")
                    Switch(checked = detectLists, onCheckedChange = { detectLists = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(PreprocessOptions(enabled, count ?: 3, detectTitles, detectLists))
            }, enabled = validCount) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun PreviewDialog(processed: String, originalLength: Int, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("预览处理结果", style = MaterialTheme.typography.titleLarge)
                Text("处理前：$originalLength 字符 · 处理后：${processed.length} 字符")
                Text(processed, modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { clipboard.setText(AnnotatedString(processed)) }) { Text("复制预处理结果") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun ModesPage(state: ReaderState, model: ReaderViewModel, onEdit: (String) -> Unit,
                      onAdd: () -> Unit, modifier: Modifier) {
    var pendingRestore by remember { mutableStateOf<ReadingMode?>(null) }
    var pendingDelete by remember { mutableStateOf<ReadingMode?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("内置提示词", style = MaterialTheme.typography.titleLarge)
        state.modes.filter { it.builtIn }.forEach { mode -> ModeCard(mode, onEdit,
            onRestore = { pendingRestore = mode }, onDelete = null) }
        HorizontalDivider()
        Text("我的自定义提示词", style = MaterialTheme.typography.titleLarge)
        state.modes.filterNot { it.builtIn }.forEach { mode -> ModeCard(mode, onEdit,
            onRestore = null, onDelete = { pendingDelete = mode }) }
        if (state.modes.none { !it.builtIn }) Text("还没有自定义阅读模式")
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("+ 新增提示词") }
    }
    pendingRestore?.let { mode ->
        ConfirmModeActionDialog("恢复默认提示词？", "将从 APK 内置文件恢复“${mode.name}”的提示词正文。",
            onConfirm = { model.restoreDefault(mode.id); pendingRestore = null },
            onDismiss = { pendingRestore = null })
    }
    pendingDelete?.let { mode ->
        ConfirmModeActionDialog("删除自定义提示词？", "将删除“${mode.name}”。此操作不可撤销。",
            onConfirm = { model.deleteMode(mode.id); pendingDelete = null },
            onDismiss = { pendingDelete = null })
    }
}

@Composable
private fun ConfirmModeActionDialog(title: String, message: String,
                                    onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun ModeCard(mode: ReadingMode, onEdit: (String) -> Unit,
                     onRestore: (() -> Unit)?, onDelete: (() -> Unit)?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(mode.name, style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(onClick = { onEdit(mode.id) }) { Text("编辑") }
                if (onRestore != null) TextButton(onClick = onRestore) { Text("恢复默认") }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun ModeEditor(mode: ReadingMode?, onSave: suspend (String, String) -> Boolean,
                       onSaved: () -> Unit, onCancel: () -> Unit, onRestore: (() -> Unit)?,
                       onDelete: (() -> Unit)?, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(mode?.id) { mutableStateOf(mode?.name ?: "") }
    var prompt by rememberSaveable(mode?.id) { mutableStateOf(mode?.prompt ?: "") }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val hasChanges = name != (mode?.name ?: "") || prompt != (mode?.prompt ?: "")
    val leave = { if (hasChanges) confirmDiscard = true else onCancel() }
    BackHandler { if (!saving) leave() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("模式名称") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("提示词") },
            modifier = Modifier.fillMaxWidth().height(360.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                saving = true
                scope.launch {
                    val success = onSave(name, prompt)
                    saving = false
                    if (success) onSaved()
                }
            }, enabled = !saving && name.isNotBlank() && prompt.isNotBlank()) { Text("保存") }
            OutlinedButton(onClick = leave, enabled = !saving) { Text("取消") }
        }
        if (onRestore != null) OutlinedButton(onClick = { confirmRestore = true }, enabled = !saving) { Text("恢复默认") }
        if (onDelete != null) TextButton(onClick = { confirmDelete = true }, enabled = !saving) { Text("删除此提示词") }
    }
    if (confirmDiscard) ConfirmModeActionDialog("放弃未保存修改？", "编辑内容尚未保存。",
        onConfirm = { confirmDiscard = false; onCancel() },
        onDismiss = { confirmDiscard = false })
    if (confirmRestore && onRestore != null) ConfirmModeActionDialog("恢复默认提示词？",
        "将从 APK 内置文件恢复原始提示词；未保存的编辑内容会丢失。",
        onConfirm = { confirmRestore = false; onRestore() },
        onDismiss = { confirmRestore = false })
    if (confirmDelete && onDelete != null) ConfirmModeActionDialog("删除自定义提示词？",
        "此模式将被删除，未保存的编辑内容也会丢失。",
        onConfirm = { confirmDelete = false; onDelete() },
        onDismiss = { confirmDelete = false })
}

@Composable
private fun SettingsPage(state: ReaderState, model: ReaderViewModel, modifier: Modifier) {
    val initial = state.settings
    var baseUrl by remember(initial) { mutableStateOf(initial.baseUrl) }
    var newApiKey by remember(initial) { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var deleteApiKey by remember(initial) { mutableStateOf(false) }
    var modelId by remember(initial) { mutableStateOf(initial.modelId) }
    var temperature by remember(initial) { mutableStateOf(initial.temperature) }
    var maxTokens by remember(initial) { mutableStateOf(initial.maxOutputTokens) }
    var streaming by remember(initial) { mutableStateOf(initial.streaming) }
    var sendTemperature by remember(initial) { mutableStateOf(initial.sendTemperature) }
    var useMaxCompletionTokens by remember(initial) { mutableStateOf(initial.useMaxCompletionTokens) }
    var allowHttp by remember(initial) { mutableStateOf(initial.allowHttp) }
    var autoRetry by remember(initial) { mutableStateOf(initial.autoRetry) }
    var retryHttp504 by remember(initial) { mutableStateOf(initial.retryHttp504) }
    var maxRetries by remember(initial) { mutableStateOf(initial.maxRetries) }
    var connectTimeout by remember(initial) { mutableStateOf(initial.connectTimeoutSeconds) }
    var readTimeout by remember(initial) { mutableStateOf(initial.readTimeoutSeconds) }
    var sendReasoningEffort by remember(initial) { mutableStateOf(initial.sendReasoningEffort) }
    var reasoningEffort by remember(initial) { mutableStateOf(initial.reasoningEffort) }
    var reasoningExpanded by remember { mutableStateOf(false) }
    val draft = ApiSettings(baseUrl, modelId, temperature, maxTokens, streaming,
        sendTemperature, useMaxCompletionTokens, allowHttp, initial.hasApiKey, initial.keyNeedsReentry,
        autoRetry, maxRetries, connectTimeout, readTimeout, retryHttp504,
        sendReasoningEffort, reasoningEffort)
    val valid = maxTokens.toIntOrNull()?.let { it > 0 } == true &&
        (!sendTemperature || temperature.toDoubleOrNull()?.let { it in 0.0..2.0 } == true) &&
        maxRetries.toIntOrNull()?.let { it in 0..5 } == true &&
        connectTimeout.toIntOrNull()?.let { it in 10..60 } == true &&
        readTimeout.toIntOrNull()?.let { it in 30..600 } == true
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("阅读文章会发送至您配置的第三方 API 服务。测试连接使用下方当前参数，可能产生调用费用。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("API Base URL") }, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("允许 HTTP Base URL")
            Switch(checked = allowHttp, onCheckedChange = { allowHttp = it })
        }
        if (allowHttp) {
            Text("HTTP 会以明文传输 API Key 和文章内容，请仅在信任该连接时使用。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text(when {
            initial.keyNeedsReentry -> "原有 API Key 无法解密，请重新输入。"
            deleteApiKey -> "保存后将删除已配置的 API Key。"
            initial.hasApiKey -> "API Key：已配置。留空可保留现有密钥。"
            else -> "API Key：未配置。"
        })
        OutlinedTextField(newApiKey, { newApiKey = it; if (it.isNotEmpty()) deleteApiKey = false },
            label = { Text("新 API Key（留空保留原值）") },
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showApiKey = !showApiKey }) { Text(if (showApiKey) "隐藏密钥" else "显示密钥") }
            if (initial.hasApiKey || initial.keyNeedsReentry) {
                TextButton(onClick = { deleteApiKey = !deleteApiKey; if (deleteApiKey) newApiKey = "" }) {
                    Text(if (deleteApiKey) "取消删除" else "删除现有密钥")
                }
            }
        }
        OutlinedTextField(modelId, { modelId = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(temperature, { temperature = it }, label = { Text("Temperature") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(maxTokens, { maxTokens = it }, label = { Text("Max Output Tokens") }, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("发送 Temperature 参数")
            Switch(checked = sendTemperature, onCheckedChange = { sendTemperature = it })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("使用 max_completion_tokens")
            Switch(checked = useMaxCompletionTokens, onCheckedChange = { useMaxCompletionTokens = it })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("流式输出")
            Switch(checked = streaming, onCheckedChange = { streaming = it })
        }
        HorizontalDivider()
        Text("思考深度", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("发送思考深度参数")
            Switch(checked = sendReasoningEffort, onCheckedChange = { sendReasoningEffort = it })
        }
        Text("开启后，向 API 请求添加 reasoning_effort。关闭后不发送该参数，由模型服务端决定默认行为。",
            style = MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(onClick = { reasoningExpanded = true }, enabled = sendReasoningEffort,
                modifier = Modifier.fillMaxWidth()) {
                Text("思考深度：${reasoningEffort.displayName}")
            }
            DropdownMenu(expanded = reasoningExpanded && sendReasoningEffort,
                onDismissRequest = { reasoningExpanded = false }) {
                ReasoningEffort.entries.forEach { effort ->
                    DropdownMenuItem(text = { Text(effort.displayName) }, onClick = {
                        reasoningEffort = effort
                        reasoningExpanded = false
                    })
                }
            }
        }
        HorizontalDivider()
        Text("网络稳定性", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("自动重试")
            Switch(checked = autoRetry, onCheckedChange = { autoRetry = it })
        }
        Text("仅针对符合条件的临时错误自动重试，可能产生额外 API 调用费用。",
            style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("允许重试 504")
            Switch(checked = retryHttp504, onCheckedChange = { retryHttp504 = it })
        }
        Text("504 网关超时不代表上游未执行；开启后可能重复请求和计费。总自动重试关闭时此项不生效。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(maxRetries, { maxRetries = it }, label = { Text("最大重试次数（0–5）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = maxRetries.toIntOrNull()?.let { it in 0..5 } != true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(connectTimeout, { connectTimeout = it }, label = { Text("连接超时（10–60 秒）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = connectTimeout.toIntOrNull()?.let { it in 10..60 } != true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(readTimeout, { readTimeout = it }, label = { Text("读取超时（30–600 秒）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = readTimeout.toIntOrNull()?.let { it in 30..600 } != true,
            modifier = Modifier.fillMaxWidth())
        Button(onClick = { model.saveSettings(draft, newApiKey, deleteApiKey) }, enabled = valid,
            modifier = Modifier.fillMaxWidth()) { Text("保存配置") }
        OutlinedButton(onClick = { model.testConnection(draft, newApiKey, deleteApiKey) },
            enabled = valid && !state.testingConnection, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.testingConnection) "正在测试连接…" else "测试连接")
        }
        state.settingsMessage?.let { Text(it) }
    }
}
