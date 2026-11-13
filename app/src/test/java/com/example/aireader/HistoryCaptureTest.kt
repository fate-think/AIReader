package com.example.aireader

import org.junit.Assert.*
import org.junit.Test

class HistoryCaptureTest {
    private val mode = ReadingMode("mode-id", "旧模式名称", "提示词", false)

    @Test fun completedRequestStoresOriginalAndSnapshotMetadata() {
        val original = "\n  标题第一行  \n第二行原文。"
        val sent = "完整提示词\n{段落1} 标题第一行\n{段落2} 第二行原文。"
        val capture = HistoryCapture.start(original, mode, "old-model", "旧 API 配置",
            HistorySaveOptions(), sent, now = 123L)
        val record = capture.completed("# 完整报告")!!
        assertEquals(capture.requestId, record.id)
        assertEquals("标题第一行", record.title)
        assertEquals(original, record.originalArticle)
        assertEquals(sent, record.finalUserMessage)
        assertEquals("# 完整报告", record.markdownResult)
        assertEquals("mode-id", record.readingModeId)
        assertEquals("旧模式名称", record.readingModeName)
        assertEquals("old-model", record.modelId)
        assertEquals("旧 API 配置", record.apiConfigName)
        assertEquals(123L, record.createdAt)
    }

    @Test fun disabledAndEmptyResultNeverBecomeHistory() {
        assertNull(HistoryCapture.start("正文", mode, "model", "api",
            HistorySaveOptions(autoSave = false), "提示词\n正文").completed("报告"))
        assertNull(HistoryCapture.start("正文", mode, "model", "api",
            HistorySaveOptions(), "提示词\n正文").completed("  \n"))
    }

    @Test fun onlyFinishedEventCanCreateHistoryForEitherResponseType() {
        val capture = HistoryCapture.start("正文。", mode, "model", "api", HistorySaveOptions(), "真实消息")
        assertNull(capture.onEvent(ReadingEvent.Text("部分结果"), "部分结果"))
        assertNull(capture.onEvent(ReadingEvent.RetryWaiting(1, 2, 2), ""))
        assertNull(capture.onEvent(ReadingEvent.RetryExhausted, ""))
        val streamed = capture.onEvent(ReadingEvent.Finished, "完整流式报告")!!
        val nonStreaming = capture.onEvent(ReadingEvent.Finished, "完整非流式报告")!!
        assertEquals(capture.requestId, streamed.id)
        assertEquals(capture.requestId, nonStreaming.id)
    }

    @Test fun originalArticleChoiceIsFixedAtRequestStart() {
        val capture = HistoryCapture.start("原始文章。", mode, "model", "api",
            HistorySaveOptions(saveOriginalArticle = false), "提示词\n原始文章。")
        assertNull(capture.completed("报告")!!.originalArticle)
        assertNull(capture.completed("报告")!!.finalUserMessage)
        val saved = HistoryCapture.start("原始文章。", mode, "model", "api",
            HistorySaveOptions(saveOriginalArticle = true), "提示词\n原始文章。")
        assertEquals("原始文章。", saved.completed("报告")!!.originalArticle)
        assertEquals("提示词\n原始文章。", saved.completed("报告")!!.finalUserMessage)
    }

    @Test fun titleIsBoundedAndMissingTitleUsesFallback() {
        assertEquals(50, HistoryCapture.titleFromArticle("甲".repeat(100)).length)
        assertEquals("未命名阅读报告", HistoryCapture.titleFromArticle(" \n\t"))
    }

    @Test fun requestIdsAreStablePerRequestAndUniqueAcrossRequests() {
        val a = HistoryCapture.start("正文", mode, "model", "api", HistorySaveOptions(), "请求 A")
        val b = HistoryCapture.start("正文", mode, "model", "api", HistorySaveOptions(), "请求 B")
        assertNotEquals(a.requestId, b.requestId)
        assertEquals(a.requestId, a.completed("报告")!!.id)
        assertEquals(a.requestId, a.completed("报告")!!.id)
    }

    @Test fun storageFailureLeavesSuccessfulReportAndArticleIntact() {
        val before = ReaderState(article = "尚未发送的草稿。", result = "# 已生成报告",
            status = ReadingStatus.Completed)
        val after = before.withHistorySaveFailure()
        assertEquals(before.article, after.article)
        assertEquals(before.result, after.result)
        assertEquals(ReadingStatus.Completed, after.status)
        assertTrue(after.historySaveMessage!!.contains("保存到阅读历史失败"))
    }
}
