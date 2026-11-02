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

class PreprocessSettingsStoreTest {
    @Test fun optionsSurviveReopeningDataStore() = runBlocking {
        val file = File.createTempFile("aireader-options-", ".preferences_pb")
        file.delete()
        val saved = PreprocessOptions(enabled = false, paraOnce = 7,
            detectTitles = false, detectNumberedLists = false)
        val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val firstStore = PreprocessSettingsStore(PreferenceDataStoreFactory.create(
                scope = firstScope, produceFile = { file }))
            firstStore.save(saved)
            firstScope.coroutineContext[Job]!!.cancelAndJoin()

            val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try {
                val reopened = PreprocessSettingsStore(PreferenceDataStoreFactory.create(
                    scope = secondScope, produceFile = { file }))
                assertEquals(saved, reopened.options.first())
            } finally {
                secondScope.coroutineContext[Job]!!.cancelAndJoin()
            }
        } finally {
            if (firstScope.coroutineContext[Job]!!.isActive) {
                firstScope.coroutineContext[Job]!!.cancelAndJoin()
            }
            file.delete()
        }
    }
}
