package com.kmkk21.musenotibridge.domain

import com.kmkk21.musenotibridge.data.db.NotificationCategory
import com.kmkk21.musenotibridge.data.prefs.AppFilterMode
import com.kmkk21.musenotibridge.data.prefs.BridgeSettings
import java.security.MessageDigest

data class RawNotificationInput(
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val isOngoing: Boolean = false,
    val isGroupSummary: Boolean = false,
    val selfPackageName: String = "com.kmkk21.musenotibridge"
)

sealed class FilterDecision {
    data object DropSilently : FilterDecision()
    data class BlockAd(val matchedKeyword: String, val cleanTitle: String, val cleanContent: String) : FilterDecision()
    data class Ignore(val reason: String, val cleanTitle: String, val cleanContent: String) : FilterDecision()
    data class Accept(
        val cleanTitle: String,
        val cleanContent: String,
        val category: String,
        val isUrgent: Boolean,
        val matchedRule: String,
        val fingerprint: String
    ) : FilterDecision()
}

object NotificationFilterEngine {

    private val ALWAYS_SKIP_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.providers.downloads",
        "com.samsung.android.incallui",
        "com.samsung.android.app.smartcapture",
        "com.google.android.gms",
        "com.facebook.aura" // Meta Muse app itself to prevent infinite echo loop
    )

    private val FINANCE_KEYWORDS = listOf(
        "결제", "승인", "출금", "입금", "이체", "잔액", "카드", "일시불", "할부",
        "자동이체", "송금", "결제취소", "승인취소", "환불", "원 ", "원)", "KRW",
        "토스", "카카오페이", "네이버페이", "삼성페이", "계좌", "납부", "청구"
    )

    private val FINANCE_PACKAGE_HINTS = listOf(
        "card", "bank", "pay", "toss", "kakaobank", "kbstar", "shinhan",
        "woori", "hana", "nh", "hyundaicard", "samsungcard", "lottecard", "bccard"
    )

    private val SCHEDULE_KEYWORDS = listOf(
        "예약", "일정", "미팅", "회의", "마감", "체크인", "체크아웃", "탑승",
        "예매", "진료", "방문", "수업", "면접", "약속", "리마인드", "캘린더",
        "배송", "도착 예정", "출발", "만료 예정", "갱신 예정"
    )

    fun evaluate(input: RawNotificationInput, settings: BridgeSettings): FilterDecision {
        if (!settings.masterEnabled) return FilterDecision.DropSilently
        if (input.isOngoing || input.isGroupSummary) return FilterDecision.DropSilently
        if (input.packageName == input.selfPackageName) return FilterDecision.DropSilently
        if (input.packageName in ALWAYS_SKIP_PACKAGES) return FilterDecision.DropSilently

        val cleanTitle = normalizeWhitespace(input.title).take(80)
        val cleanContent = normalizeWhitespace(input.content)
        if (cleanTitle.isEmpty() && cleanContent.isEmpty()) {
            return FilterDecision.DropSilently
        }

        val combinedLower = "$cleanTitle $cleanContent".lowercase()

        // 1. Ad detection runs across ALL apps when enabled so ad notifications are deleted immediately
        if (settings.adBlockEnabled) {
            val matchedAd = settings.adKeywords
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .firstOrNull { keyword -> combinedLower.contains(keyword.lowercase()) }
            if (matchedAd != null) {
                return FilterDecision.BlockAd(
                    matchedKeyword = matchedAd,
                    cleanTitle = cleanTitle,
                    cleanContent = truncate(cleanContent, settings.maxContentChars)
                )
            }
        }

        // 2. App allowlist / blocklist check
        val appAllowed = when (settings.appFilterMode) {
            AppFilterMode.EXCLUDE_SELECTED -> input.packageName !in settings.selectedPackages
            AppFilterMode.INCLUDE_ONLY ->
                settings.selectedPackages.isEmpty() || input.packageName in settings.selectedPackages
        }
        if (!appAllowed) {
            return FilterDecision.DropSilently
        }

        // 3. Noise / sensitive OTP ignore filter
        val matchedIgnore = settings.ignoreKeywords
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .firstOrNull { keyword -> combinedLower.contains(keyword.lowercase()) }
        if (matchedIgnore != null) {
            return FilterDecision.Ignore(
                reason = matchedIgnore,
                cleanTitle = cleanTitle,
                cleanContent = truncate(cleanContent, settings.maxContentChars)
            )
        }

        val truncatedContent = truncate(cleanContent, settings.maxContentChars)
        val category = categorize(input.packageName, input.appName, combinedLower)
        val matchedUrgent = settings.urgentKeywords
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .firstOrNull { keyword -> combinedLower.contains(keyword.lowercase()) }

        val fingerprint = sha256("${input.packageName}|$cleanTitle|$truncatedContent")

        return FilterDecision.Accept(
            cleanTitle = cleanTitle,
            cleanContent = truncatedContent,
            category = category,
            isUrgent = matchedUrgent != null,
            matchedRule = matchedUrgent ?: "",
            fingerprint = fingerprint
        )
    }

    fun categorize(packageName: String, appName: String, combinedLower: String): String {
        val pkgLower = packageName.lowercase()
        val appLower = appName.lowercase()

        val hasAmountPattern = Regex("""\d{1,3}(,\d{3})+\s*원|\d+\s*원""").containsMatchIn(combinedLower)
        val hasFinanceKeyword = FINANCE_KEYWORDS.any { combinedLower.contains(it.lowercase()) }
        val isFinancePackage = FINANCE_PACKAGE_HINTS.any { pkgLower.contains(it) || appLower.contains(it) }

        if ((hasFinanceKeyword && hasAmountPattern) || (isFinancePackage && hasFinanceKeyword)) {
            return NotificationCategory.FINANCE
        }

        val hasScheduleKeyword = SCHEDULE_KEYWORDS.any { combinedLower.contains(it.lowercase()) }
        if (hasScheduleKeyword) {
            return NotificationCategory.SCHEDULE
        }

        if (hasFinanceKeyword) {
            return NotificationCategory.FINANCE
        }

        return NotificationCategory.GENERAL
    }

    fun normalizeWhitespace(raw: String): String {
        return raw.replace(Regex("""\s+"""), " ").trim()
    }

    fun truncate(text: String, maxChars: Int): String {
        val limit = maxChars.coerceAtLeast(40)
        return if (text.length <= limit) text else text.take(limit - 1).trimEnd() + "…"
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }
}
