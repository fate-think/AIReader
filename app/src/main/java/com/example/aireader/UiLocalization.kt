package com.example.aireader

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/** The generated map keeps every resource visible to Release resource shrinking. */
fun Context.uiString(source: String): String {
    uiDynamicString(source)?.let { return it }
    val id = uiStringId(source) ?: return source
    return getString(id)
}

private fun Context.uiDynamicString(source: String): String? {
    fun match(pattern: String): List<String>? = Regex(pattern).matchEntire(source)?.groupValues?.drop(1)
    match("(\\d+) 字")?.let { return getString(R.string.ui_dyn_char_count, it[0]) }
    match("HTML 阅读字号：(\\d+)px")?.let { return getString(R.string.ui_dyn_font_size, it[0]) }
    match("处理前：(\\d+) 字符 · 处理后：(\\d+) 字符")?.let {
        return getString(R.string.ui_dyn_preprocess_count, it[0], it[1])
    }
    match("将从 APK 内置文件恢复“(.+)”的提示词正文。")?.let {
        return getString(R.string.ui_dyn_restore_prompt, it[0])
    }
    match("将删除“(.+)”。此操作不可撤销。")?.let {
        return getString(R.string.ui_dyn_delete_prompt, it[0])
    }
    match("提示词数据读取失败：(.+)")?.let {
        return getString(R.string.ui_dyn_prompt_error, uiString(it[0]))
    }
    match("将删除“(.+)”及其独立保存的密钥。")?.let {
        return getString(R.string.ui_dyn_delete_profile, it[0])
    }
    match("版本：(.+)")?.let { return getString(R.string.ui_dyn_version, it[0]) }
    match("API 配置：(.+)")?.let { return getString(R.string.ui_dyn_api_profile, it[0]) }
    match("删除选中的 (\\d+) 条历史？")?.let {
        return getString(R.string.ui_dyn_delete_selected_confirm, it[0])
    }
    match("删除选中（(\\d+)）")?.let {
        return getString(R.string.ui_dyn_delete_selected, it[0])
    }
    match("服务器繁忙，约 (\\d+) 秒后重试（第 (\\d+)/(\\d+) 次）。")?.let {
        return getString(R.string.ui_dyn_retry_wait, it[0], it[1], it[2])
    }
    match("正在重试（第 (\\d+)/(\\d+) 次）…")?.let {
        return getString(R.string.ui_dyn_retrying, it[0], it[1])
    }
    match("测试失败：(.+)")?.let { return getString(R.string.ui_dyn_test_failed, uiString(it[0])) }
    match("思考深度：(.+)")?.let { return getString(R.string.ui_dyn_reasoning, uiString(it[0])) }
    match("当前 · (.+)")?.let { return getString(R.string.ui_dyn_current, it[0]) }
    match("API 服务暂时不可用（HTTP (\\d+)），请稍后重试。")?.let {
        return getString(R.string.ui_dyn_http_unavailable, it[0])
    }
    match("API 请求失败（HTTP (\\d+)），请检查配置或服务状态。")?.let {
        return getString(R.string.ui_dyn_http_error, it[0])
    }
    return null
}

@Composable
fun UiText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
           style: TextStyle = LocalTextStyle.current, textAlign: TextAlign? = null,
           maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip) {
    val localized = LocalContext.current.uiString(text)
    androidx.compose.material3.Text(localized, modifier = modifier, color = color, style = style,
        textAlign = textAlign, maxLines = maxLines, overflow = overflow)
}
