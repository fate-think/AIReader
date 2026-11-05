package com.example.aireader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHomeScreen(onOpen: (SettingsDestination) -> Unit, modifier: Modifier = Modifier) {
    val entries = settingsHomeDestinations.map { destination -> when (destination) {
        SettingsDestination.API_LIST -> Triple(destination, "管理服务商、模型与请求参数", R.drawable.ic_smart_toy)
        SettingsDestination.APPEARANCE -> Triple(destination, "跟随系统、浅色与深色", R.drawable.ic_dark_mode)
        SettingsDestination.HISTORY_DATA -> Triple(destination, "历史保存、原文存储与数据清理", R.drawable.ic_history)
        SettingsDestination.ABOUT -> Triple(destination, "版本信息与应用说明", null)
        else -> error("不是设置首页菜单")
    } }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp)) {
        items(entries) { (destination, description, icon) ->
            ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(destination) }) {
                ListItem(headlineContent = { Text(destination.title) },
                    supportingContent = { Text(description) },
                    leadingContent = {
                        if (icon == null) Icon(Icons.Outlined.Info, contentDescription = null)
                        else Icon(painterResource(icon), contentDescription = null)
                    },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) })
            }
        }
    }
}

@Composable
fun ApiSettingsList(state: ReaderState, model: ReaderViewModel,
                    onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    var pendingDelete by remember { mutableStateOf<ApiProfile?>(null) }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp)) {
        item {
            Text("已保存的 API 配置", style = MaterialTheme.typography.titleLarge)
            state.apiProfilesError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        items(state.apiProfiles, key = { it.id }) { profile ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${if (profile.id == state.selectedApiProfileId) "当前 · " else ""}${profile.name}",
                        style = MaterialTheme.typography.titleMedium)
                    Text(profile.config.modelId.ifBlank { "未配置模型" },
                        style = MaterialTheme.typography.bodySmall)
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { model.selectApiProfile(profile.id) },
                            enabled = profile.id != state.selectedApiProfileId,
                            modifier = Modifier.weight(1f)) { Text("使用") }
                        TextButton(onClick = {
                            if (profile.id != state.selectedApiProfileId) model.selectApiProfile(profile.id)
                            onEdit(profile.id)
                        }, modifier = Modifier.weight(1f)) {
                            Text(if (profile.id == state.selectedApiProfileId) "编辑" else "使用并编辑")
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { model.copyApiProfile(profile.id, profile.name) },
                            modifier = Modifier.weight(1f)) { Text("复制") }
                        TextButton(onClick = { pendingDelete = profile },
                            enabled = state.apiProfiles.size > 1,
                            modifier = Modifier.weight(1f)) { Text("删除") }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { model.addApiProfile() }, modifier = Modifier.fillMaxWidth()) {
                Text("新增 API 配置")
            }
            state.settingsMessage?.let { Text(it) }
        }
    }
    pendingDelete?.let { profile ->
        ConfirmModeActionDialog("删除 API 配置？", "将删除“${profile.name}”及其独立保存的密钥。",
            onConfirm = { model.deleteApiProfile(profile.id); pendingDelete = null },
            onDismiss = { pendingDelete = null })
    }
}

@Composable
fun AppearanceSettingsScreen(state: ReaderState, model: ReaderViewModel, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("选择应用主题", style = MaterialTheme.typography.titleLarge)
        ThemeMode.entries.forEach { mode ->
            Row(modifier = Modifier.fillMaxWidth().clickable { model.setThemeMode(mode) }.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioButton(selected = state.themeMode == mode, onClick = null)
                Text(mode.displayName, modifier = Modifier.padding(top = 12.dp))
            }
        }
        state.themeMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun HistoryDataSettingsScreen(state: ReaderState, model: ReaderViewModel,
                              historyModel: ReadingHistoryViewModel,
                              historyState: ReadingHistoryState,
                              onOpenHistory: () -> Unit, modifier: Modifier = Modifier) {
    var confirmClearHistory by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("本地阅读历史", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("自动保存阅读历史")
            Switch(checked = state.historyOptions.autoSave, onCheckedChange = {
                model.saveHistoryOptions(state.historyOptions.copy(autoSave = it))
            })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("保存文章原文")
            Switch(checked = state.historyOptions.saveOriginalArticle,
                enabled = state.historyOptions.autoSave, onCheckedChange = {
                    model.saveHistoryOptions(state.historyOptions.copy(saveOriginalArticle = it))
                })
        }
        Text("历史仅保存在本机，应用已禁用 Android 自动备份。卸载应用会删除本机历史；无法保证恢复。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
            Text("查看阅读历史")
        }
        OutlinedButton(onClick = { confirmClearHistory = true }, modifier = Modifier.fillMaxWidth()) {
            Text("清空全部阅读历史")
        }
        historyState.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.settingsMessage?.let { Text(it) }
    }
    if (confirmClearHistory) ConfirmModeActionDialog("确定清空全部阅读历史？",
        "全部已保存的报告和文章原文将从本机删除，无法恢复。提示词、API 配置及密钥不会改变。",
        onConfirm = { historyModel.clearAll(); confirmClearHistory = false },
        onDismiss = { confirmClearHistory = false })
}

@Suppress("DEPRECATION")
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "无法读取版本信息"
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("AIReader", style = MaterialTheme.typography.headlineMedium)
        Text("版本：$version")
        Text("AIReader 是一款基于 Kotlin 和 Jetpack Compose 开发的 Android AI 阅读助手，支持自定义阅读提示词、文章预处理、OpenAI 兼容接口及本地阅读历史。")
        HorizontalDivider()
        Text("数据与隐私", style = MaterialTheme.typography.titleMedium)
        Text("API 请求发送到您配置的服务地址。API Key 使用 Android Keystore 加密保存。阅读历史保存在本机，是否保存由阅读历史设置控制。文件导出与分享均由您主动发起。")
    }
}

@Composable
fun ApiSettingsEditor(state: ReaderState, model: ReaderViewModel,
                      onBack: () -> Unit, modifier: Modifier) {
    val selectedProfile = state.apiProfiles.firstOrNull { it.id == state.selectedApiProfileId }
    val initial = state.settings
    val editorKey = selectedProfile?.id to initial
    var profileName by remember(selectedProfile?.id) { mutableStateOf(selectedProfile?.name.orEmpty()) }
    var confirmDiscard by remember(editorKey) { mutableStateOf(false) }
    var baseUrl by remember(editorKey) { mutableStateOf(initial.baseUrl) }
    var newApiKey by remember(editorKey) { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var deleteApiKey by remember(editorKey) { mutableStateOf(false) }
    var modelId by remember(editorKey) { mutableStateOf(initial.modelId) }
    var temperature by remember(editorKey) { mutableStateOf(initial.temperature) }
    var maxTokens by remember(editorKey) { mutableStateOf(initial.maxOutputTokens) }
    var streaming by remember(editorKey) { mutableStateOf(initial.streaming) }
    var sendTemperature by remember(editorKey) { mutableStateOf(initial.sendTemperature) }
    var useMaxCompletionTokens by remember(editorKey) { mutableStateOf(initial.useMaxCompletionTokens) }
    var allowHttp by remember(editorKey) { mutableStateOf(initial.allowHttp) }
    var autoRetry by remember(editorKey) { mutableStateOf(initial.autoRetry) }
    var retryHttp504 by remember(editorKey) { mutableStateOf(initial.retryHttp504) }
    var maxRetries by remember(editorKey) { mutableStateOf(initial.maxRetries) }
    var connectTimeout by remember(editorKey) { mutableStateOf(initial.connectTimeoutSeconds) }
    var readTimeout by remember(editorKey) { mutableStateOf(initial.readTimeoutSeconds) }
    var sendReasoningEffort by remember(editorKey) { mutableStateOf(initial.sendReasoningEffort) }
    var reasoningEffort by remember(editorKey) { mutableStateOf(initial.reasoningEffort) }
    var reasoningExpanded by remember { mutableStateOf(false) }
    val draft = ApiSettings(baseUrl, modelId, temperature, maxTokens, streaming,
        sendTemperature, useMaxCompletionTokens, allowHttp, initial.hasApiKey, initial.keyNeedsReentry,
        autoRetry, maxRetries, connectTimeout, readTimeout, retryHttp504,
        sendReasoningEffort, reasoningEffort)
    val valid = profileName.isNotBlank() && selectedProfile != null &&
        maxTokens.toIntOrNull()?.let { it > 0 } == true &&
        (!sendTemperature || temperature.toDoubleOrNull()?.let { it in 0.0..2.0 } == true) &&
        maxRetries.toIntOrNull()?.let { it in 0..5 } == true &&
        connectTimeout.toIntOrNull()?.let { it in 10..60 } == true &&
        readTimeout.toIntOrNull()?.let { it in 30..600 } == true
    val changed = selectedProfile != null && (
        profileName != selectedProfile.name || baseUrl != initial.baseUrl ||
        modelId != initial.modelId || temperature != initial.temperature ||
        maxTokens != initial.maxOutputTokens || streaming != initial.streaming ||
        sendTemperature != initial.sendTemperature ||
        useMaxCompletionTokens != initial.useMaxCompletionTokens || allowHttp != initial.allowHttp ||
        autoRetry != initial.autoRetry || retryHttp504 != initial.retryHttp504 ||
        maxRetries != initial.maxRetries || connectTimeout != initial.connectTimeoutSeconds ||
        readTimeout != initial.readTimeoutSeconds ||
        sendReasoningEffort != initial.sendReasoningEffort ||
        reasoningEffort != initial.reasoningEffort || newApiKey.isNotEmpty() || deleteApiKey)
    val requestBack = { if (changed) confirmDiscard = true else onBack() }
    BackHandler(onBack = requestBack)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = requestBack) { Text("← 返回配置列表") }
        Text("基本连接", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(profileName, { profileName = it }, label = { Text("配置名称") },
            isError = profileName.isBlank(), modifier = Modifier.fillMaxWidth())
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
        HorizontalDivider()
        Text("模型参数", style = MaterialTheme.typography.titleLarge)
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
        Button(onClick = { selectedProfile?.let {
            model.saveSettings(it.id, profileName, draft, newApiKey, deleteApiKey)
        } }, enabled = valid,
            modifier = Modifier.fillMaxWidth()) { Text("保存配置") }
        OutlinedButton(onClick = { model.testConnection(draft, newApiKey, deleteApiKey) },
            enabled = valid && !state.testingConnection, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.testingConnection) "正在测试连接…" else "测试连接")
        }
        state.settingsMessage?.let { Text(it) }
    }
    if (confirmDiscard) ConfirmModeActionDialog("放弃未保存的 API 修改？",
        "当前编辑内容尚未保存。",
        onConfirm = { confirmDiscard = false; onBack() },
        onDismiss = { confirmDiscard = false })
}
