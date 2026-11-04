package com.example.aireader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsNavigationTest {
    @Test fun homeHasExactlyFourMenusInRequestedOrder() {
        assertEquals(listOf("大模型 API 配置", "夜间模式", "阅读历史与数据管理", "关于"),
            settingsHomeDestinations.map { it.title })
        assertFalse(settingsHomeDestinations.contains(SettingsDestination.HISTORY_LIST))
        assertFalse(settingsHomeDestinations.any { it.title.contains("网络稳定性") })
    }

    @Test fun apiEditorReturnsThroughList() {
        assertEquals(SettingsDestination.API_LIST, SettingsDestination.API_EDIT.parent())
        assertEquals(SettingsDestination.HOME, SettingsDestination.API_LIST.parent())
    }

    @Test fun historyReturnsToDataManagementWhenOpenedFromSettings() {
        assertEquals(SettingsDestination.HISTORY_LIST, SettingsDestination.HISTORY_DETAIL.parent())
        assertEquals(SettingsDestination.HISTORY_DATA, SettingsDestination.HISTORY_LIST.parent())
        assertEquals(SettingsDestination.HOME, SettingsDestination.HISTORY_DATA.parent())
    }

    @Test fun themeAndAboutReturnHomeWithoutCycle() {
        assertEquals(SettingsDestination.HOME, SettingsDestination.APPEARANCE.parent())
        assertEquals(SettingsDestination.HOME, SettingsDestination.ABOUT.parent())
        assertNull(SettingsDestination.HOME.parent())
    }
}
