package com.example.aireader

import org.junit.Assert.assertEquals
import org.junit.Test

class UserMessageBuilderTest {
    @Test fun promptAndProcessedArticleHaveExactlyOneBoundaryNewline() {
        val prompt = "## 标题\n\n- 条目\n末行  \n\n"
        val article = "{段落1} 正文。\n{段落2} 结尾。"
        assertEquals("## 标题\n\n- 条目\n末行\n{段落1} 正文。\n{段落2} 结尾。",
            buildUserMessage(prompt, article))
        assertEquals("{段落1} 正文。\n{段落2} 结尾。", article)
    }

    @Test fun leadingArticleLineBreaksDoNotCreateAnEmptyBoundaryLine() {
        assertEquals("提示词\n  正文首行\n\n正文次行",
            buildUserMessage("提示词\r\n", "\r\n\n  正文首行\n\n正文次行"))
    }
}
