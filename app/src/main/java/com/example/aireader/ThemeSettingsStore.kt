package com.example.aireader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val storedValue: String, val displayName: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色模式"),
    DARK("dark", "深色模式");

    fun usesDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStoredValue(value: String?): ThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

class ThemeSettingsStore(private val dataStore: DataStore<Preferences>) {
    private val themeKey = stringPreferencesKey("theme_mode")
    val mode: Flow<ThemeMode> = dataStore.data.map { ThemeMode.fromStoredValue(it[themeKey]) }

    suspend fun save(value: ThemeMode) {
        dataStore.edit { it[themeKey] = value.storedValue }
    }
}
