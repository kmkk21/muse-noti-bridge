package com.kmkk21.musenotibridge.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DeliveryScheduler {

    private const val PERIODIC_WORK_NAME = "muse_bridge_periodic_digest"
    private const val SHORT_INTERVAL_WORK_NAME = "muse_bridge_short_interval_digest"
    private const val IMMEDIATE_WORK_NAME = "muse_bridge_immediate_digest"

    private val connectedConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(context: Context, intervalMinutes: Int) {
        val wm = WorkManager.getInstance(context.applicationContext)
        val clampedMinutes = intervalMinutes.coerceAtLeast(5)

        if (clampedMinutes < 15) {
            // Android PeriodicWorkRequest enforces a 15-min floor, so use a self-chaining
            // OneTimeWorkRequest for 5~10 min intervals plus a 15-min periodic safety net.
            scheduleNextShortInterval(context, clampedMinutes, ExistingWorkPolicy.REPLACE)
            val fallback = PeriodicWorkRequestBuilder<DigestDeliveryWorker>(15, TimeUnit.MINUTES)
                .setConstraints(connectedConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                fallback
            )
        } else {
            wm.cancelUniqueWork(SHORT_INTERVAL_WORK_NAME)
            val request = PeriodicWorkRequestBuilder<DigestDeliveryWorker>(
                clampedMinutes.toLong(),
                TimeUnit.MINUTES
            )
                .setConstraints(connectedConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            wm.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    fun scheduleNextShortInterval(
        context: Context,
        intervalMinutes: Int,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
    ) {
        val safeMinutes = intervalMinutes.coerceIn(5, 14).toLong()
        val request = OneTimeWorkRequestBuilder<DigestDeliveryWorker>()
            .setInitialDelay(safeMinutes, TimeUnit.MINUTES)
            .setConstraints(connectedConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            SHORT_INTERVAL_WORK_NAME,
            policy,
            request
        )
    }

    fun triggerImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<DigestDeliveryWorker>()
            .setConstraints(connectedConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelPeriodic(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(PERIODIC_WORK_NAME)
        wm.cancelUniqueWork(SHORT_INTERVAL_WORK_NAME)
    }
}
