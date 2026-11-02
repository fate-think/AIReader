package com.example.aireader

fun buildUserMessage(prompt: String, processedArticle: String): String =
    prompt.trimEnd() + "\n\n" + processedArticle
