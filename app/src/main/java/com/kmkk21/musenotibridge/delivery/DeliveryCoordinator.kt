package com.kmkk21.musenotibridge.delivery

import android.content.Context
import com.kmkk21.musenotibridge.data.db.AppDatabase
import com.kmkk21.musenotibridge.data.db.DeliveryLogEntity
import com.kmkk21.musenotibridge.data.db.NotificationStatus
import com.kmkk21.musenotibridge.data.prefs.BridgeSettings
import com.kmkk21.musenotibridge.data.prefs.SettingsRepository
import com.kmkk21.musenotibridge.domain.DigestFormatter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

data class DeliveryOutcome(
    val success: Boolean,
    val sentCount: Int,
    val estimatedTokens: Int,
    val summaryMessage: String
)

class DeliveryCoordinator(context: Context) {

    private val dao = AppDatabase.getInstance(context).notificationDao()
    private val settingsRepo = SettingsRepository(context)

    suspend fun deliverPendingBatch(): DeliveryOutcome = deliveryMutex.withLock {
        val settings = settingsRepo.getSettings()
        if (!settings.hasAnyDeliveryChannel) {
            return@withLock DeliveryOutcome(
                success = false,
                sentCount = 0,
                estimatedTokens = 0,
                summaryMessage = "전송 채널(Muse Mail 또는 Webhook)이 아직 설정되지 않았습니다."
            )
        }

        val pending = dao.getPendingForDelivery()
        if (pending.isEmpty()) {
            pruneOldRecords()
            return@withLock DeliveryOutcome(
                success = true,
                sentCount = 0,
                estimatedTokens = 0,
                summaryMessage = "대기 중인 알림이 없습니다."
            )
        }

        val now = System.currentTimeMillis()
        val digest = DigestFormatter.format(
            items = pending,
            instruction = settings.museInstruction,
            generatedAtEpochMs = now
        )

        val channelResults = mutableListOf<Pair<String, Result<String>>>()

        if (settings.isEmailConfigured) {
            val res = SmtpSender.sendEmail(
                settings = settings,
                subject = digest.subject,
                body = digest.plainTextBody
            ).map { "이메일 전송 완료" }
            channelResults.add("Muse Mail" to res)
        }

        if (settings.isWebhookConfigured) {
            val res = WebhookSender.postDigest(
                settings = settings,
                jsonPayload = digest.jsonPayload
            )
            channelResults.add("Webhook" to res)
        }

        val anySuccess = channelResults.any { it.second.isSuccess }
        val allSuccess = channelResults.all { it.second.isSuccess }
        val channelNames = channelResults.joinToString(" + ") { it.first }
        val detailMessage = channelResults.joinToString(" / ") { (name, res) ->
            if (res.isSuccess) "$name 성공" else "$name 실패(${res.exceptionOrNull()?.message ?: "오류"})"
        }

        val ids = pending.map { it.id }
        if (anySuccess) {
            dao.updateStatus(ids, NotificationStatus.SENT, now)
        } else {
            dao.updateStatus(ids, NotificationStatus.FAILED, null)
        }

        dao.insertDeliveryLog(
            DeliveryLogEntity(
                deliveredAt = now,
                channel = channelNames,
                itemCount = pending.size,
                estimatedTokens = digest.estimatedTokens,
                success = allSuccess,
                message = detailMessage
            )
        )

        pruneOldRecords()

        DeliveryOutcome(
            success = anySuccess,
            sentCount = if (anySuccess) pending.size else 0,
            estimatedTokens = digest.estimatedTokens,
            summaryMessage = if (anySuccess) {
                "${pending.size}건 전송 완료 (약 ${digest.estimatedTokens} 토큰) · $detailMessage"
            } else {
                "전송 실패 (자동 재시도 예정) · $detailMessage"
            }
        )
    }

    suspend fun sendTestMessage(settings: BridgeSettings): DeliveryOutcome {
        if (!settings.hasAnyDeliveryChannel) {
            return DeliveryOutcome(
                success = false,
                sentCount = 0,
                estimatedTokens = 0,
                summaryMessage = "먼저 이메일 또는 Webhook 설정을 입력해 주세요."
            )
        }

        val testSubject = "[Muse 알림 브릿지] 연결 테스트 메시지"
        val testBody = """
            Muse 알림 브릿지가 정상적으로 연결되었습니다!
            이제 핸드폰 광고 알림은 로컬에서 즉시 삭제되고, 중요한 결제·일정 알림만 묶어서 이 주소로 안전하게 전달됩니다.
        """.trimIndent()
        val testJson = """{"source":"MuseNotiBridge","test":true,"message":"연결 테스트 성공"}"""

        val results = mutableListOf<Pair<String, Result<String>>>()
        if (settings.isEmailConfigured) {
            val res = SmtpSender.sendEmail(settings, testSubject, testBody).map { "성공" }
            results.add("Muse Mail" to res)
        }
        if (settings.isWebhookConfigured) {
            val res = WebhookSender.postDigest(settings, testJson)
            results.add("Webhook" to res)
        }

        val allOk = results.all { it.second.isSuccess }
        val msg = results.joinToString(" / ") { (name, res) ->
            if (res.isSuccess) "$name 연결 성공" else "$name 실패: ${res.exceptionOrNull()?.message ?: "알 수 없는 오류"}"
        }
        return DeliveryOutcome(
            success = allOk,
            sentCount = if (allOk) 1 else 0,
            estimatedTokens = DigestFormatter.estimateTokens(testBody),
            summaryMessage = msg
        )
    }

    private suspend fun pruneOldRecords() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        dao.pruneOld(cutoff)
        dao.pruneOldDeliveryLogs(cutoff)
    }

    companion object {
        private val deliveryMutex = Mutex()
    }
}
