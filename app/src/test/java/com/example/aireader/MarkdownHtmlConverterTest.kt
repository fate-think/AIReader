package com.example.aireader

import org.junit.Assert.*
import org.junit.Test

class MarkdownHtmlConverterTest {
    private val converter = MarkdownHtmlConverter()

    @Test fun headingsOneThroughSix() {
        val html = converter.convert((1..6).joinToString("\n\n") { "${"#".repeat(it)} 标题$it" })
        (1..6).forEach { assertTrue(html.contains("<h$it>标题$it</h$it>")) }
    }

    @Test fun inlineFormattingAndCode() {
        val html = converter.convert("**粗体** *斜体* `代码` ~~删除~~")
        assertTrue(html.contains("<strong>粗体</strong>"))
        assertTrue(html.contains("<em>斜体</em>"))
        assertTrue(html.contains("<code>代码</code>"))
        assertTrue(html.contains("<del>删除</del>"))
    }

    @Test fun blockFormatting() {
        val html = converter.convert("- 项目一\n  - 子项\n\n1. 第一\n2. 第二\n\n> 引用\n\n---\n\n```kotlin\nval x = 1\n```")
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<ol>"))
        assertTrue(html.contains("<blockquote>"))
        assertTrue(html.contains("<hr"))
        assertTrue(html.contains("val x = 1"))
    }

    @Test fun gfmTableAndChineseText() {
        val html = converter.convert("| 名称 | 值 |\n| --- | --- |\n| 中文 | 你好，世界 |")
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("你好，世界"))
    }

    @Test fun untrustedHtmlEscapesAndDangerousLinksDisappear() {
        val html = converter.convert("<script>alert(1)</script>\n\n<a href=\"javascript:alert(1)\">恶意</a>\n\nA < B & C\n\n[危险](javascript:alert(1)) [安全](https://example.com)\n\n![远程图](https://example.com/x.png)")
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("&amp;"))
        assertFalse(html.contains("href=\"javascript:"))
        assertTrue(html.contains("危险"))
        assertTrue(html.contains("href=\"https://example.com\""))
        assertFalse(html.contains("<img"))
        assertTrue(html.contains("远程图"))
    }

    @Test fun completeDocumentAndFontSize() {
        val html = converter.convert("", 36)
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<html lang=\"zh-CN\">"))
        assertTrue(html.contains("<meta charset=\"UTF-8\">"))
        assertTrue(html.contains("name=\"viewport\""))
        assertTrue(html.contains("<title>"))
        assertTrue(html.contains("<style>"))
        assertTrue(html.contains("font-size: 36px"))
        assertTrue(html.contains("<body>"))
        assertTrue(html.contains("<body>\n</body>"))
        assertTrue(html.endsWith("</html>"))
        assertTrue(converter.convert("文本").contains("font-size: 24px"))
        assertThrows(IllegalArgumentException::class.java) { converter.convert("文本", 13) }
    }

    @Test fun longMarkdownDoesNotLoseLastParagraph() {
        val markdown = (1..500).joinToString("\n\n") { "## 第${it}节\n\n内容$it" }
        val html = converter.convert(markdown)
        assertTrue(html.contains("第500节"))
        assertTrue(html.contains("内容500"))
    }
}
