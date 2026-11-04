package com.example.aireader

import java.util.UUID

/** A history storage failure is separate from the successful reading result. */
fun ReaderState.withHistorySaveFailure(): ReaderState =
    copy(historySaveMessage = "报告已生成，但保存到阅读历史失败。")

/** Immutable request-start snapshot. The UUID is the Room primary key for idempotent completion. */
data class HistoryCapture(
    val requestId: String,
    val originalArticle: String,
    val readingModeId: String,
    val readingModeName: String,
    val modelId: String,
    val apiConfigName: String,
    val options: HistorySaveOptions,
    val createdAt: Long
) {
    fun onEvent(event: ReadingEvent, report: String): ReadingHistoryEntity? =
        if (event == ReadingEvent.Finished) completed(report) else null

    fun completed(report: String): ReadingHistoryEntity? {
        if (!options.autoSave || report.isBlank()) return null
        return ReadingHistoryEntity(requestId, titleFromArticle(originalArticle),
            if (options.saveOriginalArticle) originalArticle else null, report,
            readingModeId, readingModeName, modelId, apiConfigName, createdAt, createdAt)
    }

    companion object {
        fun start(article: String, mode: ReadingMode, modelId: String, apiName: String,
                  options: HistorySaveOptions, now: Long = System.currentTimeMillis()): HistoryCapture =
            HistoryCapture(UUID.randomUUID().toString(), article, mode.id, mode.name, modelId,
                apiName, options, now)

        fun titleFromArticle(article: String): String = article.lineSequence()
            .map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?.take(50) ?: "未命名阅读报告"
    }
}
