package com.example.aireader

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeSettingsStoreTest {
    @Test fun themeModesResolveAgainstSystemAppearance() {
        assertEquals(listOf("system", "light", "dark"), ThemeMode.entries.map { it.storedValue })
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue("corrupt"))
        assertFalse(ThemeMode.SYSTEM.usesDark(false))
        assertTrue(ThemeMode.SYSTEM.usesDark(true))
        assertFalse(ThemeMode.LIGHT.usesDark(false))
        assertFalse(ThemeMode.LIGHT.usesDark(true))
        assertTrue(ThemeMode.DARK.usesDark(false))
        assertTrue(ThemeMode.DARK.usesDark(true))
    }

    @Test fun themePersistsAndLegacyDataGetsSystemDefault() = runBlocking {
        val file = File.createTempFile("aireader-theme-", ".preferences_pb")
        file.delete()
        val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file })
            dataStore.edit { it[stringPreferencesKey("settings")] = "existing-api-settings" }
            val first = ThemeSettingsStore(dataStore)
            assertEquals(ThemeMode.SYSTEM, first.mode.first())
            first.save(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, first.mode.first())
            first.save(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, first.mode.first())
            assertEquals("existing-api-settings", dataStore.data.first()[stringPreferencesKey("settings")])
            firstScope.coroutineContext[Job]!!.cancelAndJoin()

            val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try {
                val reopenedData = PreferenceDataStoreFactory.create(scope = secondScope,
                    produceFile = { file })
                val reopened = ThemeSettingsStore(reopenedData)
                assertEquals(ThemeMode.DARK, reopened.mode.first())
                reopenedData.edit { it[stringPreferencesKey("theme_mode")] = "unknown-value" }
                assertEquals(ThemeMode.SYSTEM, reopened.mode.first())
            } finally { secondScope.coroutineContext[Job]!!.cancelAndJoin() }
        } finally {
            if (firstScope.coroutineContext[Job]!!.isActive) firstScope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
