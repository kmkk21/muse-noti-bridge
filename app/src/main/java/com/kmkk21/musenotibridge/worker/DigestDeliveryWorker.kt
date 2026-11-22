package com.kmkk21.musenotibridge.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kmkk21.musenotibridge.delivery.DeliveryCoordinator

class DigestDeliveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val coordinator = DeliveryCoordinator(applicationContext)
        val outcome = coordinator.deliverPendingBatch()
        return if (outcome.success) {
            Result.success()
        } else {
            // If no delivery channel is configured, retrying immediately doesn't help
            if (outcome.summaryMessage.contains("아직 설정되지")) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }
}
