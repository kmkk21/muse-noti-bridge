package com.kmkk21.musenotibridge.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.kmkk21.musenotibridge.data.prefs.SettingsRepository
import com.kmkk21.musenotibridge.delivery.DeliveryCoordinator

class DigestDeliveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val coordinator = DeliveryCoordinator(applicationContext)
        val outcome = coordinator.deliverPendingBatch()

        // Re-arm short-interval chain (5~10 min) if still enabled
        val settings = SettingsRepository(applicationContext).getSettings()
        if (settings.masterEnabled && settings.batchIntervalMinutes < 15) {
            DeliveryScheduler.scheduleNextShortInterval(
                applicationContext,
                settings.batchIntervalMinutes,
                ExistingWorkPolicy.REPLACE
            )
        }

        return if (outcome.success) {
            Result.success()
        } else {
            if (outcome.summaryMessage.contains("아직 설정되지")) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }
}
