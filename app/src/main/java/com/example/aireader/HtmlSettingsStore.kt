package com.example.aireader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HtmlSettingsStore(private val dataStore: DataStore<Preferences>) {
    private val fontSizeKey = intPreferencesKey("html_font_size_px")
    val fontSizePx: Flow<Int> = dataStore.data.map { (it[fontSizeKey] ?: 24).coerceIn(14, 36) }

    suspend fun saveFontSize(value: Int) {
        require(value in 14..36)
        dataStore.edit { it[fontSizeKey] = value }
    }
}
