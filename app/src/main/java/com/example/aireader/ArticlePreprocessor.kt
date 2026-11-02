package com.example.aireader

data class PreprocessOptions(
    val enabled: Boolean = true,
    val paraOnce: Int = 3,
    val detectTitles: Boolean = true,
    val detectNumberedLists: Boolean = true
) {
    init { require(paraOnce in 1..100) { "paraOnce must be between 1 and 100" } }
}

object ArticlePreprocessor {
    private val punctuation = setOf('，', '。', '？', '！', '；', '“', '”', '（', '）',
        ',', '.', '!', '?', ':', ';', '(', ')')
    private val numberedList = Regex(
        "^\\s*(?:[0-9]+[.](?![0-9])|[0-9]+、|[（(][0-9]+[）)]|[A-Za-z][.]|[A-Za-z][)]|[(（][A-Za-z][)）])"
    )

    fun process(article: String, options: PreprocessOptions): String {
        if (!options.enabled) return article

        val output = mutableListOf<String>()
        val body = mutableListOf<String>()

        fun emit(content: String, title: Boolean = false) {
            val number = output.size + 1
            val marker = if (title) "{段落$number}{标题$number}" else "{段落$number}"
            output += "$marker $content"
        }

        fun flushBody() {
            if (body.isNotEmpty()) {
                emit(body.joinToString(""))
                body.clear()
            }
        }

        article.split(Regex("\\r\\n|\\n|\\r")).forEach { line ->
            if (line.isBlank()) return@forEach
            when {
                options.detectNumberedLists && numberedList.containsMatchIn(line) -> {
                    flushBody()
                    emit(line)
                }
                options.detectTitles && line.none { it in punctuation } -> {
                    flushBody()
                    emit(line, title = true)
                }
                else -> {
                    body += line
                    if (body.size == options.paraOnce) flushBody()
                }
            }
        }
        flushBody()
        return output.joinToString("\n")
    }
}
