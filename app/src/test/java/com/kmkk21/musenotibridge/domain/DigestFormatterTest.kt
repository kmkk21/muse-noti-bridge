package com.kmkk21.musenotibridge.domain

import com.kmkk21.musenotibridge.data.db.NotificationCategory
import com.kmkk21.musenotibridge.data.db.NotificationEntity
import com.kmkk21.musenotibridge.data.db.NotificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestFormatterTest {

    @Test
    fun formatsGroupedCompactDigestAndValidJson() {
        val items = listOf(
            NotificationEntity(
                id = 1,
                packageName = "com.hyundaicard.appcard",
                appName = "현대카드",
                title = "승인",
                content = "14,500원 스타벅스",
                category = NotificationCategory.FINANCE,
                status = NotificationStatus.PENDING,
                receivedAt = 1763700000000L,
                fingerprint = "fp1"
            ),
            NotificationEntity(
                id = 2,
                packageName = "com.nhn.android.search",
                appName = "네이버",
                title = "예약 확정",
                content = "내일 오후 3시 \"강남치과\"",
                category = NotificationCategory.SCHEDULE,
                status = NotificationStatus.PENDING,
                receivedAt = 1763701000000L,
                fingerprint = "fp2"
            )
        )

        val digest = DigestFormatter.format(
            items = items,
            instruction = "가계부와 캘린더를 정리해 주세요.",
            generatedAtEpochMs = 1763702000000L
        )

        assertEquals(2, digest.itemCount)
        assertTrue(digest.subject.contains("가계부 1"))
        assertTrue(digest.subject.contains("일정 1"))
        assertTrue(digest.plainTextBody.contains("■ 가계부/결제 (1건)"))
        assertTrue(digest.plainTextBody.contains("■ 일정/예약 (1건)"))
        assertTrue(digest.estimatedTokens in 30..400)
        assertTrue(digest.jsonPayload.contains("\\\"강남치과\\\""))
        assertTrue(digest.jsonPayload.contains("\"count\":2"))
    }
}
