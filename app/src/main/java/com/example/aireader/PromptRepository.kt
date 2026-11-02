package com.example.aireader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ReadingMode(
    val id: String,
    val name: String,
    val prompt: String,
    val builtIn: Boolean,
    val assetName: String? = null,
    val createdOrder: Long = 0
)

data class PromptCatalog(val modes: List<ReadingMode>, val selectedModeId: String)

class PromptRepository(
    private val dataStore: DataStore<Preferences>,
    private val readAsset: (String) -> String
) {
    companion object {
        const val FIRST_BUILT_IN_ID = "builtin.keypoints.v1"
        val builtIns = listOf(
            FIRST_BUILT_IN_ID to "信息点提取V1.md",
            "builtin.keypoints.v2" to "信息点提取V2.md",
            "builtin.full_analysis.v1" to "全文分析总结V1.md",
            "builtin.full_analysis.v2" to "全文分析总结V2.md"
        )
    }

    private val catalogKey = stringPreferencesKey("prompt_catalog_v2")
    private val legacyModesKey = stringPreferencesKey("modes")

    val catalogs: Flow<PromptCatalog> = dataStore.data.map { decode(it) }

    suspend fun migrateIfNeeded() {
        dataStore.edit { preferences ->
            val catalog = decode(preferences)
            val encoded = encode(catalog)
            if (preferences[catalogKey] != encoded) preferences[catalogKey] = encoded
        }
    }

    suspend fun select(id: String): PromptCatalog = update { current ->
        require(current.modes.any { it.id == id }) { "Reading mode does not exist" }
        current.copy(selectedModeId = id)
    }

    suspend fun add(name: String, prompt: String): PromptCatalog = update { current ->
        validate(name, prompt)
        val order = (current.modes.maxOfOrNull { it.createdOrder } ?: 3L) + 1
        current.copy(modes = current.modes + ReadingMode(
            UUID.randomUUID().toString(), name.trim(), prompt, false, createdOrder = order))
    }

    suspend fun save(id: String, name: String, prompt: String): PromptCatalog = update { current ->
        validate(name, prompt)
        require(current.modes.any { it.id == id }) { "Reading mode does not exist" }
        current.copy(modes = current.modes.map { mode ->
            if (mode.id == id) mode.copy(name = name.trim(), prompt = prompt) else mode
        })
    }

    suspend fun delete(id: String): PromptCatalog = update { current ->
        val mode = current.modes.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Reading mode does not exist")
        require(!mode.builtIn) { "Built-in mode cannot be deleted" }
        current.copy(
            modes = current.modes.filterNot { it.id == id },
            selectedModeId = if (current.selectedModeId == id) FIRST_BUILT_IN_ID else current.selectedModeId
        )
    }

    suspend fun restoreDefault(id: String): PromptCatalog = update { current ->
        val asset = builtIns.firstOrNull { it.first == id }?.second
            ?: throw IllegalArgumentException("Not a built-in mode")
        val original = readAsset(asset)
        current.copy(modes = current.modes.map { mode ->
            if (mode.id == id) mode.copy(prompt = original) else mode
        })
    }

    private suspend fun update(transform: (PromptCatalog) -> PromptCatalog): PromptCatalog {
        var result: PromptCatalog? = null
        dataStore.edit { preferences ->
            val next = transform(decode(preferences))
            preferences[catalogKey] = encode(next)
            result = next
        }
        return requireNotNull(result)
    }

    private fun validate(name: String, prompt: String) {
        require(name.isNotBlank()) { "Mode name cannot be empty" }
        require(prompt.isNotBlank()) { "Prompt cannot be empty" }
    }

    private fun decode(preferences: Preferences): PromptCatalog {
        val modern = preferences[catalogKey]
        val saved = if (modern != null) decodeModern(modern) else decodeLegacy(preferences[legacyModesKey])
        val byId = saved.modes.associateBy { it.id }
        require(byId.size == saved.modes.size) { "Duplicate reading mode ID" }
        val defaults = builtIns.mapIndexed { index, (id, asset) ->
            byId[id]?.copy(builtIn = true, assetName = asset, createdOrder = index.toLong())
                ?: ReadingMode(id, asset.removeSuffix(".md"), readAsset(asset), true, asset, index.toLong())
        }
        val custom = saved.modes.filter { mode -> builtIns.none { it.first == mode.id } }
            .map { it.copy(builtIn = false, assetName = null) }
            .sortedBy { it.createdOrder }
        val modes = defaults + custom
        val selected = saved.selectedModeId.takeIf { id -> modes.any { it.id == id } } ?: FIRST_BUILT_IN_ID
        return PromptCatalog(modes, selected)
    }

    private fun decodeModern(raw: String): PromptCatalog {
        val root = JSONObject(raw)
        require(root.getInt("schemaVersion") == 2) { "Unsupported prompt data version" }
        val modes = root.getJSONArray("modes")
        return PromptCatalog((0 until modes.length()).map { index ->
            val item = modes.getJSONObject(index)
            ReadingMode(
                id = item.getString("id"), name = item.getString("name"),
                prompt = item.getString("prompt"), builtIn = item.getBoolean("builtIn"),
                assetName = item.optString("assetName").ifBlank { null },
                createdOrder = item.getLong("createdOrder")
            ).also { validateMode(it) }
        }, root.getString("selectedModeId"))
    }

    private fun decodeLegacy(raw: String?): PromptCatalog {
        if (raw == null) return PromptCatalog(emptyList(), FIRST_BUILT_IN_ID)
        val modes = JSONArray(raw)
        return PromptCatalog((0 until modes.length()).map { index ->
            val item = modes.getJSONObject(index)
            ReadingMode(
                id = item.getString("id"), name = item.getString("name"),
                prompt = item.getString("prompt"), builtIn = item.getBoolean("builtIn"),
                assetName = item.optString("assetName").ifBlank { null },
                createdOrder = index.toLong() + 4
            ).also { validateMode(it) }
        }, FIRST_BUILT_IN_ID)
    }

    private fun validateMode(mode: ReadingMode) {
        require(mode.id.isNotBlank()) { "Reading mode ID is empty" }
        validate(mode.name, mode.prompt)
    }

    private fun encode(catalog: PromptCatalog): String {
        val array = JSONArray()
        catalog.modes.forEach { mode ->
            array.put(JSONObject()
                .put("id", mode.id).put("name", mode.name).put("prompt", mode.prompt)
                .put("builtIn", mode.builtIn).put("assetName", mode.assetName ?: "")
                .put("createdOrder", mode.createdOrder))
        }
        return JSONObject().put("schemaVersion", 2).put("selectedModeId", catalog.selectedModeId)
            .put("modes", array).toString()
    }
}
