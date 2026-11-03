package com.example.aireader

import java.io.OutputStream

object HtmlExport {
    fun write(output: OutputStream, html: String) {
        output.write(html.toByteArray(Charsets.UTF_8))
    }
}
