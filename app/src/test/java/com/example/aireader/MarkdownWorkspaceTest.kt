package com.example.aireader

import org.junit.Assert.*
import org.junit.Test

class MarkdownWorkspaceTest {
    @Test fun firstOpenIsEmptyAndEditable() {
        val state = ReaderState()
        assertEquals("", state.result)
        assertEquals(MarkdownViewMode.EDIT, state.markdownViewMode)
        assertEquals(MarkdownSource.NONE, state.markdownSource)
    }

    @Test fun manualPastePreservesOriginalMarkdownAndDoesNotBecomeAiSuccess() {
        val markdown = "# 标题\n\n```kotlin\nval a = 1\n```\n| 甲 | B |"
        val state = ReaderState(article = "独立的文章原文。", status = ReadingStatus.Completed)
            .withEditedMarkdown(markdown)
        assertEquals(markdown, state.result)
        assertEquals("独立的文章原文。", state.article)
        assertEquals(MarkdownSource.MANUAL, state.markdownSource)
        assertEquals(ReadingStatus.Idle, state.status)
        assertEquals(MarkdownViewMode.EDIT, state.markdownViewMode)
    }

    @Test fun editPreviewEditKeepsExactlyTheSameContent() {
        val edited = ReaderState().withEditedMarkdown("# 中文 English\n  空格\n")
        val preview = edited.copy(markdownViewMode = MarkdownViewMode.PREVIEW)
        val back = preview.copy(markdownViewMode = MarkdownViewMode.EDIT)
        assertEquals(edited.result, preview.result)
        assertEquals(edited.result, back.result)
    }

    @Test fun editingAiReportMarksItAsModified() {
        val finished = ReaderState().withMarkdownGenerationStarted()
            .withGeneratedMarkdown("# AI 报告", ReadingStatus.Completed)
        val edited = finished.withEditedMarkdown("# 修改后的报告")
        assertEquals(MarkdownSource.EDITED_AI, edited.markdownSource)
        assertEquals(ReadingStatus.Idle, edited.status)
        assertTrue(MarkdownWorkspace.requiresOverwriteConfirmation(edited.result, edited.markdownSource))
    }

    @Test fun replacementNeedsConfirmationForManualAndModifiedAiOnly() {
        assertFalse(MarkdownWorkspace.requiresOverwriteConfirmation("", MarkdownSource.MANUAL))
        assertFalse(MarkdownWorkspace.requiresOverwriteConfirmation("AI 内容", MarkdownSource.AI))
        assertTrue(MarkdownWorkspace.requiresOverwriteConfirmation("外部内容", MarkdownSource.MANUAL))
        assertTrue(MarkdownWorkspace.requiresOverwriteConfirmation("修改内容", MarkdownSource.EDITED_AI))
    }

    @Test fun confirmedGenerationUsesAiStateAndPreview() {
        val before = ReaderState(article = "原文。", result = "# 外部 Markdown",
            markdownSource = MarkdownSource.MANUAL)
        val started = before.withMarkdownGenerationStarted()
        assertEquals("", started.result)
        assertEquals("原文。", started.article)
        assertEquals(MarkdownSource.AI, started.markdownSource)
        assertEquals(MarkdownViewMode.PREVIEW, started.markdownViewMode)
        assertEquals(ReadingStatus.Loading, started.status)
    }

    @Test fun sseTextAccumulatesAndCompletionKeepsPreview() {
        val started = ReaderState().withMarkdownGenerationStarted()
        val first = started.withGeneratedMarkdown("第一段", ReadingStatus.Streaming)
        val second = first.withGeneratedMarkdown("第一段第二段", ReadingStatus.Streaming)
        val finished = second.withGeneratedMarkdown("第一段第二段", ReadingStatus.Completed)
        assertEquals("第一段第二段", finished.result)
        assertEquals(MarkdownSource.AI, finished.markdownSource)
        assertEquals(MarkdownViewMode.PREVIEW, finished.markdownViewMode)
    }

    @Test fun stopAndFailureKeepReceivedText() {
        val partial = ReaderState().withMarkdownGenerationStarted()
            .withGeneratedMarkdown("已有文本", ReadingStatus.Streaming)
        assertEquals("已有文本", partial.withGeneratedMarkdown("已有文本", ReadingStatus.Cancelled).result)
        assertEquals("已有文本", partial.withGeneratedMarkdown("已有文本", ReadingStatus.Interrupted).result)
    }

    @Test fun failureWithoutOutputReturnsToBlankEditor() {
        val failed = ReaderState().withMarkdownGenerationStarted()
            .withGeneratedMarkdown("", ReadingStatus.Error)
        assertEquals("", failed.result)
        assertEquals(MarkdownSource.NONE, failed.markdownSource)
        assertEquals(MarkdownViewMode.EDIT, failed.markdownViewMode)
    }

    @Test fun clearOnlyResetsWorkspaceAndKeepsArticleAndSettings() {
        val before = ReaderState(article = "原文。", result = "# 报告",
            markdownSource = MarkdownSource.MANUAL, htmlFontSizePx = 30)
        val after = before.withClearedMarkdown()
        assertEquals("", after.result)
        assertEquals(MarkdownViewMode.EDIT, after.markdownViewMode)
        assertEquals(MarkdownSource.NONE, after.markdownSource)
        assertEquals(before.article, after.article)
        assertEquals(before.settings, after.settings)
        assertEquals(30, after.htmlFontSizePx)
    }

    @Test fun htmlIsAvailableForManualTextAndDisabledWhileGeneratingOrEmpty() {
        assertTrue(MarkdownWorkspace.canUseHtml("# 外部内容", generating = false))
        assertFalse(MarkdownWorkspace.canUseHtml("# 正在生成", generating = true))
        assertFalse(MarkdownWorkspace.canUseHtml("  \n", generating = false))
    }

    @Test fun editingAgainUsesLatestMarkdownForConversion() {
        val first = ReaderState().withEditedMarkdown("# 旧标题")
        val latest = first.withEditedMarkdown("# 新标题\n\n新正文")
        val html = MarkdownHtmlConverter().convert(latest.result, 24)
        assertTrue(html.contains("新标题"))
        assertTrue(html.contains("新正文"))
        assertFalse(html.contains("旧标题"))
    }

    @Test fun editingCompletedAiReportDoesNotChangeItsHistorySnapshot() {
        val mode = ReadingMode("mode", "阅读模式", "提示词", false)
        val capture = HistoryCapture.start("文章原文。", mode, "model", "api",
            HistorySaveOptions(), "提示词\n文章原文。", now = 100L)
        val saved = capture.onEvent(ReadingEvent.Finished, "# AI 原始报告")!!
        val workspace = ReaderState(result = "# AI 原始报告", markdownSource = MarkdownSource.AI,
            status = ReadingStatus.Completed).withEditedMarkdown("# 手动修改报告")
        assertEquals("# AI 原始报告", saved.markdownResult)
        assertEquals("# 手动修改报告", workspace.result)
        assertEquals("文章原文。", saved.originalArticle)
    }
}
