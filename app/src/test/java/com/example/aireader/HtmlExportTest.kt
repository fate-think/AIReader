package com.example.aireader

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class HtmlExportTest {
    @Test fun savedBytesMatchConvertedHtmlExactly() {
        val html = MarkdownHtmlConverter().convert("# 中文报告\n\n完整正文。")
        val output = ByteArrayOutputStream()
        HtmlExport.write(output, html)
        assertArrayEquals(html.toByteArray(Charsets.UTF_8), output.toByteArray())
    }
}
