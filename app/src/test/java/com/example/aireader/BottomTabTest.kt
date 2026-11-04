package com.example.aireader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BottomTabTest {
    @Test fun tabsHaveRequiredOrderAndDistinctDestinations() {
        assertEquals(listOf("阅读", "提示词", "历史", "设置"), BottomTab.entries.map { it.title })
        assertEquals(listOf(0, 1, 2, 3), BottomTab.entries.map { it.index })
        assertNotEquals(BottomTab.HISTORY.index, BottomTab.SETTINGS.index)
    }
}
