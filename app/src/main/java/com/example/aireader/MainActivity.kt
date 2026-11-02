package com.example.aireader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

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
    var page by remember { mutableStateOf(0) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
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
            creating || editingId != null -> ModeEditor(
                mode = state.modes.firstOrNull { it.id == editingId },
                onSave = { name, prompt -> model.saveMode(editingId, name, prompt); creating = false; editingId = null },
                onCancel = { creating = false; editingId = null }, modifier = modifier)
            page == 0 -> ReaderPage(state, model, modifier)
            page == 1 -> ModesPage(state, model, onEdit = { editingId = it },
                onAdd = { creating = true }, modifier = modifier)
            else -> SettingsPage(state.settings, model::saveSettings, modifier)
        }
    }
}

@Composable
private fun ReaderPage(state: ReaderState, model: ReaderViewModel, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("阅读模式", style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(state.modes.firstOrNull { it.id == state.selectedModeId }?.name ?: "选择阅读模式")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.modes.forEach { mode -> DropdownMenuItem(text = { Text(mode.name) },
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
        Button(onClick = model::startReading, enabled = state.article.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("开始阅读")
        }
        HorizontalDivider()
        Text("AI 阅读结果", style = MaterialTheme.typography.titleMedium)
        Text(state.result, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModesPage(state: ReaderState, model: ReaderViewModel, onEdit: (String) -> Unit,
                      onAdd: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("内置提示词", style = MaterialTheme.typography.titleLarge)
        state.modes.filter { it.builtIn }.forEach { mode -> ModeCard(mode, onEdit,
            onRestore = { model.restoreDefault(mode.id) }, onDelete = null) }
        HorizontalDivider()
        Text("自定义提示词", style = MaterialTheme.typography.titleLarge)
        state.modes.filterNot { it.builtIn }.forEach { mode -> ModeCard(mode, onEdit,
            onRestore = null, onDelete = { model.deleteMode(mode.id) }) }
        if (state.modes.none { !it.builtIn }) Text("还没有自定义阅读模式")
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("新增自定义提示词") }
    }
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
private fun ModeEditor(mode: ReadingMode?, onSave: (String, String) -> Unit,
                       onCancel: () -> Unit, modifier: Modifier) {
    var name by remember(mode?.id) { mutableStateOf(mode?.name ?: "") }
    var prompt by remember(mode?.id) { mutableStateOf(mode?.prompt ?: "") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("模式名称") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("提示词") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(name, prompt) }, enabled = name.isNotBlank() && prompt.isNotBlank()) { Text("保存") }
            OutlinedButton(onClick = onCancel) { Text("取消") }
        }
    }
}

@Composable
private fun SettingsPage(initial: ApiSettings, onSave: (ApiSettings) -> Unit, modifier: Modifier) {
    var baseUrl by remember(initial) { mutableStateOf(initial.baseUrl) }
    var apiKey by remember(initial) { mutableStateOf(initial.apiKey) }
    var modelId by remember(initial) { mutableStateOf(initial.modelId) }
    var temperature by remember(initial) { mutableStateOf(initial.temperature) }
    var maxTokens by remember(initial) { mutableStateOf(initial.maxOutputTokens) }
    var streaming by remember(initial) { mutableStateOf(initial.streaming) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("API Base URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(modelId, { modelId = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(temperature, { temperature = it }, label = { Text("Temperature") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(maxTokens, { maxTokens = it }, label = { Text("Max Output Tokens") }, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("流式输出")
            Switch(checked = streaming, onCheckedChange = { streaming = it })
        }
        Button(onClick = { onSave(ApiSettings(baseUrl, apiKey, modelId, temperature, maxTokens, streaming)) },
            enabled = temperature.toDoubleOrNull()?.let { it in 0.0..2.0 } == true &&
                maxTokens.toIntOrNull()?.let { it > 0 } == true,
            modifier = Modifier.fillMaxWidth()) { Text("保存设置") }
    }
}
