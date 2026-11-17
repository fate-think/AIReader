package com.example.aireader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PromptRepositoryTest {
    private fun assetFile(name: String): File = listOf(
        File("src/main/assets/prompts/$name"),
        File("app/src/main/assets/prompts/$name")
    ).firstOrNull { it.isFile } ?: error("Missing asset: $name")

    private fun repository(store: DataStore<Preferences>) = PromptRepository(store) { name ->
        assetFile(name).readText(Charsets.UTF_8)
    }

    private suspend fun withStore(block: suspend (PromptRepository, DataStore<Preferences>) -> Unit) {
        val file = File.createTempFile("aireader-prompts-", ".preferences_pb")
        file.delete()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            block(repository(store), store)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test fun sevenBuiltInsLoadWithExactAssetContents() = runBlocking { withStore { repo, _ ->
        repo.migrateIfNeeded()
        val catalog = repo.catalogs.first()
        assertEquals(7, catalog.modes.size)
        assertEquals(PromptRepository.builtIns.map { it.first }, catalog.modes.map { it.id })
        PromptRepository.builtIns.forEach { (id, filename) ->
            val mode = catalog.modes.single { it.id == id }
            assertTrue(mode.builtIn)
            assertEquals(filename.removeSuffix(".md"), mode.name)
            assertEquals(assetFile(filename).readText(Charsets.UTF_8), mode.prompt)
        }
    } }

    @Test fun builtInEditAndSelectedModeSurviveReopening() = runBlocking {
        val file = File.createTempFile("aireader-prompt-reopen-", ".preferences_pb")
        file.delete()
        val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val first = repository(PreferenceDataStoreFactory.create(
                scope = firstScope, produceFile = { file }))
            first.migrateIfNeeded()
            first.save("builtin.full_analysis.v2", "全文分析总结V2", "用户修改的完整提示词\n第二行")
            first.save("builtin.info_rescue.v1", "信息抢救", "第五条用户修改")
            first.select("builtin.full_analysis.v2")
            firstScope.coroutineContext[Job]!!.cancelAndJoin()

            val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try {
                val reopened = repository(PreferenceDataStoreFactory.create(
                    scope = secondScope, produceFile = { file }))
                val catalog = reopened.catalogs.first()
                assertEquals("用户修改的完整提示词\n第二行",
                    catalog.modes.single { it.id == "builtin.full_analysis.v2" }.prompt)
                assertEquals("第五条用户修改",
                    catalog.modes.single { it.id == "builtin.info_rescue.v1" }.prompt)
                assertEquals("builtin.full_analysis.v2", catalog.selectedModeId)
            } finally {
                secondScope.coroutineContext[Job]!!.cancelAndJoin()
            }
        } finally {
            if (firstScope.coroutineContext[Job]!!.isActive) firstScope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test fun restoreDefaultReadsOriginalAssetAgain() = runBlocking { withStore { repo, _ ->
        repo.migrateIfNeeded()
        repo.save("builtin.keypoints.v1", "信息点提取V1", "用户修改")
        val restored = repo.restoreDefault("builtin.keypoints.v1")
        assertEquals(assetFile("信息点提取V1.md").readText(Charsets.UTF_8),
            restored.modes.single { it.id == "builtin.keypoints.v1" }.prompt)
    } }

    @Test fun newBuiltInCanBeSelectedEditedAndRestored() = runBlocking { withStore { repo, _ ->
        repo.migrateIfNeeded()
        val id = "builtin.info_rescue.v1"
        repo.select(id)
        repo.save(id, "信息抢救", "用户修改的提示词")
        assertEquals(id, repo.catalogs.first().selectedModeId)
        assertEquals("用户修改的提示词", repo.catalogs.first().modes.single { it.id == id }.prompt)
        val restored = repo.restoreDefault(id)
        assertEquals(assetFile("信息抢救.md").readText(Charsets.UTF_8),
            restored.modes.single { it.id == id }.prompt)
    } }

    @Test fun builtInModeCannotBeDeleted() = runBlocking { withStore { repo, _ ->
        repo.migrateIfNeeded()
        assertTrue(runCatching { repo.delete(PromptRepository.FIRST_BUILT_IN_ID) }.isFailure)
        assertEquals(7, repo.catalogs.first().modes.size)
    } }

    @Test fun fiveCustomModesHaveDistinctIdsAndCreationOrder() = runBlocking { withStore { repo, _ ->
        repo.migrateIfNeeded()
        coroutineScope { (1..5).map { number ->
            async { repo.add("模式$number", "提示词$number") }
        }.awaitAll() }
        val custom = repo.catalogs.first().modes.filterNot { it.builtIn }
        assertEquals(5, custom.size)
        assertEquals(5, custom.map { it.id }.toSet().size)
        assertEquals(custom.map { it.createdOrder }.sorted(), custom.map { it.createdOrder })
        assertEquals((1..5).map { "模式$it" }.toSet(), custom.map { it.name }.toSet())
    } }

    @Test fun editingAndDeletingOneCustomModePreservesOthers() = runBlocking { withStore { repo, _ ->
        repo.migrateIfNeeded()
        val first = repo.add("甲", "甲的提示词").modes.last()
        val second = repo.add("乙", "乙的提示词").modes.last()
        repo.save(first.id, "甲改名", "甲的新提示词")
        assertEquals("乙的提示词", repo.catalogs.first().modes.single { it.id == second.id }.prompt)
        repo.delete(first.id)
        val modes = repo.catalogs.first().modes
        assertFalse(modes.any { it.id == first.id })
        assertEquals("乙的提示词", modes.single { it.id == second.id }.prompt)
    } }

    @Test fun deletingSelectedCustomModeFallsBackToFirstBuiltIn() = runBlocking { withStore { repo, _ ->
        repo.migrateIfNeeded()
        val custom = repo.add("自定义", "提示词").modes.last()
        repo.select(custom.id)
        val deleted = repo.delete(custom.id)
        assertEquals(PromptRepository.FIRST_BUILT_IN_ID, deleted.selectedModeId)
        assertFalse(deleted.modes.any { it.id == custom.id })
    } }

    @Test fun emptyNameAndPromptAreRejectedWithoutChangingData() = runBlocking { withStore { repo, _ ->
        repo.migrateIfNeeded()
        val before = repo.catalogs.first()
        assertTrue(runCatching { repo.add(" ", "正文") }.isFailure)
        assertTrue(runCatching { repo.add("名称", "  ") }.isFailure)
        assertEquals(before, repo.catalogs.first())
    } }

    @Test fun customModeAndSelectionPersistAfterReopening() = runBlocking {
        val file = File.createTempFile("aireader-custom-reopen-", ".preferences_pb")
        file.delete()
        val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val first = repository(PreferenceDataStoreFactory.create(
                scope = firstScope, produceFile = { file }))
            first.migrateIfNeeded()
            val custom = first.add("论文精读", "完整正文\n第二行").modes.last()
            first.select(custom.id)
            firstScope.coroutineContext[Job]!!.cancelAndJoin()

            val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try {
                val reopened = repository(PreferenceDataStoreFactory.create(
                    scope = secondScope, produceFile = { file }))
                val catalog = reopened.catalogs.first()
                assertEquals(custom.id, catalog.selectedModeId)
                assertEquals(custom, catalog.modes.single { it.id == custom.id })
            } finally {
                secondScope.coroutineContext[Job]!!.cancelAndJoin()
            }
        } finally {
            if (firstScope.coroutineContext[Job]!!.isActive) firstScope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test fun legacyModesMigrateWithoutLosingEditedPrompts() = runBlocking { withStore { repo, store ->
        val legacy = JSONArray()
            .put(JSONObject().put("id", "builtin.keypoints.v1").put("name", "旧版内置")
                .put("prompt", "旧版用户修改\n不应丢失").put("builtIn", true)
                .put("assetName", "信息点提取V1.md"))
            .put(JSONObject().put("id", "legacy-custom-uuid").put("name", "旧版自定义")
                .put("prompt", "旧版自定义正文").put("builtIn", false).put("assetName", ""))
        store.edit { it[stringPreferencesKey("modes")] = legacy.toString() }
        repo.migrateIfNeeded()
        val catalog = repo.catalogs.first()
        assertEquals("旧版用户修改\n不应丢失",
            catalog.modes.single { it.id == "builtin.keypoints.v1" }.prompt)
        assertEquals("旧版自定义正文",
            catalog.modes.single { it.id == "legacy-custom-uuid" }.prompt)
        assertTrue(store.data.first()[stringPreferencesKey("prompt_catalog_v2")] != null)
    } }

    @Test fun existingCatalogGainsFifthBuiltInWithoutChangingUserData() = runBlocking { withStore { repo, store ->
        val oldModes = JSONArray()
        PromptRepository.builtIns.take(4).forEachIndexed { index, (id, filename) ->
            oldModes.put(JSONObject().put("id", id).put("name", filename.removeSuffix(".md"))
                .put("prompt", if (index == 0) "原有用户修改" else assetFile(filename).readText(Charsets.UTF_8))
                .put("builtIn", true).put("assetName", filename).put("createdOrder", index))
        }
        oldModes.put(JSONObject().put("id", "existing-custom").put("name", "原有自定义")
            .put("prompt", "原有自定义正文").put("builtIn", false).put("assetName", "")
            .put("createdOrder", 4))
        store.edit { it[stringPreferencesKey("prompt_catalog_v2")] = JSONObject()
            .put("schemaVersion", 2).put("selectedModeId", "existing-custom")
            .put("modes", oldModes).toString() }

        repo.migrateIfNeeded()
        val catalog = repo.catalogs.first()
        assertEquals(PromptRepository.builtIns.take(5).map { it.first },
            catalog.modes.take(5).map { it.id })
        assertEquals("existing-custom", catalog.selectedModeId)
        assertEquals("原有用户修改", catalog.modes.first().prompt)
        assertEquals("原有自定义正文", catalog.modes.last().prompt)
        assertEquals(assetFile("信息抢救.md").readText(Charsets.UTF_8), catalog.modes[4].prompt)
    } }

    @Test fun existingCatalogGainsTwoExternalBuiltInsWithoutChangingUserData() = runBlocking {
        withStore { repo, store ->
            val oldModes = JSONArray()
            PromptRepository.builtIns.take(5).forEachIndexed { index, (id, filename) ->
                oldModes.put(JSONObject().put("id", id).put("name", filename.removeSuffix(".md"))
                    .put("prompt", if (index == 0) "原有用户修改" else assetFile(filename).readText(Charsets.UTF_8))
                    .put("builtIn", true).put("assetName", filename).put("createdOrder", index))
            }
            oldModes.put(JSONObject().put("id", "existing-custom").put("name", "原有自定义")
                .put("prompt", "原有自定义正文").put("builtIn", false).put("assetName", "")
                .put("createdOrder", 5))
            store.edit { it[stringPreferencesKey("prompt_catalog_v2")] = JSONObject()
                .put("schemaVersion", 2).put("selectedModeId", "existing-custom")
                .put("modes", oldModes).toString() }

            repo.migrateIfNeeded()
            val catalog = repo.catalogs.first()
            assertEquals(PromptRepository.builtIns.map { it.first }, catalog.modes.take(7).map { it.id })
            assertEquals("existing-custom", catalog.selectedModeId)
            assertEquals("原有用户修改", catalog.modes.first().prompt)
            assertEquals("原有自定义正文", catalog.modes.last().prompt)
            PromptRepository.builtIns.drop(5).forEach { (id, filename) ->
                assertEquals(assetFile(filename).readText(Charsets.UTF_8),
                    catalog.modes.single { it.id == id }.prompt)
            }
        }
    }

    @Test fun malformedSavedDataIsNotSilentlyReplaced() = runBlocking { withStore { repo, store ->
        store.edit { it[stringPreferencesKey("modes")] = "broken json" }
        assertTrue(runCatching { repo.migrateIfNeeded() }.isFailure)
        assertEquals("broken json", store.data.first()[stringPreferencesKey("modes")])
        assertEquals(null, store.data.first()[stringPreferencesKey("prompt_catalog_v2")])
    } }

    @Test fun malformedCurrentCatalogIsNotSilentlyReplaced() = runBlocking { withStore { repo, store ->
        store.edit { it[stringPreferencesKey("prompt_catalog_v2")] = "{invalid" }
        assertTrue(runCatching { repo.migrateIfNeeded() }.isFailure)
        assertEquals("{invalid", store.data.first()[stringPreferencesKey("prompt_catalog_v2")])
    } }

    @Test fun longMarkdownPromptIsSavedExactly() = runBlocking { withStore { repo, _ ->
        repo.migrateIfNeeded()
        val longPrompt = buildString {
            repeat(500) { append("## 标题$it\n- 条目 $it：完整内容。\n\n") }
        }
        val custom = repo.add("长文", longPrompt).modes.last()
        assertEquals(longPrompt, repo.catalogs.first().modes.single { it.id == custom.id }.prompt)
        assertNotEquals(longPrompt.trim(), longPrompt)
    } }

    @Test fun finalUserMessageUsesSavedPromptAndProcessedArticle() = runBlocking { withStore { repo, _ ->
        repo.migrateIfNeeded()
        val custom = repo.add("分析", "完整提示词\n末行  \n").modes.last()
        repo.select(custom.id)
        val selected = repo.catalogs.first().modes.single { it.id == custom.id }
        val processed = ArticlePreprocessor.process("第一段。\n第二段。", PreprocessOptions(paraOnce = 3))
        assertEquals("完整提示词\n末行\n{段落1} 第一段。第二段。",
            buildUserMessage(selected.prompt, processed))
    } }
}
