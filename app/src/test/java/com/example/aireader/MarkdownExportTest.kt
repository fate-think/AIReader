package com.example.aireader

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class MarkdownExportTest {
    @Test fun writesCompleteOriginalMarkdownAsUtf8() {
        val markdown = "# 标题\n\n| 中文 | English |\n| --- | --- |\n| 🙂 | text |\n"
        val output = ByteArrayOutputStream()
        MarkdownExport.write(output, markdown)
        assertArrayEquals(markdown.toByteArray(Charsets.UTF_8), output.toByteArray())
    }
}
