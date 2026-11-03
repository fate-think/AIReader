package com.example.aireader

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.Link
import org.commonmark.node.OrderedList
import org.commonmark.node.StrongEmphasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownDocumentTest {
    @Test fun headingsAndInlineFormattingAreParsed() {
        val source = (1..6).joinToString("\n\n") { "${"#".repeat(it)} 标题$it" } +
            "\n\n**加粗**、*斜体*与[链接](https://example.com/path)"
        val blocks = MarkdownDocument.blocks(source)
        assertEquals((1..6).toList(), blocks.filterIsInstance<Heading>().map { it.level })
        val inline = MarkdownDocument.children(blocks.last())
        assertTrue(inline.any { it is StrongEmphasis })
        assertTrue(inline.any { it is Emphasis })
        assertTrue(inline.any { it is Link })
    }

    @Test fun nestedListsQuoteCodeAndTableAreRecognized() {
        val source = """
            1. 第一项
               - 子项
            2. 第二项

            > 引用内容

            ```kotlin
            println("hello")
            ```

            | 列一 | 列二 |
            | --- | --- |
            | 中文 | English |
        """.trimIndent()
        val blocks = MarkdownDocument.blocks(source)
        val list = blocks.filterIsInstance<OrderedList>().single()
        assertEquals(1, MarkdownDocument.orderedNumber(list, 0))
        fun containsBullet(node: org.commonmark.node.Node): Boolean =
            node is BulletList || MarkdownDocument.children(node).any { containsBullet(it) }
        assertTrue(containsBullet(list))
        assertTrue(blocks.any { it is BlockQuote })
        assertTrue(blocks.any { it is FencedCodeBlock })
        assertTrue(blocks.any { it is TableBlock })
    }

    @Test fun orderedListKeepsItsMarkdownStartNumber() {
        val list = MarkdownDocument.blocks("5. 五\n6. 六").filterIsInstance<OrderedList>().single()
        assertEquals(5, MarkdownDocument.orderedNumber(list, 0))
        assertEquals(6, MarkdownDocument.orderedNumber(list, 1))
    }

    @Test fun linksRequireAUserClickableSafeScheme() {
        assertEquals("https://example.com/path", MarkdownDocument.safeLink("https://example.com/path"))
        assertNull(MarkdownDocument.safeLink("javascript:alert(1)"))
        assertNull(MarkdownDocument.safeLink("file:///private/data"))
        assertNull(MarkdownDocument.safeLink("https://"))
    }

    @Test fun longStreamingTextIsReassembledExactlyWithoutSplittingEmoji() {
        val text = "中文🙂English\n".repeat(500)
        val chunks = MarkdownDocument.streamingChunks(text, 101)
        assertTrue(chunks.size > 1)
        assertEquals(text, chunks.joinToString(""))
        assertFalse(chunks.any { it.isNotEmpty() && Character.isHighSurrogate(it.last()) })
    }
}
