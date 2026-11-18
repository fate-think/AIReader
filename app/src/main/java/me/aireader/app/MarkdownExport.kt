package me.aireader.app

import java.io.OutputStream

object MarkdownExport {
    fun write(output: OutputStream, markdown: String) {
        output.write(markdown.toByteArray(Charsets.UTF_8))
    }
}
