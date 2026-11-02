package com.example.aireader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model: ReaderViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            MaterialTheme { App(state, model) }
        }
    }
}

@Composable
private fun App(state: ReaderState, model: ReaderViewModel) {
    var page by rememberSaveable { mutableStateOf(0) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { Surface(shadowElevation = 2.dp) {
            Text(when { creating || editingId != null -> "编辑阅读模式"; page == 0 -> "AI 阅读助手"; page == 1 -> "提示词管理"; else -> "API 设置" },
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
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
            page == 0 -> ReaderPage(state, model, modifier)
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
private fun ReaderPage(state: ReaderState, model: ReaderViewModel, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var showPreprocessSettings by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val active = state.status == ReadingStatus.Loading || state.status == ReadingStatus.Streaming
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("阅读模式", style = MaterialTheme.typography.titleMedium)
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp))
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
        Button(onClick = { if (active) model.stopReading() else model.startReading() },
            enabled = active || state.article.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (active) "停止生成" else "开始阅读")
        }
        if (!active && state.status != ReadingStatus.Idle) {
            OutlinedButton(onClick = model::startReading, enabled = state.article.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text("重新生成") }
        }
        HorizontalDivider()
        Text("AI 阅读结果", style = MaterialTheme.typography.titleMedium)
        Text(when (state.status) {
            ReadingStatus.Idle -> "等待开始"
            ReadingStatus.Loading -> "正在连接…"
            ReadingStatus.Streaming -> "正在生成…"
            ReadingStatus.Completed -> "已完成"
            ReadingStatus.Cancelled -> "已停止"
            ReadingStatus.Error -> "请求失败"
        })
        if (active) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.readingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.result.isNotEmpty()) {
            MarkdownResult(state.result)
            TextButton(onClick = { clipboard.setText(AnnotatedString(state.result)) }) { Text("复制结果") }
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
    val draft = ApiSettings(baseUrl, modelId, temperature, maxTokens, streaming,
        sendTemperature, useMaxCompletionTokens, initial.hasApiKey, initial.keyNeedsReentry)
    val valid = maxTokens.toIntOrNull()?.let { it > 0 } == true &&
        (!sendTemperature || temperature.toDoubleOrNull()?.let { it in 0.0..2.0 } == true)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("阅读文章会发送至您配置的第三方 API 服务，测试连接可能产生少量费用。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("API Base URL") }, modifier = Modifier.fillMaxWidth())
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
        Button(onClick = { model.saveSettings(draft, newApiKey, deleteApiKey) }, enabled = valid,
            modifier = Modifier.fillMaxWidth()) { Text("保存配置") }
        OutlinedButton(onClick = { model.testConnection(draft, newApiKey, deleteApiKey) },
            enabled = valid && !state.testingConnection, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.testingConnection) "正在测试连接…" else "测试连接")
        }
        state.settingsMessage?.let { Text(it) }
    }
}
