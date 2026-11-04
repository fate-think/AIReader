package com.example.aireader

fun buildUserMessage(prompt: String, processedArticle: String): String =
    prompt.trimEnd() + "\n" + processedArticle.trimStart('\n', '\r')
