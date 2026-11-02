package com.example.aireader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ArticlePreprocessorTest {
    @Test fun paraOnceOneNumbersEachBodyLine() {
        assertEquals("{段落1} 甲。\n{段落2} 乙。",
            ArticlePreprocessor.process("甲。\n乙。", PreprocessOptions(paraOnce = 1)))
    }

    @Test fun threeBodyLinesAreConcatenatedExactly() {
        assertEquals("{段落1} 第一段内容。第二段内容。第三段内容。",
            ArticlePreprocessor.process("第一段内容。\n第二段内容。\n第三段内容。", PreprocessOptions()))
    }

    @Test fun mergedBodyHasNoInsertedNewlineOrSpace() {
        val actual = ArticlePreprocessor.process("甲。\n乙。\n丙。", PreprocessOptions())
        assertEquals("{段落1} 甲。乙。丙。", actual)
        assertFalse(actual.contains("甲。 乙。"))
        assertFalse(actual.contains("甲。\n乙。"))
    }

    @Test fun trailingShortBodyGroupIsEmitted() {
        assertEquals("{段落1} 甲。乙。丙。\n{段落2} 丁。",
            ArticlePreprocessor.process("甲。\n乙。\n丙。\n丁。", PreprocessOptions()))
    }

    @Test fun titleGetsMatchingParagraphAndTitleNumber() {
        assertEquals("{段落1}{标题1} 第一章\n{段落2} 正文。",
            ArticlePreprocessor.process("第一章\n正文。", PreprocessOptions()))
    }

    @Test fun numericListIsSeparateAndTakesPriorityOverTitle() {
        assertEquals("{段落1} 1. 第一项\n{段落2} 2、第二项\n{段落3} （3）第三项\n{段落4} (4)第四项",
            ArticlePreprocessor.process("1. 第一项\n2、第二项\n（3）第三项\n(4)第四项", PreprocessOptions()))
    }

    @Test fun alphabeticListIsSeparate() {
        assertEquals("{段落1} A. 甲\n{段落2} B)乙\n{段落3} (C)丙\n{段落4} （D）丁",
            ArticlePreprocessor.process("A. 甲\nB)乙\n(C)丙\n（D）丁", PreprocessOptions()))
    }

    @Test fun numberedListCanHaveNoSpaceAfterMarker() {
        assertEquals("{段落1} 1.第一项\n{段落2} A.第二项",
            ArticlePreprocessor.process("1.第一项\nA.第二项", PreprocessOptions()))
    }

    @Test fun chineseColonDoesNotMakeBodyText() {
        assertEquals("{段落1}{标题1} 导语：背景",
            ArticlePreprocessor.process("导语：背景", PreprocessOptions()))
    }

    @Test fun blankLinesAreIgnored() {
        assertEquals("{段落1} 甲。乙。",
            ArticlePreprocessor.process("\n甲。\n   \n\t\n乙。\n", PreprocessOptions()))
    }

    @Test fun bodyBufferIsFlushedBeforeTitle() {
        assertEquals("{段落1} 甲。乙。\n{段落2}{标题2} 小标题\n{段落3} 丙。",
            ArticlePreprocessor.process("甲。\n乙。\n小标题\n丙。", PreprocessOptions()))
    }

    @Test fun bodyBufferIsFlushedBeforeNumberedList() {
        assertEquals("{段落1} 甲。乙。\n{段落2} 1. 项目\n{段落3} 丙。",
            ArticlePreprocessor.process("甲。\n乙。\n1. 项目\n丙。", PreprocessOptions()))
    }

    @Test fun paragraphNumbersAreContinuousAcrossKinds() {
        assertEquals("{段落1} 甲。\n{段落2}{标题2} 标题\n{段落3} 1. 项目\n{段落4} 乙。丙。丁。",
            ArticlePreprocessor.process("甲。\n标题\n1. 项目\n乙。\n丙。\n丁。", PreprocessOptions()))
    }

    @Test fun disablingPreprocessingReturnsOriginalExactly() {
        val original = "标题\r\n\r\n甲。  \n"
        assertEquals(original, ArticlePreprocessor.process(original, PreprocessOptions(enabled = false)))
    }

    @Test fun windowsNewlinesAreSupported() {
        assertEquals("{段落1} 甲。乙。丙。",
            ArticlePreprocessor.process("甲。\r\n乙。\r\n丙。", PreprocessOptions()))
    }

    @Test fun mixedChineseAndEnglishIsPreserved() {
        assertEquals("{段落1} Kotlin 很好。Compose works!AI 阅读。",
            ArticlePreprocessor.process("Kotlin 很好。\nCompose works!\nAI 阅读。", PreprocessOptions()))
    }

    @Test fun originalArticleIsUnchanged() {
        val original = "甲。\n\n乙。"
        ArticlePreprocessor.process(original, PreprocessOptions())
        assertEquals("甲。\n\n乙。", original)
    }

    @Test fun titleAndListDetectionCanBeDisabledIndependently() {
        assertEquals("{段落1} 标题1. 列表正文。",
            ArticlePreprocessor.process("标题\n1. 列表\n正文。",
                PreprocessOptions(detectTitles = false, detectNumberedLists = false)))
    }

    @Test fun userMessageContainsOnlyPromptAndProcessedBody() {
        val prompt = "完整提示词\n末行  \n"
        val article = ArticlePreprocessor.process("甲。", PreprocessOptions())
        assertEquals("完整提示词\n末行\n\n{段落1} 甲。", buildUserMessage(prompt, article))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsParaOnceOutsideRange() { PreprocessOptions(paraOnce = 101) }
}
