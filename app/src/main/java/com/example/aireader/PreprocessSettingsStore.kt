package com.example.aireader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreprocessSettingsStore(private val dataStore: DataStore<Preferences>) {
    private val enabledKey = booleanPreferencesKey("preprocess_enabled")
    private val paraOnceKey = intPreferencesKey("preprocess_para_once")
    private val titlesKey = booleanPreferencesKey("preprocess_detect_titles")
    private val listsKey = booleanPreferencesKey("preprocess_detect_lists")

    val options: Flow<PreprocessOptions> = dataStore.data.map { preferences ->
        PreprocessOptions(
            enabled = preferences[enabledKey] ?: true,
            paraOnce = (preferences[paraOnceKey] ?: 3).coerceIn(1, 100),
            detectTitles = preferences[titlesKey] ?: true,
            detectNumberedLists = preferences[listsKey] ?: true
        )
    }

    suspend fun save(options: PreprocessOptions) {
        dataStore.edit { preferences ->
            preferences[enabledKey] = options.enabled
            preferences[paraOnceKey] = options.paraOnce
            preferences[titlesKey] = options.detectTitles
            preferences[listsKey] = options.detectNumberedLists
        }
    }
}
