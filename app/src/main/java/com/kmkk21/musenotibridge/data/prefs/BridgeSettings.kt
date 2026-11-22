package com.kmkk21.musenotibridge.data.prefs

enum class AppFilterMode {
    EXCLUDE_SELECTED,
    INCLUDE_ONLY
}

enum class SmtpPreset(
    val displayName: String,
    val host: String,
    val port: Int,
    val useSsl: Boolean
) {
    GMAIL("Gmail (앱 비밀번호)", "smtp.gmail.com", 465, true),
    NAVER("네이버 메일", "smtp.naver.com", 465, true),
    OUTLOOK("Outlook / Hotmail", "smtp.office365.com", 587, false),
    CUSTOM("직접 입력", "", 465, true)
}

data class BridgeSettings(
    val masterEnabled: Boolean = true,
    val adBlockEnabled: Boolean = true,
    val keepAliveEnabled: Boolean = true,
    val appFilterMode: AppFilterMode = AppFilterMode.EXCLUDE_SELECTED,
    val selectedPackages: Set<String> = emptySet(),
    val adKeywords: Set<String> = DEFAULT_AD_KEYWORDS,
    val ignoreKeywords: Set<String> = DEFAULT_IGNORE_KEYWORDS,
    val urgentKeywords: Set<String> = DEFAULT_URGENT_KEYWORDS,
    val maxContentChars: Int = 160,
    val batchIntervalMinutes: Int = 120,
    val urgentInstantSend: Boolean = true,
    val emailEnabled: Boolean = true,
    val smtpPreset: SmtpPreset = SmtpPreset.GMAIL,
    val smtpHost: String = SmtpPreset.GMAIL.host,
    val smtpPort: Int = SmtpPreset.GMAIL.port,
    val smtpUseSsl: Boolean = SmtpPreset.GMAIL.useSsl,
    val smtpUsername: String = "",
    val smtpPassword: String = "",
    val recipientEmail: String = "",
    val webhookEnabled: Boolean = false,
    val webhookUrl: String = "",
    val webhookAuthHeader: String = "",
    val museInstruction: String = DEFAULT_MUSE_INSTRUCTION
) {
    val isEmailConfigured: Boolean
        get() = emailEnabled &&
            smtpHost.isNotBlank() &&
            smtpUsername.isNotBlank() &&
            smtpPassword.isNotBlank() &&
            recipientEmail.isNotBlank()

    val isWebhookConfigured: Boolean
        get() = webhookEnabled && webhookUrl.isNotBlank()

    val hasAnyDeliveryChannel: Boolean
        get() = isEmailConfigured || isWebhookConfigured

    companion object {
        val DEFAULT_AD_KEYWORDS = setOf(
            "(광고)",
            "[광고]",
            "광고)",
            "특가",
            "쿠폰",
            "할인",
            "프로모션",
            "이벤트",
            "혜택",
            "세일",
            "무료증정",
            "출석체크",
            "적립금",
            "포인트 소멸",
            "최대 50%",
            "최대 70%",
            "타임딜",
            "선착순"
        )

        val DEFAULT_IGNORE_KEYWORDS = setOf(
            "인증번호",
            "보안코드",
            "본인확인",
            "OTP",
            "verification code",
            "로그인 알림",
            "새로운 기기에서 로그인"
        )

        val DEFAULT_URGENT_KEYWORDS = setOf(
            "해외결제",
            "부정사용",
            "연체",
            "긴급"
        )

        val DEFAULT_MUSE_INSTRUCTION =
            "아래는 안드로이드 폰에서 광고·중복·인증번호를 자동 제거하고 정리한 알림 요약입니다. " +
                "1) 결제·입출금 내역은 날짜/가맹점/금액/카테고리로 가계부에 기록하고, " +
                "2) 예약·약속·마감 일정은 캘린더에 등록하고, " +
                "3) 놓치면 안 되는 중요 사항은 리마인드해 주세요."
    }
}
