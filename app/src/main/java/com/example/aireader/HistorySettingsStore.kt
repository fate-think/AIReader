package com.example.aireader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class HistorySaveOptions(val autoSave: Boolean = true, val saveOriginalArticle: Boolean = true)

class HistorySettingsStore(private val dataStore: DataStore<Preferences>) {
    private val autoSaveKey = booleanPreferencesKey("history_auto_save")
    private val originalKey = booleanPreferencesKey("history_save_original_article")

    val options: Flow<HistorySaveOptions> = dataStore.data.map { preferences ->
        HistorySaveOptions(preferences[autoSaveKey] ?: true, preferences[originalKey] ?: true)
    }

    suspend fun save(options: HistorySaveOptions) {
        dataStore.edit { preferences ->
            preferences[autoSaveKey] = options.autoSave
            preferences[originalKey] = options.saveOriginalArticle
        }
    }
}
