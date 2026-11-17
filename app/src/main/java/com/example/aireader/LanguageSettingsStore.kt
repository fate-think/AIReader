package com.example.aireader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

enum class LanguageMode(val tag: String, val nativeName: String) {
    ZH_CN("zh-CN", "简体中文"),
    ZH_HK("zh-HK", "繁體中文（香港）"),
    ZH_TW("zh-TW", "繁體中文（台灣）"),
    EN("en", "English");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun fromTag(tag: String?): LanguageMode? = entries.firstOrNull { it.tag == tag }
        fun systemFallback(system: Locale): LanguageMode = when {
            system.language == "en" -> EN
            system.language == "zh" && system.country == "HK" -> ZH_HK
            system.language == "zh" && system.country == "TW" -> ZH_TW
            else -> ZH_CN
        }
    }
}

class LanguageSettingsStore(private val dataStore: DataStore<Preferences>) {
    private val key = stringPreferencesKey("interface_language")
    val language: Flow<LanguageMode?> = dataStore.data.map { LanguageMode.fromTag(it[key]) }

    suspend fun save(mode: LanguageMode) {
        dataStore.edit { it[key] = mode.tag }
    }
}
