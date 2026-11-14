package com.example.aireader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiEditorSnapshotTest {
    private val original = ApiSettings(baseUrl = "https://old.example", modelId = "old-model")
    private val saved = ApiEditorSnapshot("原配置", original)

    @Test fun unchangedFormAndKeyVisibilityAreClean() {
        assertFalse(saved.hasUnsavedChanges("原配置", original, "", false))
        assertFalse(saved.hasUnsavedChanges("原配置", original.copy(hasApiKey = true), "", false))
    }

    @Test fun keyAndConfigChangesRemainDirtyUntilSaved() {
        assertTrue(saved.hasUnsavedChanges("原配置", original, "new-key", false))
        assertTrue(saved.hasUnsavedChanges("原配置", original, "", true))
        assertTrue(saved.hasUnsavedChanges("新名称", original, "", false))
        assertTrue(saved.hasUnsavedChanges("原配置", original.copy(baseUrl = "https://new.example"), "", false))
    }

    @Test fun successfulSaveAdvancesBaselineButLaterEditsStayDirty() {
        val submitted = original.copy(baseUrl = "https://saved.example")
        val afterSave = ApiEditorSnapshot("新名称", submitted)
        assertFalse(afterSave.hasUnsavedChanges("新名称", submitted, "", false))
        assertTrue(afterSave.hasUnsavedChanges("新名称", submitted.copy(modelId = "later"), "", false))
        assertTrue(afterSave.hasUnsavedChanges("新名称", submitted, "later-key", false))
    }
}
