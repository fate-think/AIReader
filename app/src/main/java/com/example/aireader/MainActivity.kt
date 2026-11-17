package com.example.aireader

import android.content.Intent
import android.content.ContextWrapper
import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(
            android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        setContent {
            val model: ReaderViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            val systemConfiguration = LocalConfiguration.current
            val activeLanguage = state.languageMode ?: LanguageMode.systemFallback(
                systemConfiguration.locales[0])
            val localizedContext = remember(activeLanguage, systemConfiguration) {
                val languageResources = createConfigurationContext(Configuration(systemConfiguration).apply {
                    setLocale(activeLanguage.locale)
                }).resources
                // Keep the Activity in the ContextWrapper chain so Activity Result launchers
                // can resolve their owner while UI resources use the selected language.
                object : ContextWrapper(this@MainActivity) {
                    override fun getResources() = languageResources
                    override fun getAssets() = languageResources.assets
                }
            }
            val useDark = state.themeMode.usesDark(isSystemInDarkTheme())
            LaunchedEffect(useDark) {
                val style = if (useDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CompositionLocalProvider(LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration) {
                MaterialTheme(colorScheme = if (useDark) darkColorScheme() else lightColorScheme()) {
                    App(state, model, useDark)
                }
            }
        }
    }
}

@Composable
private fun App(state: ReaderState, model: ReaderViewModel, useDark: Boolean) {
    val snackbarHostState = remember { SnackbarHostState() }
    val uiContext = LocalContext.current
    LaunchedEffect(model, uiContext) {
        model.apiNotices.collect { message ->
            withTimeoutOrNull(2500L) {
                snackbarHostState.showSnackbar(uiContext.uiString(message), duration = SnackbarDuration.Indefinite)
            }
        }
    }
    val historyModel: ReadingHistoryViewModel = viewModel()
    val historyState by historyModel.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(BottomTab.READING.index) }
    var settingsDestination by rememberSaveable { mutableStateOf(SettingsDestination.HOME) }
    var apiEditorId by rememberSaveable { mutableStateOf<String?>(null) }
    var apiCreating by rememberSaveable { mutableStateOf(false) }
    var historyId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var previewOpen by rememberSaveable { mutableStateOf(false) }
    var htmlPreview by remember { mutableStateOf<String?>(null) }
    var historyPreviewMarkdown by remember { mutableStateOf<String?>(null) }
    var historyPreviewHtml by remember { mutableStateOf<String?>(null) }
    val previewTheme = if (useDark) HtmlDocumentTheme.DARK else HtmlDocumentTheme.LIGHT
    LaunchedEffect(page, settingsDestination, historyId) {
        if (historyId != null && (page == BottomTab.HISTORY.index ||
                page == BottomTab.SETTINGS.index && settingsDestination == SettingsDestination.HISTORY_DETAIL)) {
            historyModel.open(historyId!!)
        }
        else historyModel.closeDetail()
    }
    val goBackSettings: () -> Unit = {
        settingsDestination = settingsDestination.parent() ?: SettingsDestination.HOME
        if (settingsDestination != SettingsDestination.HISTORY_DETAIL) historyId = null
        if (settingsDestination != SettingsDestination.API_EDIT) apiEditorId = null
        if (settingsDestination != SettingsDestination.API_EDIT) apiCreating = false
    }
    val editorReady = apiCreating || state.selectedApiProfileId == apiEditorId && apiEditorId != null
    BackHandler(enabled = page == BottomTab.HISTORY.index && historyId != null &&
        historyPreviewMarkdown == null) { historyId = null }
    BackHandler(enabled = page == BottomTab.SETTINGS.index && settingsDestination != SettingsDestination.HOME &&
        (settingsDestination != SettingsDestination.API_EDIT || !editorReady) &&
        historyPreviewMarkdown == null) {
        goBackSettings()
    }
    LaunchedEffect(historyPreviewMarkdown, previewTheme, state.htmlFontSizePx) {
        val markdown = historyPreviewMarkdown
        if (markdown != null) {
            historyPreviewHtml = null
            try { historyPreviewHtml = withContext(Dispatchers.Default) {
                MarkdownHtmlConverter().convert(markdown, state.htmlFontSizePx, previewTheme)
            } } catch (_: Exception) {
                historyPreviewMarkdown = null
                historyModel.reportFailure("HTML 预览生成失败。")
            }
        }
    }
    if (historyPreviewMarkdown != null) {
        val html = historyPreviewHtml
        if (html == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        } else HtmlPreview(html, state.htmlFontSizePx,
            onFontSizeChange = model::setHtmlFontSize,
            onClose = { historyPreviewMarkdown = null; historyPreviewHtml = null })
        return
    }
    LaunchedEffect(previewTheme, state.htmlFontSizePx, previewOpen, state.loaded) {
        if (previewOpen) {
            if (state.loaded && state.result.isEmpty()) {
                previewOpen = false
                return@LaunchedEffect
            }
            try { model.htmlForCurrentResult(state.htmlFontSizePx, previewTheme)?.let { htmlPreview = it } }
            catch (_: Exception) { model.reportHtmlFailure("HTML 预览刷新失败。") }
        }
    }
    if (previewOpen) {
        val html = htmlPreview
        if (html == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            HtmlPreview(html, state.htmlFontSizePx, onFontSizeChange = { size ->
                model.setHtmlFontSize(size)
            }, onClose = { previewOpen = false; htmlPreview = null })
        }
        return
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Row(modifier = Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (page == BottomTab.SETTINGS.index && settingsDestination != SettingsDestination.HOME &&
                    (settingsDestination != SettingsDestination.API_EDIT || !editorReady)) {
                    TextButton(onClick = goBackSettings) { UiText("‹ 返回") }
                }
                UiText(when { creating || editingId != null -> "编辑阅读模式";
                    page == BottomTab.READING.index -> "AI 阅读助手";
                    page == BottomTab.PROMPTS.index -> "提示词管理";
                    page == BottomTab.HISTORY.index -> if (historyId == null) "阅读历史" else "历史报告";
                    else -> if (settingsDestination == SettingsDestination.API_EDIT && apiCreating)
                        "新增 API 配置" else settingsDestination.title }, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } },
        bottomBar = { if (!creating && editingId == null &&
            !(page == BottomTab.SETTINGS.index && settingsDestination == SettingsDestination.API_EDIT)) NavigationBar {
            BottomTab.entries.forEach { tab ->
                NavigationBarItem(selected = page == tab.index, onClick = {
                    page = tab.index
                    settingsDestination = SettingsDestination.HOME
                    historyId = null
                    apiEditorId = null
                }, icon = {
                    val iconColor = LocalContentColor.current
                    val iconRes = when (tab) {
                        BottomTab.READING -> R.drawable.ic_reading
                        BottomTab.PROMPTS -> R.drawable.ic_prompt_nav
                        BottomTab.HISTORY -> R.drawable.ic_history_nav
                        BottomTab.SETTINGS -> R.drawable.ic_settings_nav
                    }
                    Icon(painterResource(iconRes), contentDescription = null, tint = iconColor)
                }, label = { UiText(tab.title, maxLines = 1,
                    overflow = TextOverflow.Ellipsis) }, alwaysShowLabel = true)
            }
        } }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        val nestedModifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        when {
            !state.loaded && page != BottomTab.READING.index -> Box(modifier) { CircularProgressIndicator() }
            page == BottomTab.HISTORY.index && historyId != null -> ReadingHistoryDetailScreen(
                historyState, historyModel, onBack = { historyId = null },
                onPreviewHtml = { historyPreviewMarkdown = it },
                fontSizePx = state.htmlFontSizePx, modifier = Modifier.fillMaxSize().padding(padding))
            page == BottomTab.HISTORY.index -> ReadingHistoryListScreen(historyState, historyModel,
                onOpen = { historyId = it }, modifier = nestedModifier)
            page == BottomTab.SETTINGS.index -> when (settingsDestination) {
                SettingsDestination.HOME -> SettingsHomeScreen(
                    onOpen = { settingsDestination = it }, modifier = nestedModifier)
                SettingsDestination.API_LIST -> ApiSettingsList(state, model,
                    onEdit = { id -> apiEditorId = id; settingsDestination = SettingsDestination.API_EDIT },
                    onAdd = { apiCreating = true; apiEditorId = null; settingsDestination = SettingsDestination.API_EDIT },
                    modifier = Modifier.fillMaxSize().padding(padding))
                SettingsDestination.API_EDIT -> if (editorReady) ApiSettingsEditor(state, model,
                    isNew = apiCreating, onBack = goBackSettings, onSaved = goBackSettings,
                    snackbarHostState = snackbarHostState, modifier = Modifier.fillMaxSize().padding(padding))
                    else Box(nestedModifier) {
                        if (state.apiProfilesError != null || state.settingsMessage?.startsWith("无法切换") == true)
                            UiText(state.apiProfilesError ?: state.settingsMessage.orEmpty())
                        else CircularProgressIndicator()
                    }
                SettingsDestination.APPEARANCE -> AppearanceSettingsScreen(state, model, modifier)
                SettingsDestination.LANGUAGE -> LanguageSettingsScreen(state, model, modifier)
                SettingsDestination.HISTORY_DATA -> HistoryDataSettingsScreen(state, model,
                    historyModel, historyState,
                    onOpenHistory = { settingsDestination = SettingsDestination.HISTORY_LIST },
                    modifier = modifier)
                SettingsDestination.HISTORY_LIST -> ReadingHistoryListScreen(historyState, historyModel,
                    onOpen = { historyId = it; settingsDestination = SettingsDestination.HISTORY_DETAIL },
                    modifier = nestedModifier)
                SettingsDestination.HISTORY_DETAIL -> ReadingHistoryDetailScreen(historyState, historyModel,
                    onBack = goBackSettings, onPreviewHtml = { historyPreviewMarkdown = it },
                    fontSizePx = state.htmlFontSizePx, modifier = Modifier.fillMaxSize().padding(padding))
                SettingsDestination.ABOUT -> AboutScreen(modifier)
            }
            state.modes.isEmpty() && page != BottomTab.READING.index ->
                UiText("提示词数据读取失败：${state.storageError ?: "未知错误"}", modifier = modifier)
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
            page == BottomTab.READING.index -> ReaderPage(state, model, previewTheme,
                onHtmlPreview = { htmlPreview = it; previewOpen = true }, modifier = modifier)
            page == BottomTab.PROMPTS.index -> ModesPage(state, model, onEdit = { editingId = it },
                onAdd = { creating = true }, modifier = modifier)
            else -> Unit
        }
    }
    if (state.modes.isNotEmpty() && state.storageError != null) {
        AlertDialog(onDismissRequest = model::clearStorageError,
            title = { UiText("提示词保存失败") }, text = { UiText(state.storageError.orEmpty()) },
            confirmButton = { TextButton(onClick = model::clearStorageError) { UiText("知道了") } })
    }
}

@Composable
private fun ReaderPage(state: ReaderState, model: ReaderViewModel,
                       previewTheme: HtmlDocumentTheme,
                       onHtmlPreview: (String) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var apiExpanded by remember { mutableStateOf(false) }
    var showPreprocessSettings by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val resultScroll = rememberLazyListState()
    val thinkingScrollBoundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset,
                                      source: NestedScrollSource): Offset = Offset(0f, available.y)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(0f, available.y)
        }
    }
    val userDraggingResult by resultScroll.interactionSource.collectIsDraggedAsState()
    var followResult by remember { mutableStateOf(true) }
    var confirmClearResult by remember { mutableStateOf(false) }
    var confirmReplaceMarkdown by remember { mutableStateOf(false) }
    var moreActions by remember { mutableStateOf(false) }
    var htmlBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) {
        model.completeMarkdownExport(it)
    }
    val htmlExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) {
        model.completeHtmlExport(it)
    }
    val active = state.status in setOf(ReadingStatus.Loading, ReadingStatus.WaitingResponse,
        ReadingStatus.Thinking, ReadingStatus.WaitingForRetry, ReadingStatus.Retrying, ReadingStatus.Streaming)
    val htmlReady = MarkdownWorkspace.canUseHtml(state.result, active)
    fun requestReading() {
        if (MarkdownWorkspace.requiresOverwriteConfirmation(state.result, state.markdownSource)) {
            confirmReplaceMarkdown = true
        } else {
            keyboard?.hide()
            model.startReading()
        }
    }
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
    var finalPreview by remember { mutableStateOf<FinalRequestPreview?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val currentApi = state.apiProfiles.firstOrNull { it.id == state.selectedApiProfileId }
        UiText("阅读配置", style = MaterialTheme.typography.titleMedium)
        androidx.compose.material3.Text("${currentApi?.name ?: context.uiString("未载入")} · ${state.settings.modelId.ifBlank { context.uiString("未配置模型") }}",
            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Box {
            TextButton(onClick = { apiExpanded = true }, enabled = state.apiProfiles.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    UiText("切换 API 配置")
                    ExpandCollapseArrow(apiExpanded)
                }
            }
            DropdownMenu(expanded = apiExpanded, onDismissRequest = { apiExpanded = false }) {
                state.apiProfiles.forEach { profile ->
                    DropdownMenuItem(text = { androidx.compose.material3.Text("${profile.name} · ${profile.config.modelId.ifBlank { context.uiString("未配置模型") }}") },
                        onClick = { model.selectApiProfile(profile.id); apiExpanded = false })
                }
            }
        }
        if (state.loaded && state.modes.isEmpty()) UiText("提示词数据读取失败：${state.storageError ?: "未知错误"}",
            color = MaterialTheme.colorScheme.error)
        Box {
            TextButton(onClick = { expanded = true }) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Text(
                        state.modes.firstOrNull { it.id == state.selectedModeId }?.name
                            ?: context.uiString("选择阅读模式"),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ExpandCollapseArrow(expanded)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                UiText("内置阅读模式", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium)
                state.modes.filter { it.builtIn }.forEach { mode -> DropdownMenuItem(text = { androidx.compose.material3.Text(mode.name) },
                    onClick = { model.selectMode(mode.id); expanded = false }) }
                HorizontalDivider()
                UiText("我的自定义提示词", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium)
                state.modes.filterNot { it.builtIn }.forEach { mode -> DropdownMenuItem(text = { androidx.compose.material3.Text(mode.name) },
                    onClick = { model.selectMode(mode.id); expanded = false }) }
            }
        }
        }
        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            UiText("文章内容", style = MaterialTheme.typography.titleMedium)
            UiText("${state.article.length} 字", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(value = state.article, onValueChange = model::setArticle,
            placeholder = { UiText("在这里粘贴文章") },
            maxLines = 10,
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 280.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = model::clearArticle, enabled = state.article.isNotEmpty()) { UiText("清空文章") }
        }
        OutlinedButton(onClick = { showPreprocessSettings = true }, modifier = Modifier.fillMaxWidth()) {
            UiText("文章预处理设置")
        }
        OutlinedButton(onClick = { preview = model.processedArticle() },
            enabled = state.article.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            UiText("预览处理结果")
        }
        OutlinedButton(onClick = { finalPreview = model.finalRequestPreview() },
            enabled = state.article.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            UiText("预览最终发送内容")
        }
        Button(onClick = { if (active) model.stopReading() else requestReading() },
            enabled = active || state.article.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            UiText(if (state.status == ReadingStatus.WaitingForRetry) "取消重试" else if (active) "停止生成" else "开始阅读")
        }
        if (!active && (state.status != ReadingStatus.Idle || state.result.isNotEmpty())) {
            OutlinedButton(onClick = { requestReading() }, enabled = state.article.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { UiText("重新生成") }
        }
        } }
        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiText("AI 阅读结果", style = MaterialTheme.typography.titleMedium)
        UiText(when (state.status) {
            ReadingStatus.Idle -> when (state.markdownSource) {
                MarkdownSource.MANUAL -> "手动 Markdown"
                MarkdownSource.EDITED_AI -> "已手动修改"
                else -> "等待开始"
            }
            ReadingStatus.Loading -> "正在连接…"
            ReadingStatus.WaitingResponse -> "等待模型响应…"
            ReadingStatus.Thinking -> "正在思考…"
            ReadingStatus.WaitingForRetry -> state.retryMessage ?: "等待重试…"
            ReadingStatus.Retrying -> state.retryMessage ?: "正在重试…"
            ReadingStatus.Streaming -> "正在生成…"
            ReadingStatus.Completed -> "生成完成"
            ReadingStatus.Cancelled -> "已停止生成"
            ReadingStatus.Interrupted -> "生成中断"
            ReadingStatus.RetryExhausted -> "重试次数已用尽"
            ReadingStatus.Error -> "生成失败"
        })
        if (active) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.readingError?.let { UiText(it, color = MaterialTheme.colorScheme.error) }
        state.historySaveMessage?.let { UiText(it, color = MaterialTheme.colorScheme.error) }
        if (state.thinking.isNotEmpty()) {
            var thinkingExpanded by remember { mutableStateOf(false) }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    TextButton(onClick = { thinkingExpanded = !thinkingExpanded }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            UiText("思考过程")
                            ExpandCollapseArrow(thinkingExpanded)
                        }
                    }
                    if (thinkingExpanded) SelectionContainer {
                        androidx.compose.material3.Text(state.thinking, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                            .nestedScroll(thinkingScrollBoundary)
                            .verticalScroll(rememberScrollState()))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.markdownViewMode == MarkdownViewMode.EDIT,
                onClick = { model.setMarkdownViewMode(MarkdownViewMode.EDIT) },
                enabled = !active, shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).fillMaxHeight(),
                label = { UiText("编辑 Markdown", textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()) })
            FilterChip(selected = state.markdownViewMode == MarkdownViewMode.PREVIEW,
                onClick = { model.setMarkdownViewMode(MarkdownViewMode.PREVIEW) },
                enabled = !active, shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).fillMaxHeight(),
                label = { UiText("预览 Markdown", textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()) })
        }
        if (!active && state.markdownViewMode == MarkdownViewMode.EDIT) {
            OutlinedTextField(value = state.result, onValueChange = model::setMarkdown,
                placeholder = { UiText("可粘贴其他 AI 软件生成的 Markdown，也可编辑 AIReader 生成的阅读报告。") },
                modifier = Modifier.fillMaxWidth().height(360.dp))
        } else if (state.result.isEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth().height(360.dp)) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    UiText(if (active) "等待模型输出…" else "暂无 Markdown 内容。")
                }
            }
        } else {
            if (active && !followResult) {
                TextButton(onClick = { followResult = true }) { UiText("回到底部") }
            }
            OutlinedCard(Modifier.fillMaxWidth().height(360.dp)) {
                MarkdownResult(state.result, resultScroll, streaming = active)
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(state.result)) },
                enabled = state.result.isNotEmpty(), shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).fillMaxHeight()) {
                UiText("复制 Markdown", textAlign = TextAlign.Center)
            }
            Box(Modifier.weight(1f).heightIn(min = 56.dp).fillMaxHeight()) {
                OutlinedButton(onClick = { moreActions = true }, shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxSize()) {
                    UiText("更多操作", textAlign = TextAlign.Center)
                }
                DropdownMenu(expanded = moreActions, onDismissRequest = { moreActions = false },
                    modifier = Modifier.widthIn(min = 220.dp).clip(RoundedCornerShape(16.dp))) {
                    DropdownMenuItem(text = { UiText("HTML 预览") }, enabled = htmlReady && !htmlBusy,
                        modifier = Modifier.heightIn(min = 52.dp),
                        onClick = {
                            moreActions = false; htmlBusy = true
                            scope.launch {
                                try { model.htmlForCurrentResult(theme = previewTheme)?.let(onHtmlPreview) }
                                catch (_: Exception) { model.reportHtmlFailure("HTML 预览生成失败。") }
                                finally { htmlBusy = false }
                            }
                        })
                    DropdownMenuItem(text = { UiText("浏览器打开") }, enabled = htmlReady && !htmlBusy,
                        modifier = Modifier.heightIn(min = 52.dp),
                        onClick = {
                            moreActions = false; htmlBusy = true
                            scope.launch {
                                try {
                                    model.browserHtmlUri()?.let { uri ->
                                        try { HtmlBrowser.open(context, uri) }
                                        catch (_: Exception) {
                                            model.reportHtmlFailure("浏览器可能不支持直接打开此文件。可使用 HTML 预览，或保存 HTML 后用支持本地文件的浏览器打开。")
                                        }
                                    }
                                } finally { htmlBusy = false }
                            }
                        })
                    DropdownMenuItem(text = { UiText("保存 HTML") }, enabled = htmlReady && !htmlBusy,
                        modifier = Modifier.heightIn(min = 52.dp),
                        onClick = {
                            moreActions = false; htmlBusy = true
                            scope.launch {
                                try {
                                    if (model.prepareHtmlExport()) htmlExportLauncher.launch("AIReader-阅读报告.html")
                                } finally { htmlBusy = false }
                            }
                        })
                    HorizontalDivider()
                    DropdownMenuItem(text = { UiText("分享结果") }, enabled = state.result.isNotEmpty(),
                        modifier = Modifier.heightIn(min = 52.dp), onClick = {
                        moreActions = false
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, state.result)
                        }
                        try { context.startActivity(Intent.createChooser(send, context.uiString("分享阅读结果"))) }
                        catch (_: Exception) { model.reportShareFailure() }
                    })
                    DropdownMenuItem(text = { UiText("保存 Markdown") }, enabled = state.result.isNotEmpty(),
                        modifier = Modifier.heightIn(min = 52.dp), onClick = {
                        moreActions = false
                        if (model.prepareMarkdownExport()) exportLauncher.launch("AIReader-report.md")
                    })
                    HorizontalDivider()
                    DropdownMenuItem(text = { UiText("清空 Markdown") }, enabled = !active && state.result.isNotEmpty(),
                        modifier = Modifier.heightIn(min = 52.dp),
                        onClick = { moreActions = false; confirmClearResult = true })
                }
            }
        }
        if (htmlBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        UiText("HTML 阅读字号：${state.htmlFontSizePx}px", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { model.setHtmlFontSize(state.htmlFontSizePx - 1) },
                enabled = state.htmlFontSizePx > 14) { UiText("A−") }
            OutlinedButton(onClick = { model.setHtmlFontSize(state.htmlFontSizePx + 1) },
                enabled = state.htmlFontSizePx < 36) { UiText("A+") }
        }
        state.resultActionMessage?.let { UiText(it, style = MaterialTheme.typography.bodySmall) }
        } }
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
    if (confirmReplaceMarkdown) ConfirmModeActionDialog("替换当前 Markdown？",
        if (state.markdownSource == MarkdownSource.EDITED_AI)
            "当前 Markdown 已被手动修改。重新生成将替换当前内容，是否继续？"
        else "当前 Markdown 是手动输入的内容。新生成结果将替换当前 Markdown，是否继续？",
        onConfirm = { confirmReplaceMarkdown = false; keyboard?.hide(); model.startReading(confirmedOverwrite = true) },
        onDismiss = { confirmReplaceMarkdown = false })
    if (confirmClearResult) ConfirmModeActionDialog("清空 Markdown？", "这会清除当前 Markdown，不影响文章和提示词。",
        onConfirm = { model.clearResult(); confirmClearResult = false },
        onDismiss = { confirmClearResult = false })
}

@Composable
private fun FinalRequestPreviewDialog(request: FinalRequestPreview, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.86f)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                UiText("预览最终发送内容", style = MaterialTheme.typography.titleLarge)
                androidx.compose.material3.Text(request.userMessage,
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { clipboard.setText(AnnotatedString(request.userMessage)) }) { UiText("复制完整内容") }
                    TextButton(onClick = onDismiss) { UiText("关闭") }
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
    AlertDialog(onDismissRequest = onDismiss, title = { UiText("文章预处理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    UiText("启用预处理")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(value = paraOnce, onValueChange = { paraOnce = it },
                    label = { UiText("每组自然段数（1–100）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !validCount, modifier = Modifier.fillMaxWidth())
                UiText("例如设为 3，则每 3 个自然段合并为 1 段。",
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    UiText("自动识别标题")
                    Switch(checked = detectTitles, onCheckedChange = { detectTitles = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    UiText("自动识别编号列表")
                    Switch(checked = detectLists, onCheckedChange = { detectLists = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(PreprocessOptions(enabled, count ?: 1, detectTitles, detectLists))
            }, enabled = validCount) { UiText("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { UiText("取消") } })
}

@Composable
private fun PreviewDialog(processed: String, originalLength: Int, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UiText("预览处理结果", style = MaterialTheme.typography.titleLarge)
                UiText("处理前：$originalLength 字符 · 处理后：${processed.length} 字符")
                androidx.compose.material3.Text(processed, modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { clipboard.setText(AnnotatedString(processed)) }) { UiText("复制预处理结果") }
                    TextButton(onClick = onDismiss) { UiText("关闭") }
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
        UiText("内置提示词", style = MaterialTheme.typography.titleLarge)
        state.modes.filter { it.builtIn }.forEach { mode -> ModeCard(mode, onEdit,
            onRestore = { pendingRestore = mode }, onDelete = null) }
        HorizontalDivider()
        UiText("我的自定义提示词", style = MaterialTheme.typography.titleLarge)
        state.modes.filterNot { it.builtIn }.forEach { mode -> ModeCard(mode, onEdit,
            onRestore = null, onDelete = { pendingDelete = mode }) }
        if (state.modes.none { !it.builtIn }) UiText("还没有自定义阅读模式")
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { UiText("+ 新增提示词") }
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
internal fun ConfirmModeActionDialog(title: String, message: String,
                                    onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { UiText(title) }, text = { UiText(message) },
        confirmButton = { TextButton(onClick = onConfirm) { UiText("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { UiText("取消") } })
}

@Composable
private fun ModeCard(mode: ReadingMode, onEdit: (String) -> Unit,
                     onRestore: (() -> Unit)?, onDelete: (() -> Unit)?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            androidx.compose.material3.Text(mode.name, style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(onClick = { onEdit(mode.id) }) { UiText("编辑") }
                if (onRestore != null) TextButton(onClick = onRestore) { UiText("恢复默认") }
                if (onDelete != null) TextButton(onClick = onDelete) { UiText("删除") }
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
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { UiText("模式名称") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { UiText("提示词") },
            modifier = Modifier.fillMaxWidth().height(360.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                saving = true
                scope.launch {
                    val success = onSave(name, prompt)
                    saving = false
                    if (success) onSaved()
                }
            }, enabled = !saving && name.isNotBlank() && prompt.isNotBlank()) { UiText("保存") }
            OutlinedButton(onClick = leave, enabled = !saving) { UiText("取消") }
        }
        if (onRestore != null) OutlinedButton(onClick = { confirmRestore = true }, enabled = !saving) { UiText("恢复默认") }
        if (onDelete != null) TextButton(onClick = { confirmDelete = true }, enabled = !saving) { UiText("删除此提示词") }
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
