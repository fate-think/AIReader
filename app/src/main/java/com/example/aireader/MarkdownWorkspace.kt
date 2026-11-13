package com.example.aireader

enum class MarkdownSource { NONE, AI, MANUAL, EDITED_AI }

enum class MarkdownViewMode { EDIT, PREVIEW }

/** Rules shared by the editor and request entry points; the text itself lives in ReaderState.result. */
object MarkdownWorkspace {
    fun sourceAfterEdit(previous: MarkdownSource, text: String): MarkdownSource = when {
        text.isEmpty() -> MarkdownSource.NONE
        previous == MarkdownSource.AI || previous == MarkdownSource.EDITED_AI -> MarkdownSource.EDITED_AI
        else -> MarkdownSource.MANUAL
    }

    fun requiresOverwriteConfirmation(text: String, source: MarkdownSource): Boolean =
        text.isNotEmpty() && source in setOf(MarkdownSource.MANUAL, MarkdownSource.EDITED_AI)

    fun canUseHtml(text: String, generating: Boolean): Boolean = text.isNotBlank() && !generating
}

fun ReaderState.withEditedMarkdown(text: String): ReaderState = copy(
    result = text,
    markdownSource = MarkdownWorkspace.sourceAfterEdit(markdownSource, text),
    markdownViewMode = MarkdownViewMode.EDIT,
    status = ReadingStatus.Idle,
    readingError = null,
    retryMessage = null,
    resultActionMessage = null,
    historySaveMessage = null
)

fun ReaderState.withMarkdownGenerationStarted(): ReaderState = copy(
    result = "",
    markdownSource = MarkdownSource.AI,
    markdownViewMode = MarkdownViewMode.PREVIEW,
    status = ReadingStatus.Loading,
    readingError = null,
    retryMessage = null,
    resultActionMessage = null,
    historySaveMessage = null
)

fun ReaderState.withGeneratedMarkdown(text: String, nextStatus: ReadingStatus): ReaderState {
    val terminalStatus = when (nextStatus) {
        ReadingStatus.Completed, ReadingStatus.Cancelled, ReadingStatus.Interrupted,
        ReadingStatus.RetryExhausted, ReadingStatus.Error -> true
        else -> false
    }
    val emptyTerminalResult = text.isEmpty() && terminalStatus
    return copy(result = text,
        markdownSource = if (emptyTerminalResult) MarkdownSource.NONE else MarkdownSource.AI,
        markdownViewMode = if (emptyTerminalResult) MarkdownViewMode.EDIT else MarkdownViewMode.PREVIEW,
        status = nextStatus, retryMessage = null)
}

fun ReaderState.withClearedMarkdown(): ReaderState = copy(
    result = "",
    markdownSource = MarkdownSource.NONE,
    markdownViewMode = MarkdownViewMode.EDIT,
    status = ReadingStatus.Idle,
    readingError = null,
    retryMessage = null,
    resultActionMessage = null,
    historySaveMessage = null
)
