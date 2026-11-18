package me.aireader.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
private fun GlobalSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit,
                         enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UiText(label, modifier = Modifier.weight(1f).padding(top = 16.dp),
            color = if (enabled) androidx.compose.ui.graphics.Color.Unspecified
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun GlobalApiSettingsForm(state: ReaderState, model: ReaderViewModel, modifier: Modifier = Modifier) {
    val initial = state.globalApiSettings
    var allowHttp by remember(initial) { mutableStateOf(initial.allowHttp) }
    var sendMaxTokens by remember(initial) { mutableStateOf(initial.sendMaxTokens) }
    var maxTokens by remember(initial) { mutableStateOf(initial.maxOutputTokens.toString()) }
    var streaming by remember(initial) { mutableStateOf(initial.streaming) }
    var sendReasoning by remember(initial) { mutableStateOf(initial.reasoning.sendReasoningEffort) }
    var reasoning by remember(initial) { mutableStateOf(initial.reasoning.reasoningEffort) }
    var reasoningExpanded by remember { mutableStateOf(false) }
    var autoRetry by remember(initial) { mutableStateOf(initial.network.autoRetry) }
    var retry504 by remember(initial) { mutableStateOf(initial.network.retryHttp504) }
    var maxRetries by remember(initial) { mutableStateOf(initial.network.maxRetries.toString()) }
    var connectTimeout by remember(initial) { mutableStateOf(initial.network.connectTimeoutSeconds.toString()) }
    var readTimeout by remember(initial) { mutableStateOf(initial.network.readTimeoutSeconds.toString()) }
    var saving by remember { mutableStateOf(false) }
    val tokenCount = maxTokens.toIntOrNull()?.takeIf { it > 0 }
    val retryCount = maxRetries.toIntOrNull()?.takeIf { it in 0..5 }
    val connect = connectTimeout.toIntOrNull()?.takeIf { it in 10..60 }
    val read = readTimeout.toIntOrNull()?.takeIf { it in 30..600 }
    val valid = tokenCount != null && retryCount != null && connect != null && read != null
    val changed = allowHttp != initial.allowHttp || sendMaxTokens != initial.sendMaxTokens ||
        maxTokens != initial.maxOutputTokens.toString() || streaming != initial.streaming ||
        sendReasoning != initial.reasoning.sendReasoningEffort ||
        reasoning != initial.reasoning.reasoningEffort || autoRetry != initial.network.autoRetry ||
        retry504 != initial.network.retryHttp504 || maxRetries != initial.network.maxRetries.toString() ||
        connectTimeout != initial.network.connectTimeoutSeconds.toString() ||
        readTimeout != initial.network.readTimeoutSeconds.toString()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiText("如无特殊需求，以下参数默认即可。", style = MaterialTheme.typography.bodyMedium)
        GlobalSwitch("允许 HTTP BaseURL", allowHttp, { allowHttp = it })
        if (allowHttp) UiText("HTTP 会以明文传输 API Key 和文章内容，请仅在信任该连接时使用。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        GlobalSwitch("发送 max_tokens 参数", sendMaxTokens, { sendMaxTokens = it })
        OutlinedTextField(maxTokens, { maxTokens = it }, label = { UiText("Max Output Tokens") },
            enabled = sendMaxTokens, isError = tokenCount == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth())
        GlobalSwitch("流式输出", streaming, { streaming = it })
        HorizontalDivider()
        UiText("思考深度", style = MaterialTheme.typography.titleMedium)
        GlobalSwitch("发送思考深度参数", sendReasoning, { sendReasoning = it })
        UiText("开启后，向 API 请求添加 reasoning_effort。关闭后不发送该参数，由模型服务端决定默认行为。",
            style = MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(onClick = { reasoningExpanded = true }, enabled = sendReasoning,
                modifier = Modifier.fillMaxWidth()) { UiText("思考深度：${reasoning.displayName}") }
            DropdownMenu(expanded = reasoningExpanded && sendReasoning,
                onDismissRequest = { reasoningExpanded = false }) {
                ReasoningEffort.entries.forEach { effort ->
                    DropdownMenuItem(text = { UiText(effort.displayName) }, onClick = {
                        reasoning = effort; reasoningExpanded = false
                    })
                }
            }
        }
        HorizontalDivider()
        UiText("网络稳定性", style = MaterialTheme.typography.titleMedium)
        GlobalSwitch("自动重试", autoRetry, { autoRetry = it })
        UiText("仅针对符合条件的临时错误自动重试，可能产生额外 API 调用费用。",
            style = MaterialTheme.typography.bodySmall)
        GlobalSwitch("允许重试 504", retry504, { retry504 = it }, enabled = autoRetry)
        UiText("504 网关超时不代表上游未执行；开启后可能重复请求和计费。总自动重试关闭时此项不生效。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(maxRetries, { maxRetries = it }, label = { UiText("最大重试次数（0–5）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = retryCount == null, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(connectTimeout, { connectTimeout = it },
            label = { UiText("连接超时（10–60 秒）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = connect == null, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(readTimeout, { readTimeout = it },
            label = { UiText("读取超时（30–600 秒）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = read == null, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            saving = true
            model.saveGlobalApiSettings(GlobalApiSettings(allowHttp, sendMaxTokens, tokenCount!!,
                streaming, ReasoningSettings(sendReasoning, reasoning),
                NetworkStabilitySettings(autoRetry, retryCount!!, connect!!, read!!, retry504))) {
                saving = false
            }
        }, enabled = valid && changed && !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            UiText(if (saving) "保存中…" else "保存全局参数")
        }
    }
}
