package me.aireader.app

fun buildUserMessage(prompt: String, processedArticle: String): String =
    prompt.trimEnd() + "\n" + processedArticle.trimStart('\n', '\r')
