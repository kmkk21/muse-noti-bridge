package com.kmkk21.musenotibridge.domain

import com.kmkk21.musenotibridge.data.db.NotificationCategory
import com.kmkk21.musenotibridge.data.prefs.AppFilterMode
import com.kmkk21.musenotibridge.data.prefs.BridgeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFilterEngineTest {

    private val defaultSettings = BridgeSettings()

    @Test
    fun blocksAdNotificationAndExtractsMatchedKeyword() {
        val input = RawNotificationInput(
            packageName = "com.coupang.mobile",
            appName = "쿠팡",
            title = "(광고) 오늘만 로켓 특가!",
            content = "지금 바로 쿠폰 받고 50% 할인 받으세요"
        )
        val decision = NotificationFilterEngine.evaluate(input, defaultSettings)
        assertTrue(decision is FilterDecision.BlockAd)
        val ad = decision as FilterDecision.BlockAd
        assertTrue(ad.matchedKeyword.isNotBlank())
    }

    @Test
    fun ignoresOtpAndVerificationCodeNotifications() {
        val input = RawNotificationInput(
            packageName = "com.samsung.android.messaging",
            appName = "메시지",
            title = "카카오",
            content = "[카카오] 본인확인 인증번호 [482910]을 입력해 주세요."
        )
        val decision = NotificationFilterEngine.evaluate(input, defaultSettings)
        assertTrue(decision is FilterDecision.Ignore)
    }

    @Test
    fun categorizesKoreanCardPaymentAsFinanceAndTruncatesLongContent() {
        val longSuffix = "가".repeat(300)
        val input = RawNotificationInput(
            packageName = "com.hyundaicard.appcard",
            appName = "현대카드",
            title = "결제 승인",
            content = "현대카드 승인 14,500원 일시불 스타벅스 강남역점 $longSuffix"
        )
        val settings = defaultSettings.copy(maxContentChars = 100)
        val decision = NotificationFilterEngine.evaluate(input, settings)

        assertTrue(decision is FilterDecision.Accept)
        val accepted = decision as FilterDecision.Accept
        assertEquals(NotificationCategory.FINANCE, accepted.category)
        assertTrue(accepted.cleanContent.length <= 100)
        assertTrue(accepted.cleanContent.endsWith("…"))
        assertFalse(accepted.isUrgent)
    }

    @Test
    fun categorizesReservationAsSchedule() {
        val input = RawNotificationInput(
            packageName = "com.nhn.android.search",
            appName = "네이버",
            title = "네이버 예약 확정",
            content = "내일 오후 3시 강남치과 예약이 확정되었습니다."
        )
        val decision = NotificationFilterEngine.evaluate(input, defaultSettings)
        assertTrue(decision is FilterDecision.Accept)
        assertEquals(NotificationCategory.SCHEDULE, (decision as FilterDecision.Accept).category)
    }

    @Test
    fun marksForeignPaymentAsUrgent() {
        val input = RawNotificationInput(
            packageName = "com.shcard.smartpay",
            appName = "신한 SOL페이",
            title = "해외결제 승인 알림",
            content = "해외결제 승인 USD 49.99 OPENAI"
        )
        val decision = NotificationFilterEngine.evaluate(input, defaultSettings)
        assertTrue(decision is FilterDecision.Accept)
        val accepted = decision as FilterDecision.Accept
        assertTrue(accepted.isUrgent)
        assertEquals("해외결제", accepted.matchedRule)
    }

    @Test
    fun respectsIncludeOnlyAppFilterMode() {
        val settings = defaultSettings.copy(
            appFilterMode = AppFilterMode.INCLUDE_ONLY,
            selectedPackages = setOf("viva.republica.toss")
        )
        val allowed = NotificationFilterEngine.evaluate(
            RawNotificationInput("viva.republica.toss", "토스", "입금", "10,000원 입금되었습니다"),
            settings
        )
        val skipped = NotificationFilterEngine.evaluate(
            RawNotificationInput("com.example.game", "게임", "하트", "하트가 가득 찼습니다"),
            settings
        )
        assertTrue(allowed is FilterDecision.Accept)
        assertTrue(skipped is FilterDecision.DropSilently)
    }

    @Test
    fun skipsMetaMuseAppItselfToPreventEchoLoop() {
        val input = RawNotificationInput(
            packageName = "com.facebook.aura",
            appName = "Muse",
            title = "Muse",
            content = "일정을 등록했습니다."
        )
        val decision = NotificationFilterEngine.evaluate(input, defaultSettings)
        assertTrue(decision is FilterDecision.DropSilently)
    }
}
