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

class HtmlSettingsStoreTest {
    @Test fun fontSizePersistsAfterReopening() = runBlocking {
        val file = File.createTempFile("aireader-html-", ".preferences_pb")
        file.delete()
        val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val first = HtmlSettingsStore(PreferenceDataStoreFactory.create(scope = firstScope,
                produceFile = { file }))
            assertEquals(24, first.fontSizePx.first())
            first.saveFontSize(31)
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try {
                val second = HtmlSettingsStore(PreferenceDataStoreFactory.create(scope = secondScope,
                    produceFile = { file }))
                assertEquals(31, second.fontSizePx.first())
            } finally { secondScope.coroutineContext[Job]!!.cancelAndJoin() }
        } finally {
            if (firstScope.coroutineContext[Job]!!.isActive) firstScope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
