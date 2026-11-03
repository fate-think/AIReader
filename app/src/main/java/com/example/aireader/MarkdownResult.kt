package com.example.aireader

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownText
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import java.net.URI

object MarkdownDocument {
    private val parser = Parser.builder().extensions(listOf(TablesExtension.create())).build()

    fun blocks(markdown: String): List<Node> = children(parser.parse(markdown))

    fun streamingChunks(content: String, maxChars: Int = 2000): List<String> {
        require(maxChars > 1)
        val chunks = ArrayList<String>()
        var start = 0
        while (start < content.length) {
            var end = minOf(start + maxChars, content.length)
            if (end < content.length && Character.isHighSurrogate(content[end - 1]) &&
                Character.isLowSurrogate(content[end])) end--
            chunks += content.substring(start, end)
            start = end
        }
        return chunks
    }

    fun children(node: Node): List<Node> {
        val result = ArrayList<Node>()
        var child = node.firstChild
        while (child != null) {
            result += child
            child = child.next
        }
        return result
    }

    fun orderedNumber(list: OrderedList, index: Int): Int = list.startNumber + index

    fun safeLink(destination: String): String? = try {
        URI(destination).takeIf {
            val scheme = it.scheme?.lowercase()
            scheme in setOf("http", "https", "mailto") &&
                (scheme == "mailto" || !it.host.isNullOrBlank())
        }?.toString()
    } catch (_: Exception) { null }
}

@Composable
fun MarkdownResult(content: String, listState: LazyListState, streaming: Boolean) {
    if (streaming) {
        val chunks = remember(content) { MarkdownDocument.streamingChunks(content) }
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            items(chunks) { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    } else {
        val blocks = remember(content) {
            try { MarkdownDocument.blocks(content) } catch (_: Exception) { null }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (blocks == null) item { Text(content) }
            else items(blocks) { MarkdownBlock(it) }
        }
    }
}

@Composable
private fun MarkdownBlock(node: Node) {
    when (node) {
        is Heading -> InlineMarkdown(node, when (node.level) {
            1 -> MaterialTheme.typography.headlineMedium
            2 -> MaterialTheme.typography.headlineSmall
            3 -> MaterialTheme.typography.titleLarge
            4 -> MaterialTheme.typography.titleMedium
            5 -> MaterialTheme.typography.titleSmall
            else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        })
        is Paragraph -> InlineMarkdown(node, MaterialTheme.typography.bodyMedium)
        is FencedCodeBlock -> CodeBlock(node.literal)
        is IndentedCodeBlock -> CodeBlock(node.literal)
        is BlockQuote -> Surface(color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MarkdownDocument.children(node).forEach { MarkdownBlock(it) }
            }
        }
        is BulletList -> MarkdownList(node, ordered = false)
        is OrderedList -> MarkdownList(node, ordered = true)
        is TableBlock -> MarkdownTable(node)
        is ThematicBreak -> HorizontalDivider()
        is HtmlBlock -> Text(node.literal, style = MaterialTheme.typography.bodyMedium)
        else -> MarkdownDocument.children(node).forEach { MarkdownBlock(it) }
    }
}

@Composable
private fun MarkdownList(node: Node, ordered: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MarkdownDocument.children(node).filterIsInstance<ListItem>().forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth()) {
                Text(if (ordered) "${MarkdownDocument.orderedNumber(node as OrderedList, index)}."
                    else "•", modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MarkdownDocument.children(item).forEach { MarkdownBlock(it) }
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(value: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Text(value.trimEnd('\n'), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MarkdownTable(node: TableBlock) {
    val rows = remember(node) {
        MarkdownDocument.children(node).flatMap { MarkdownDocument.children(it) }
            .map { MarkdownDocument.children(it).filterIsInstance<TableCell>() }
    }
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        rows.forEachIndexed { rowIndex, cells ->
            Row(Modifier.background(if (rowIndex == 0) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface)) {
                cells.forEach { cell ->
                    InlineMarkdown(cell, MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal),
                        Modifier.width(170.dp).padding(8.dp))
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun InlineMarkdown(node: Node, style: TextStyle, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(node, linkColor) { annotatedInline(node, linkColor) }
    ClickableText(text = text, modifier = modifier, style = style.copy(color = MaterialTheme.colorScheme.onSurface)) {
        text.getStringAnnotations("url", it, it).firstOrNull()?.item?.let { url ->
            try { uriHandler.openUri(url) } catch (_: Exception) { /* No suitable app for this link. */ }
        }
    }
}

private fun annotatedInline(node: Node, linkColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        fun appendNode(current: Node) {
            when (current) {
                is MarkdownText -> append(current.literal)
                is Code -> {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                    append(current.literal)
                    pop()
                }
                is Emphasis -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    MarkdownDocument.children(current).forEach(::appendNode)
                    pop()
                }
                is StrongEmphasis -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    MarkdownDocument.children(current).forEach(::appendNode)
                    pop()
                }
                is Link -> {
                    val safe = MarkdownDocument.safeLink(current.destination)
                    if (safe != null) {
                        pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                        pushStringAnnotation("url", safe)
                    }
                    MarkdownDocument.children(current).forEach(::appendNode)
                    if (safe != null) { pop(); pop() }
                }
                is Image -> MarkdownDocument.children(current).forEach(::appendNode)
                is SoftLineBreak -> append(' ')
                is HardLineBreak -> append('\n')
                is HtmlInline -> append(current.literal)
                else -> MarkdownDocument.children(current).forEach(::appendNode)
            }
        }
        MarkdownDocument.children(node).forEach(::appendNode)
    }
