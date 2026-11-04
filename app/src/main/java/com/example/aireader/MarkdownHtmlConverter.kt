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
enum class HtmlDocumentTheme { AUTO, LIGHT, DARK }

class MarkdownHtmlConverter {
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions)
        .escapeHtml(true).sanitizeUrls(true).build()

    fun convert(markdown: String, fontSizePx: Int = 24,
                theme: HtmlDocumentTheme = HtmlDocumentTheme.AUTO): String {
        require(fontSizePx in 14..36) { "HTML 字体大小必须在 14–36px 之间。" }
        val document = parser.parse(markdown)
        removeUnsafeResources(document)
        val body = renderer.render(document)
        val lightColors = "--page-bg:#ffffff; --text-color:#202124; --code-bg:#f4f5f7; " +
            "--table-border:#c8c8c8; --quote-border:#b4b9c4; --quote-text:#555555; " +
            "--link-color:#1758a8;"
        val darkColors = "--page-bg:#17191c; --text-color:#e8e8e8; --code-bg:#292d32; " +
            "--table-border:#626a76; --quote-border:#858f9d; --quote-text:#bbbbbb; " +
            "--link-color:#92bbff;"
        val themeCss = when (theme) {
            HtmlDocumentTheme.AUTO -> ":root { color-scheme: light dark; $lightColors }\n" +
                "@media (prefers-color-scheme: dark) { :root { $darkColors } }"
            HtmlDocumentTheme.LIGHT -> ":root { color-scheme: light; $lightColors }"
            HtmlDocumentTheme.DARK -> ":root { color-scheme: dark; $darkColors }"
        }
        return """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src 'none'; script-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'">
<title>AIReader 阅读报告</title>
<style>
$themeCss
body { font-size: ${fontSizePx}px; line-height: 1.7; max-width: 900px; margin: 0 auto; padding: 16px; overflow-wrap: anywhere; font-family: system-ui, sans-serif; color: var(--text-color); background: var(--page-bg); }
pre { overflow-x: auto; padding: 12px; background: var(--code-bg); border-radius: 8px; }
code { font-family: ui-monospace, monospace; background: var(--code-bg); }
table { border-collapse: collapse; display: block; overflow-x: auto; max-width: 100%; }
th, td { border: 1px solid var(--table-border); padding: 6px 10px; }
th { background: var(--code-bg); }
img { max-width: 100%; }
blockquote { border-left: 4px solid var(--quote-border); margin-left: 0; padding-left: 16px; color: var(--quote-text); }
a { color: var(--link-color); }
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
