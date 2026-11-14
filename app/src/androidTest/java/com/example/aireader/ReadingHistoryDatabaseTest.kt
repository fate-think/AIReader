package com.example.aireader

import androidx.room.Room
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Device tests: the current APK workflow only runs JVM unit tests, not connectedAndroidTest. */
@RunWith(AndroidJUnit4::class)
class ReadingHistoryDatabaseTest {
    private lateinit var database: ReadingHistoryDatabase
    private lateinit var repository: ReadingHistoryRepository

    @Before fun create() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),
            ReadingHistoryDatabase::class.java).build()
        repository = ReadingHistoryRepository(database.historyDao())
    }

    @After fun close() { database.close() }

    private fun record(id: String, title: String, time: Long, mode: String = "旧模式", report: String = "# 报告") =
        ReadingHistoryEntity(id, title, "原始文章。", report, "removed-mode", mode,
            "old-model", "renamed-api", time, time, null)

    @Test fun successfulStreamingAndNonStreamingCompletionsInsertOnce() = runBlocking {
        val stream = record("stream-request", "流式报告", 10)
        val nonStream = record("normal-request", "非流式报告", 20)
        assertTrue(repository.insertOnce(stream))
        assertFalse(repository.insertOnce(stream))
        assertTrue(repository.insertOnce(nonStream))
        assertFalse(repository.insertOnce(nonStream))
        assertEquals(2, repository.summaries("").first().size)
    }

    @Test fun finalMessageIsLoadedOnlyWithDetail() = runBlocking {
        val message = "完整提示词\n{段落1} 原文。"
        repository.insertOnce(record("snapshot", "快照", 30).copy(finalUserMessage = message))
        assertEquals(message, repository.find("snapshot")?.finalUserMessage)
        assertEquals(listOf("snapshot"), repository.summaries("").first().map { it.id })
    }

    @Test fun migrationFromVersionOnePreservesExistingRecord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "history-migration-${UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name, android.content.Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("""CREATE TABLE IF NOT EXISTS reading_history (
                id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, originalArticle TEXT,
                markdownResult TEXT NOT NULL, readingModeId TEXT NOT NULL,
                readingModeName TEXT NOT NULL, modelId TEXT NOT NULL,
                apiConfigName TEXT NOT NULL, createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL)""")
            old.execSQL("CREATE INDEX IF NOT EXISTS index_reading_history_createdAt ON reading_history (createdAt)")
            old.execSQL("""INSERT INTO reading_history VALUES
                ('old-id', '旧标题', '旧原文', '# 旧报告', 'old-mode', '旧模式',
                'old-model', '旧 API', 100, 100)""")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, ReadingHistoryDatabase::class.java, name)
            .addMigrations(ReadingHistoryDatabase.MIGRATION_1_2).build()
        try {
            val old = migrated.historyDao().find("old-id")!!
            assertEquals("旧标题", old.title)
            assertEquals("旧原文", old.originalArticle)
            assertEquals("# 旧报告", old.markdownResult)
            assertNull(old.finalUserMessage)
            assertEquals(1, migrated.historyDao().summaries("").first().size)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun summaryOrderingSearchAndFullDetailRemainIndependent() = runBlocking {
        val longReport = "长报告内容".repeat(100_000)
        repository.insertOnce(record("old", "旧文章", 100, "模式甲", longReport))
        repository.insertOnce(record("new", "新文章", 200, "模式乙"))
        val rows = repository.summaries("").first()
        assertEquals(listOf("new", "old"), rows.map { it.id })
        assertEquals("旧文章", rows[1].title)
        assertEquals("模式甲", rows[1].readingModeName)
        assertEquals("old-model", rows[1].modelId)
        assertEquals(longReport.take(160), rows[1].preview)
        assertEquals(listOf("old"), repository.summaries("旧文章").first().map { it.id })
        assertEquals(listOf("new"), repository.summaries("模式乙").first().map { it.id })
        assertEquals(longReport, repository.find("old")!!.markdownResult)
        assertEquals("原始文章。", repository.find("old")!!.originalArticle)
    }

    @Test fun renameAndDeletingOneDoNotAffectOtherRecordsOrModeSnapshot() = runBlocking {
        repository.insertOnce(record("a", "标题甲", 10))
        repository.insertOnce(record("b", "标题乙", 20))
        assertTrue(repository.rename("a", "新标题", 30))
        assertEquals("新标题", repository.find("a")!!.title)
        assertEquals(30L, repository.find("a")!!.updatedAt)
        assertTrue(repository.delete("b"))
        assertEquals("旧模式", repository.find("a")!!.readingModeName)
        assertEquals("renamed-api", repository.find("a")!!.apiConfigName)
        assertNull(repository.find("b"))
    }

    @Test fun batchDeleteAndClearOnlyTouchHistoryTable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "history-test-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val settings = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            settings.edit {
                it[stringPreferencesKey("settings")] = "api-stays"
                it[stringPreferencesKey("prompt_catalog_v2")] = "prompt-stays"
            }
            (1..5).forEach { repository.insertOnce(record("$it", "记录$it", it.toLong())) }
            assertEquals(2, repository.deleteMany(setOf("1", "2")))
            assertEquals(3, repository.summaries("").first().size)
            assertEquals(3, repository.clearAll())
            assertTrue(repository.summaries("").first().isEmpty())
            assertEquals("api-stays", settings.data.first()[stringPreferencesKey("settings")])
            assertEquals("prompt-stays", settings.data.first()[stringPreferencesKey("prompt_catalog_v2")])
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
