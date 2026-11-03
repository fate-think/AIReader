package com.example.aireader

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.net.URI

/** Converts untrusted Markdown into a self-contained, offline HTML document. */
class MarkdownHtmlConverter {
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions)
        .escapeHtml(true).sanitizeUrls(true).build()

    fun convert(markdown: String, fontSizePx: Int = 24): String {
        require(fontSizePx in 14..36) { "HTML 字体大小必须在 14–36px 之间。" }
        val document = parser.parse(markdown)
        removeUnsafeResources(document)
        val body = renderer.render(document)
        return """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src 'none'; script-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'">
<title>AIReader 阅读报告</title>
<style>
body { font-size: ${fontSizePx}px; line-height: 1.7; max-width: 900px; margin: 0 auto; padding: 16px; overflow-wrap: anywhere; font-family: system-ui, sans-serif; color: #202124; background: #fff; }
pre { overflow-x: auto; padding: 12px; background: #f4f5f7; border-radius: 8px; }
code { font-family: ui-monospace, monospace; }
table { border-collapse: collapse; display: block; overflow-x: auto; max-width: 100%; }
th, td { border: 1px solid #c8c8c8; padding: 6px 10px; }
img { max-width: 100%; }
blockquote { border-left: 4px solid #b4b9c4; margin-left: 0; padding-left: 16px; color: #555; }
a { color: #1758a8; }
@media (prefers-color-scheme: dark) { body { color: #e8e8e8; background: #17191c; } pre { background: #292d32; } blockquote { color: #bbb; } a { color: #92bbff; } }
</style>
</head>
<body>
$body</body>
</html>"""
    }

    private fun removeUnsafeResources(parent: Node) {
        var node = parent.firstChild
        while (node != null) {
            val next = node.next
            when (node) {
                is Image -> {
                    node.insertBefore(Text(readableText(node)))
                    node.unlink()
                }
                is Link -> {
                    removeUnsafeResources(node)
                    if (!isSafeLink(node.destination)) {
                        var child = node.firstChild
                        while (child != null) {
                            val following = child.next
                            node.insertBefore(child)
                            child = following
                        }
                        node.unlink()
                    }
                }
                else -> removeUnsafeResources(node)
            }
            node = next
        }
    }

    private fun readableText(node: Node): String {
        val result = StringBuilder()
        fun visit(current: Node) {
            if (current is Text) result.append(current.literal)
            var child = current.firstChild
            while (child != null) {
                visit(child)
                child = child.next
            }
        }
        visit(node)
        return result.toString()
    }

    private fun isSafeLink(destination: String): Boolean = try {
        val uri = URI(destination)
        val scheme = uri.scheme?.lowercase()
        scheme in setOf("http", "https", "mailto") &&
            (scheme == "mailto" || !uri.host.isNullOrBlank())
    } catch (_: Exception) { false }
}
