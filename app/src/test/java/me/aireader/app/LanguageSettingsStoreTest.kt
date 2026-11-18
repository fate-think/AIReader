package me.aireader.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.Locale

class LanguageSettingsStoreTest {
    @Test fun fourOptionsAndSystemFallback() {
        assertEquals(listOf("zh-CN", "zh-HK", "zh-TW", "en"), LanguageMode.entries.map { it.tag })
        assertEquals(LanguageMode.ZH_HK, LanguageMode.systemFallback(Locale.forLanguageTag("zh-HK")))
        assertEquals(LanguageMode.ZH_TW, LanguageMode.systemFallback(Locale.forLanguageTag("zh-TW")))
        assertEquals(LanguageMode.EN, LanguageMode.systemFallback(Locale.ENGLISH))
        assertEquals(LanguageMode.ZH_CN, LanguageMode.systemFallback(Locale.FRENCH))
    }

    @Test fun selectedLanguagePersistsWithoutAVisibleSystemOption() = runBlocking {
        val file = File.createTempFile("aireader-language-", ".preferences_pb")
        file.delete()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = LanguageSettingsStore(PreferenceDataStoreFactory.create(
                scope = scope, produceFile = { file }))
            assertNull(store.language.first())
            store.save(LanguageMode.ZH_TW)
            assertEquals(LanguageMode.ZH_TW, store.language.first())
            store.save(LanguageMode.EN)
            assertEquals(LanguageMode.EN, store.language.first())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
