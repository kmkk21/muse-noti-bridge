package com.kmkk21.musenotibridge.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kmkk21.musenotibridge.MainActivity
import com.kmkk21.musenotibridge.R
import com.kmkk21.musenotibridge.data.prefs.SettingsRepository
import com.kmkk21.musenotibridge.delivery.DeliveryCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BridgeKeepAliveService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var shortIntervalLoopJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()

        val openIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle("Muse 알림 브릿지 작동 중")
            .setContentText("광고 알림 자동 차단 · 중요 알림 안전 수집 중")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        ensureShortIntervalTicker()

        return START_STICKY
    }

    /**
     * When the user picks a 5-min or 10-min interval, Android's PeriodicWorkRequest is capped at
     * 15 minutes and Doze can defer OneTimeWork. This lightweight foreground ticker ensures timely
     * delivery while DeliveryCoordinator's Mutex prevents any duplicate sends.
     */
    private fun ensureShortIntervalTicker() {
        if (shortIntervalLoopJob?.isActive == true) return
        val settingsRepo = SettingsRepository(applicationContext)
        val coordinator = DeliveryCoordinator(applicationContext)

        shortIntervalLoopJob = serviceScope.launch {
            while (isActive) {
                val cfg = settingsRepo.getSettings()
                val intervalMs = (cfg.batchIntervalMinutes.coerceAtLeast(5) * 60_000L)
                if (cfg.masterEnabled && cfg.batchIntervalMinutes < 15 && cfg.hasAnyDeliveryChannel) {
                    delay(intervalMs)
                    val latest = settingsRepo.getSettings()
                    if (latest.masterEnabled && latest.batchIntervalMinutes < 15) {
                        runCatching { coordinator.deliverPendingBatch() }
                    }
                } else {
                    // Sleep lightly and re-check in case user switches to 5/10 min later
                    delay(60_000L)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "브릿지 상시 연결 유지",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "안드로이드 절전 모드에서 알림 수집이 끊기지 않도록 유지합니다."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "muse_bridge_keep_alive"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, BridgeKeepAliveService::class.java)
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, BridgeKeepAliveService::class.java))
            }
        }
    }
}
