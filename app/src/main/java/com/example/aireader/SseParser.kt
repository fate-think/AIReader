package com.example.aireader

import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader

object SseParser {
    fun events(reader: BufferedReader): Sequence<ReadingEvent> = sequence {
        val data = StringBuilder()
        var errorEvent = false
        var finished = false

        fun parseData(value: String): List<ReadingEvent> {
            if (errorEvent) throw ApiException("服务端报告请求失败，请检查配置。")
            if (value == "[DONE]") return listOf(ReadingEvent.Finished)
            val json = try { JSONObject(value) }
            catch (_: JSONException) { throw ApiException("流式响应格式无效。") }
            if (json.has("error")) throw ApiException("服务端报告请求失败，请检查配置。")
            val choices = try { json.getJSONArray("choices") }
            catch (_: JSONException) { throw ApiException("流式响应格式无效。") }
            if (choices.length() == 0) return emptyList()
            val choice = try { choices.getJSONObject(0) }
            catch (_: JSONException) { throw ApiException("流式响应格式无效。") }
            val result = mutableListOf<ReadingEvent>()
            val content = choice.optJSONObject("delta")?.optString("content", "") ?: ""
            if (content.isNotEmpty() && content != "null") result += ReadingEvent.Text(content)
            if (!choice.isNull("finish_reason") && choice.optString("finish_reason").isNotEmpty()) {
                result += ReadingEvent.Finished
            }
            return result
        }

        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) {
                if (data.isNotEmpty() || errorEvent) {
                    val events = parseData(data.toString())
                    for (event in events) {
                        yield(event)
                        if (event == ReadingEvent.Finished) finished = true
                    }
                    data.clear()
                    errorEvent = false
                    if (finished) return@sequence
                }
                continue
            }
            if (line.startsWith(":")) continue
            if (line.startsWith("event:")) {
                errorEvent = line.substringAfter(':').trim() == "error"
            } else if (line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.substringAfter(':').removePrefix(" "))
            }
        }
        if (data.isNotEmpty() || errorEvent) {
            for (event in parseData(data.toString())) {
                yield(event)
                if (event == ReadingEvent.Finished) finished = true
            }
        }
        if (!finished) throw ApiException("连接中断，生成未正常结束。")
    }
}
