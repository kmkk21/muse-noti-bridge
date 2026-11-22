package com.kmkk21.musenotibridge.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object NotificationStatus {
    const val PENDING = "PENDING"
    const val SENT = "SENT"
    const val AD_BLOCKED = "AD_BLOCKED"
    const val IGNORED = "IGNORED"
    const val FAILED = "FAILED"
}

object NotificationCategory {
    const val FINANCE = "FINANCE"
    const val SCHEDULE = "SCHEDULE"
    const val GENERAL = "GENERAL"
    const val AD = "AD"
    const val IGNORED = "IGNORED"

    fun displayLabel(category: String): String = when (category) {
        FINANCE -> "가계부/결제"
        SCHEDULE -> "일정/예약"
        GENERAL -> "일반/중요"
        AD -> "광고차단"
        IGNORED -> "무시됨"
        else -> "기타"
    }
}

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["status"]),
        Index(value = ["receivedAt"]),
        Index(value = ["fingerprint", "receivedAt"])
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val category: String,
    val status: String,
    val matchedRule: String = "",
    val receivedAt: Long,
    val sentAt: Long? = null,
    val fingerprint: String
)
