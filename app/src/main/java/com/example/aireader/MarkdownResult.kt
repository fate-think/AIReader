package com.example.aireader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

@Composable
fun MarkdownResult(content: String) {
    var inCodeBlock = false
    Column(modifier = Modifier.fillMaxWidth()) {
        content.lines().forEach { line ->
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock
            } else {
                val heading = line.takeWhile { it == '#' }.length
                    .takeIf { it in 1..6 && line.getOrNull(it) == ' ' }
                val shown = if (heading != null) line.drop(heading + 1) else line
                val style = when {
                    heading == 1 -> MaterialTheme.typography.headlineSmall
                    heading == 2 -> MaterialTheme.typography.titleLarge
                    heading != null -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.bodyMedium
                }
                Text(
                    text = if (inCodeBlock) AnnotatedString(shown) else inlineMarkdown(shown),
                    style = style,
                    fontFamily = if (inCodeBlock) FontFamily.Monospace else null
                )
            }
        }
    }
}

private fun inlineMarkdown(line: String) = buildAnnotatedString {
    var cursor = 0
    while (cursor < line.length) {
        val start = line.indexOf("**", cursor)
        if (start < 0) { append(line.substring(cursor)); break }
        val end = line.indexOf("**", start + 2)
        if (end < 0) { append(line.substring(cursor)); break }
        append(line.substring(cursor, start))
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(line.substring(start + 2, end))
        pop()
        cursor = end + 2
    }
}
