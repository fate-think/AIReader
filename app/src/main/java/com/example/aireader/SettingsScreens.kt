package com.example.aireader

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

internal data class ApiEditorSnapshot(val name: String, val settings: ApiSettings) {
    fun hasUnsavedChanges(name: String, settings: ApiSettings, newKey: String, deleteKey: Boolean): Boolean =
        this.name != name || this.settings != settings.withoutKeyStatus() ||
            newKey.isNotEmpty() || deleteKey
}

private data class ApiTestNotice(val id: Int, val message: String, val success: Boolean)

private fun ApiSettings.withoutKeyStatus(): ApiSettings =
    copy(hasApiKey = false, keyNeedsReentry = false)

@Composable
fun SettingsHomeScreen(onOpen: (SettingsDestination) -> Unit, modifier: Modifier = Modifier) {
    val entries = settingsHomeDestinations.map { destination -> when (destination) {
        SettingsDestination.API_LIST -> Triple(destination, "模型、思考深度、超时与重试", R.drawable.ic_smart_toy)
        SettingsDestination.APPEARANCE -> Triple(destination, "跟随系统、浅色、深色", R.drawable.ic_dark_mode)
        SettingsDestination.LANGUAGE -> Triple(destination, "简体中文、繁體中文、English", null)
        SettingsDestination.HISTORY_DATA -> Triple(destination, "自动保存、原文存储、历史清理", R.drawable.ic_history)
        SettingsDestination.ABOUT -> Triple(destination, "版本信息、应用说明", null)
        else -> error("不是设置首页菜单")
    } }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp)) {
        items(entries) { (destination, description, icon) ->
            ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(destination) }) {
                ListItem(headlineContent = { UiText(destination.title) },
                    supportingContent = { UiText(description) },
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
                    onEdit: (String) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<ApiProfile?>(null) }
    var profilesExpanded by remember { mutableStateOf(false) }
    var globalExpanded by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable { profilesExpanded = !profilesExpanded }
                .padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                UiText("API 配置列表", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                UiText(if (profilesExpanded) "⌃" else "⌄")
            }
            if (profilesExpanded) {
                HorizontalDivider()
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.apiProfilesError?.let { UiText(it, color = MaterialTheme.colorScheme.error) }
                state.apiProfiles.forEach { profile ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    androidx.compose.material3.Text("${if (profile.id == state.selectedApiProfileId) context.uiString("当前") + " · " else ""}${profile.name}",
                        style = MaterialTheme.typography.titleMedium, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    androidx.compose.material3.Text(profile.config.modelId.ifBlank { context.uiString("未配置模型") },
                        style = MaterialTheme.typography.bodySmall, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { model.selectApiProfile(profile.id, notifySuccess = true) },
                            enabled = profile.id != state.selectedApiProfileId,
                            modifier = Modifier.weight(1f)) { UiText("使用") }
                        TextButton(onClick = {
                            if (profile.id != state.selectedApiProfileId) model.selectApiProfile(profile.id)
                            onEdit(profile.id)
                        }, modifier = Modifier.weight(1f)) {
                            UiText(if (profile.id == state.selectedApiProfileId) "编辑" else "使用并编辑")
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { model.copyApiProfile(profile.id, profile.name) },
                            modifier = Modifier.weight(1f)) { UiText("复制") }
                        TextButton(onClick = { pendingDelete = profile },
                            enabled = state.apiProfiles.size > 1,
                            modifier = Modifier.weight(1f)) { UiText("删除") }
                    }
                }
            }
                }
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                UiText("新增 API 配置")
            }
            state.settingsMessage?.let { UiText(it) }
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable { globalExpanded = !globalExpanded }
                .padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                UiText("全局参数", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                UiText(if (globalExpanded) "⌃" else "⌄")
            }
            state.globalApiSettingsMessage?.let {
                UiText(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error)
            }
            if (globalExpanded) {
                HorizontalDivider()
                GlobalApiSettingsForm(state, model, Modifier.padding(16.dp))
            }
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UiText("选择应用主题", style = MaterialTheme.typography.titleLarge)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Row(modifier = Modifier.fillMaxWidth().clickable { model.setThemeMode(mode) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = state.themeMode == mode, onClick = null)
                        UiText(mode.displayName, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
        state.themeMessage?.let { UiText(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun LanguageSettingsScreen(state: ReaderState, model: ReaderViewModel, modifier: Modifier = Modifier) {
    val systemLocale = LocalContext.current.resources.configuration.locales[0]
    val selected = state.languageMode ?: LanguageMode.systemFallback(systemLocale)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UiText("选择界面语言", style = MaterialTheme.typography.titleLarge)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                LanguageMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.fillMaxWidth().clickable { model.setLanguageMode(mode) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = selected == mode, onClick = null)
                        androidx.compose.material3.Text(mode.nativeName,
                            modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
        if (state.languageMode == null) UiText("当前按系统语言显示；其他系统语言使用简体中文。",
            style = MaterialTheme.typography.bodySmall)
        state.settingsMessage?.let { UiText(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun HistoryDataSettingsScreen(state: ReaderState, model: ReaderViewModel,
                              historyModel: ReadingHistoryViewModel,
                              historyState: ReadingHistoryState,
                              onOpenHistory: () -> Unit, modifier: Modifier = Modifier) {
    var confirmClearHistory by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiText("本地阅读历史", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            UiText("自动保存阅读历史")
            Switch(checked = state.historyOptions.autoSave, onCheckedChange = {
                model.saveHistoryOptions(state.historyOptions.copy(autoSave = it))
            })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            UiText("保存文章原文")
            Switch(checked = state.historyOptions.saveOriginalArticle,
                enabled = state.historyOptions.autoSave, onCheckedChange = {
                    model.saveHistoryOptions(state.historyOptions.copy(saveOriginalArticle = it))
                })
        }
        UiText("历史仅保存在本机，应用已禁用 Android 自动备份。卸载应用会删除本机历史；无法保证恢复。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
            UiText("查看阅读历史")
        }
        OutlinedButton(onClick = { confirmClearHistory = true }, modifier = Modifier.fillMaxWidth()) {
            UiText("清空全部阅读历史")
        }
        historyState.message?.let { UiText(it, color = MaterialTheme.colorScheme.error) }
        state.settingsMessage?.let { UiText(it) }
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
    var linkError by remember { mutableStateOf(false) }
    fun openLink(url: String) {
        linkError = try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            false
        } catch (_: Exception) { true }
    }
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "无法读取版本信息"
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UiText("AIReader", style = MaterialTheme.typography.headlineMedium)
        UiText("版本：$version")
        UiText("AIReader 是一款基于 Kotlin 和 Jetpack Compose 开发的 Android AI 阅读助手，支持自定义阅读提示词、文章预处理、OpenAI 兼容接口及本地阅读历史。")
        HorizontalDivider()
        UiText("开源项目", style = MaterialTheme.typography.titleMedium)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column {
                ListItem(headlineContent = { UiText("开源地址") }, supportingContent = { UiText("GitHub · AIReader") },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
                    modifier = Modifier.clickable { openLink("https://github.com/fate-think/AIReader") })
                HorizontalDivider()
                ListItem(headlineContent = { UiText("开源协议") }, supportingContent = { UiText("Apache License 2.0") },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
                    modifier = Modifier.clickable { openLink("https://github.com/fate-think/AIReader/blob/main/LICENSE") })
            }
        }
        if (linkError) UiText("无法打开浏览器，请检查设备上的浏览器应用。",
            color = MaterialTheme.colorScheme.error)
        HorizontalDivider()
        UiText("数据与隐私", style = MaterialTheme.typography.titleMedium)
        UiText("API 请求发送到您配置的服务地址。API Key 使用 Android Keystore 加密保存。阅读历史保存在本机，是否保存由阅读历史设置控制。文件导出与分享均由您主动发起。")
    }
}

@Composable
fun ApiSettingsEditor(state: ReaderState, model: ReaderViewModel, isNew: Boolean,
                      onBack: () -> Unit, onSaved: () -> Unit,
                      snackbarHostState: SnackbarHostState, modifier: Modifier) {
    val selectedProfile = if (isNew) null else state.apiProfiles.firstOrNull { it.id == state.selectedApiProfileId }
    val uiContext = LocalContext.current
    val initial = if (isNew) ApiSettings() else state.settings
    val editorId = selectedProfile?.id ?: "new"
    var savedSnapshot by remember(editorId) {
        mutableStateOf(ApiEditorSnapshot(selectedProfile?.name.orEmpty(), initial.withoutKeyStatus()))
    }
    var profileName by remember(editorId) { mutableStateOf(selectedProfile?.name.orEmpty()) }
    var confirmDiscard by remember(editorId) { mutableStateOf(false) }
    var saving by remember(editorId) { mutableStateOf(false) }
    var baseUrl by remember(editorId) { mutableStateOf(initial.baseUrl) }
    var newApiKey by remember(editorId) { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var deleteApiKey by remember(editorId) { mutableStateOf(false) }
    var modelId by remember(editorId) { mutableStateOf(initial.modelId) }
    var temperature by remember(editorId) { mutableStateOf(initial.temperature) }
    var sendTemperature by remember(editorId) { mutableStateOf(initial.sendTemperature) }
    var advancedExpanded by remember(editorId) { mutableStateOf(false) }
    var modelQuery by remember(editorId) { mutableStateOf("") }
    var models by remember(editorId) { mutableStateOf<List<String>?>(null) }
    var modelDialog by remember(editorId) { mutableStateOf(false) }
    var loadingModels by remember(editorId) { mutableStateOf(false) }
    var modelError by remember(editorId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val modelSource = baseUrl to newApiKey
    LaunchedEffect(modelSource, deleteApiKey, state.globalApiSettings.allowHttp) {
        models = null; modelDialog = false
    }
    val draft = ApiSettings(baseUrl, modelId, temperature, sendTemperature,
        initial.hasApiKey, initial.keyNeedsReentry)
    val valid = profileName.isNotBlank() && (isNew || selectedProfile != null) &&
        (!sendTemperature || temperature.toDoubleOrNull()?.let { it in 0.0..2.0 } == true)
    LaunchedEffect(temperature, sendTemperature) {
        if (sendTemperature && temperature.toDoubleOrNull()?.let { it in 0.0..2.0 } != true) {
            advancedExpanded = true
        }
    }
    val changed = savedSnapshot.hasUnsavedChanges(profileName, draft, newApiKey, deleteApiKey)
    val requestBack = { if (!saving) { if (changed) confirmDiscard = true else onBack() } }
    BackHandler(onBack = requestBack)
    var testNotice by remember(editorId) { mutableStateOf<ApiTestNotice?>(null) }
    var testNoticeId by remember(editorId) { mutableIntStateOf(0) }
    LaunchedEffect(testNotice) {
        val notice = testNotice ?: return@LaunchedEffect
        withTimeoutOrNull(if (notice.success) 2500L else 8000L) {
            snackbarHostState.showSnackbar(uiContext.uiString(notice.message), duration = SnackbarDuration.Indefinite)
        }
    }
    Scaffold(modifier = modifier, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
        Surface(shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (!valid) UiText("请检查配置名称及参数范围。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { model.testConnection(draft, newApiKey, deleteApiKey,
                        selectedProfile?.id) { message, success ->
                        testNotice = ApiTestNotice(++testNoticeId, message, success)
                    } }, enabled = valid && !saving && !state.testingConnection,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        UiText(if (state.testingConnection) "测试中…" else "测试连接")
                    }
                    Button(onClick = {
                        val submittedName = profileName
                        val submittedDraft = draft
                        val submittedKey = newApiKey
                        val submittedDelete = deleteApiKey
                        saving = true
                        model.saveSettings(selectedProfile?.id, submittedName, submittedDraft,
                            submittedKey, submittedDelete) { success ->
                            saving = false
                            if (success) {
                                savedSnapshot = ApiEditorSnapshot(submittedName, submittedDraft.withoutKeyStatus())
                                if (newApiKey == submittedKey) newApiKey = ""
                                if (deleteApiKey == submittedDelete) deleteApiKey = false
                                if (isNew) onSaved()
                            }
                        }
                    }, enabled = valid && !saving && !state.testingConnection,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        UiText(if (saving) "保存中…" else "保存配置")
                    }
                }
            }
        }
    }) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = requestBack, enabled = !saving) { UiText("← 返回配置列表") }
        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiText("基本连接", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(profileName, { profileName = it }, label = { UiText("配置名称") },
            isError = profileName.isBlank(), modifier = Modifier.fillMaxWidth())
        UiText("阅读文章会发送至您配置的第三方 API 服务。测试连接使用下方当前参数，可能产生调用费用。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(baseUrl, { baseUrl = it }, label = { UiText("API Base URL") }, modifier = Modifier.fillMaxWidth())
        if (baseUrl.trim().startsWith("http://", ignoreCase = true) && !state.globalApiSettings.allowHttp)
            UiText("当前全局参数禁止 HTTP BaseURL，请先在全局参数中开启允许 HTTP。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        UiText(when {
            initial.keyNeedsReentry -> "原有 API Key 无法解密，请重新输入。"
            deleteApiKey -> "保存后将删除已配置的 API Key。"
            initial.hasApiKey -> "API Key：已配置。留空可保留现有密钥。"
            else -> "API Key：未配置。"
        })
        OutlinedTextField(newApiKey, { newApiKey = it; if (it.isNotEmpty()) deleteApiKey = false },
            label = { UiText(if (isNew) "API Key（可选）" else "新 API Key（留空保留原值）") },
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showApiKey = !showApiKey }) { UiText(if (showApiKey) "隐藏密钥" else "显示密钥") }
            if (initial.hasApiKey || initial.keyNeedsReentry) {
                TextButton(onClick = { deleteApiKey = !deleteApiKey; if (deleteApiKey) newApiKey = "" }) {
                    UiText(if (deleteApiKey) "取消删除" else "删除现有密钥")
                }
            }
        }
        } }
        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiText("模型配置", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(modelId, { modelId = it }, label = { UiText("Model ID") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            if (baseUrl.isBlank()) { modelError = "请先填写 API Base URL。"; return@OutlinedButton }
            val requestedSource = Triple(baseUrl, newApiKey, deleteApiKey)
            val requestedHttp = state.globalApiSettings.allowHttp
            loadingModels = true
            modelError = null
            scope.launch {
                try {
                    val loaded = model.listModels(draft, newApiKey, deleteApiKey, selectedProfile?.id)
                    if (requestedSource == Triple(baseUrl, newApiKey, deleteApiKey) &&
                        requestedHttp == state.globalApiSettings.allowHttp) {
                        models = loaded; modelQuery = ""; modelDialog = true
                    }
                } catch (error: Exception) {
                    if (requestedSource == Triple(baseUrl, newApiKey, deleteApiKey) &&
                        requestedHttp == state.globalApiSettings.allowHttp)
                        modelError = ApiErrorMapper.message(error)
                } finally { loadingModels = false }
            }
        }, enabled = !loadingModels) { UiText(if (loadingModels) "正在获取模型…" else "获取在线模型列表") }
        modelError?.let { UiText(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
            UiText(if (advancedExpanded) "高级模型参数  ⌃" else "高级模型参数  ⌄")
        }
        if (advancedExpanded) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            UiText("发送 Temperature 参数")
            Switch(checked = sendTemperature, onCheckedChange = { sendTemperature = it })
        }
        OutlinedTextField(temperature, { temperature = it }, label = { UiText("Temperature") },
            enabled = sendTemperature, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth())
        }
        } }
        state.settingsMessage?.let { UiText(it) }
    }
    }
    if (modelDialog) AlertDialog(onDismissRequest = { modelDialog = false },
        title = { UiText("选择模型") },
        text = { Column {
            OutlinedTextField(modelQuery, { modelQuery = it }, label = { UiText("搜索 Model ID") },
                modifier = Modifier.fillMaxWidth())
            val visible = models.orEmpty().filter { it.contains(modelQuery.trim(), ignoreCase = true) }
            if (visible.isEmpty()) UiText("没有可选模型。", modifier = Modifier.padding(12.dp))
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(visible) { id ->
                    ListItem(headlineContent = { androidx.compose.material3.Text(id) },
                        trailingContent = { if (id == modelId) UiText("当前") },
                        modifier = Modifier.clickable { modelId = id; modelDialog = false })
                }
            }
        } }, confirmButton = { TextButton(onClick = { modelDialog = false }) { UiText("取消") } })
    if (confirmDiscard) ConfirmModeActionDialog("放弃未保存的 API 修改？",
        "当前编辑内容尚未保存。",
        onConfirm = { confirmDiscard = false; onBack() },
        onDismiss = { confirmDiscard = false })
}
