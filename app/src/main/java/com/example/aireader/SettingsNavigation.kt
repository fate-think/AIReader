package com.example.aireader

enum class SettingsDestination(val title: String) {
    HOME("设置"),
    API_LIST("大模型 API 配置"),
    API_EDIT("编辑 API 配置"),
    APPEARANCE("夜间模式"),
    HISTORY_DATA("阅读历史与数据管理"),
    HISTORY_LIST("阅读历史"),
    HISTORY_DETAIL("历史报告"),
    ABOUT("关于");

    fun parent(): SettingsDestination? = when (this) {
        HOME -> null
        API_LIST, APPEARANCE, HISTORY_DATA, ABOUT -> HOME
        API_EDIT -> API_LIST
        HISTORY_LIST -> HISTORY_DATA
        HISTORY_DETAIL -> HISTORY_LIST
    }
}

val settingsHomeDestinations = listOf(
    SettingsDestination.API_LIST,
    SettingsDestination.APPEARANCE,
    SettingsDestination.HISTORY_DATA,
    SettingsDestination.ABOUT
)
