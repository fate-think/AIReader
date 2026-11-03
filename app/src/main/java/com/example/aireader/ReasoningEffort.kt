package com.example.aireader

enum class ReasoningEffort(val apiValue: String, val displayName: String) {
    MAX("max", "Max（最大）"),
    XHIGH("xhigh", "XHigh（超高）"),
    HIGH("high", "High（高）"),
    MEDIUM("medium", "Medium（中等）"),
    LOW("low", "Low（低）"),
    MINIMAL("minimal", "Minimal（极低）"),
    NONE("none", "None（无）");

    companion object {
        fun fromApiValue(value: String?): ReasoningEffort =
            entries.firstOrNull { it.apiValue == value } ?: MAX
    }
}

data class ReasoningSettings(
    val sendReasoningEffort: Boolean = false,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MAX
)
