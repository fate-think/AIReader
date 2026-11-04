package com.example.aireader

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class HistorySettingsStoreTest {
    @Test fun defaultsPersistAndDoNotAffectOtherSettings() = runBlocking {
        val file = File.createTempFile("aireader-history-settings", ".preferences_pb")
        file.delete()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val history = HistorySettingsStore(store)
            val preprocess = PreprocessSettingsStore(store)
            assertEquals(HistorySaveOptions(), history.options.first())
            val oldPreprocess = preprocess.options.first()
            history.save(HistorySaveOptions(false, false))
            assertEquals(HistorySaveOptions(false, false), HistorySettingsStore(store).options.first())
            assertEquals(oldPreprocess, preprocess.options.first())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
