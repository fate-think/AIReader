package com.example.aireader

object ApiKeyNormalizer {
    fun normalize(value: String): String {
        val key = value.trim()
        if (key.isEmpty()) throw ApiException("请先配置 API Key。")
        if ('\n' in key || '\r' in key) {
            throw ApiException("API Key 中间包含换行，请检查后重新粘贴。")
        }
        return key
    }
}
