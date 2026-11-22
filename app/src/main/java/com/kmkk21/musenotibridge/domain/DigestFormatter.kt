package com.kmkk21.musenotibridge.domain

import com.kmkk21.musenotibridge.data.db.NotificationCategory
import com.kmkk21.musenotibridge.data.db.NotificationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class FormattedDigest(
    val subject: String,
    val plainTextBody: String,
    val jsonPayload: String,
    val itemCount: Int,
    val estimatedTokens: Int
)

object DigestFormatter {

    fun format(
        items: List<NotificationEntity>,
        instruction: String,
        generatedAtEpochMs: Long = System.currentTimeMillis(),
        timeZoneId: String = "Asia/Seoul"
    ): FormattedDigest {
        val tz = TimeZone.getTimeZone(timeZoneId)
        val headerFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).apply { timeZone = tz }
        val subjectFmt = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).apply { timeZone = tz }
        val timeFmt = SimpleDateFormat("HH:mm", Locale.KOREA).apply { timeZone = tz }

        val financeItems = items.filter { it.category == NotificationCategory.FINANCE }
        val scheduleItems = items.filter { it.category == NotificationCategory.SCHEDULE }
        val generalItems = items.filter {
            it.category != NotificationCategory.FINANCE && it.category != NotificationCategory.SCHEDULE
        }

        val summaryParts = buildList {
            if (financeItems.isNotEmpty()) add("가계부 ${financeItems.size}")
            if (scheduleItems.isNotEmpty()) add("일정 ${scheduleItems.size}")
            if (generalItems.isNotEmpty()) add("기타 ${generalItems.size}")
        }.joinToString(" · ")

        val subject = "[Muse 알림 요약] ${subjectFmt.format(Date(generatedAtEpochMs))} (${items.size}건: $summaryParts)"

        val sb = StringBuilder()
        sb.appendLine("[Muse 알림 브릿지 요약] ${headerFmt.format(Date(generatedAtEpochMs))} (총 ${items.size}건)")
        if (instruction.isNotBlank()) {
            sb.appendLine()
            sb.appendLine("지시사항: ${instruction.trim()}")
        }

        fun appendSection(title: String, list: List<NotificationEntity>) {
            if (list.isEmpty()) return
            sb.appendLine()
            sb.appendLine("■ $title (${list.size}건)")
            list.forEach { item ->
                val t = timeFmt.format(Date(item.receivedAt))
                val urgentBadge = if (item.matchedRule.isNotBlank()) "[긴급:${item.matchedRule}] " else ""
                val titlePart = if (item.title.isNotBlank()) "${item.title}: " else ""
                sb.appendLine("- [$t] ${item.appName} | $urgentBadge$titlePart${item.content}")
            }
        }

        appendSection("가계부/결제", financeItems)
        appendSection("일정/예약", scheduleItems)
        appendSection("일반/중요 알림", generalItems)

        val plainText = sb.toString().trim()
        val estimatedTokens = estimateTokens(plainText)
        val jsonPayload = buildJsonPayload(
            generatedAt = headerFmt.format(Date(generatedAtEpochMs)),
            subject = subject,
            instruction = instruction,
            digestText = plainText,
            items = items,
            timeFmt = timeFmt
        )

        return FormattedDigest(
            subject = subject,
            plainTextBody = plainText,
            jsonPayload = jsonPayload,
            itemCount = items.size,
            estimatedTokens = estimatedTokens
        )
    }

    /**
     * Conservative token estimator for Korean + English mixed text.
     * Korean characters typically average ~1.5 chars per token, ASCII ~4 chars per token.
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var nonAscii = 0
        var ascii = 0
        for (ch in text) {
            if (ch.code > 127) nonAscii++ else ascii++
        }
        return ((nonAscii / 1.4) + (ascii / 4.0)).toInt().coerceAtLeast(1)
    }

    private fun buildJsonPayload(
        generatedAt: String,
        subject: String,
        instruction: String,
        digestText: String,
        items: List<NotificationEntity>,
        timeFmt: SimpleDateFormat
    ): String {
        val itemsJson = items.joinToString(",") { item ->
            """{"time":"${escapeJson(timeFmt.format(Date(item.receivedAt)))}","app":"${escapeJson(item.appName)}","package":"${escapeJson(item.packageName)}","category":"${escapeJson(item.category)}","title":"${escapeJson(item.title)}","content":"${escapeJson(item.content)}"}"""
        }
        return """{"source":"MuseNotiBridge","generatedAt":"${escapeJson(generatedAt)}","count":${items.size},"subject":"${escapeJson(subject)}","instruction":"${escapeJson(instruction)}","digestText":"${escapeJson(digestText)}","items":[$itemsJson]}"""
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length + 16)
        for (c in value) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) {
                    sb.append("\\u%04x".format(c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        return sb.toString()
    }
}
