package com.kmkk21.musenotibridge.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.kmkk21.musenotibridge.data.db.AppDatabase
import com.kmkk21.musenotibridge.data.db.NotificationCategory
import com.kmkk21.musenotibridge.data.db.NotificationEntity
import com.kmkk21.musenotibridge.data.db.NotificationStatus
import com.kmkk21.musenotibridge.data.prefs.SettingsRepository
import com.kmkk21.musenotibridge.domain.FilterDecision
import com.kmkk21.musenotibridge.domain.NotificationFilterEngine
import com.kmkk21.musenotibridge.domain.RawNotificationInput
import com.kmkk21.musenotibridge.worker.DeliveryScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MuseNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isListenerConnected.value = true
        Log.i(TAG, "NotificationListener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isListenerConnected.value = false
        Log.w(TAG, "NotificationListener disconnected — requesting rebind")
        requestRebind(ComponentName(this, MuseNotificationListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val notification = sbn.notification ?: return
        val extras = notification.extras

        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val title = extractString(extras.getCharSequence(Notification.EXTRA_TITLE))
        val bigText = extractString(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        val text = extractString(extras.getCharSequence(Notification.EXTRA_TEXT))
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" / ") { it.toString().trim() }
            .orEmpty()

        val bestContent = when {
            bigText.isNotBlank() -> bigText
            textLines.isNotBlank() -> textLines
            else -> text
        }

        val packageName = sbn.packageName.orEmpty()
        val appName = resolveAppName(packageName)
        val receivedAt = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        val key = sbn.key

        val rawInput = RawNotificationInput(
            packageName = packageName,
            appName = appName,
            title = title,
            content = bestContent,
            isOngoing = sbn.isOngoing,
            isGroupSummary = isGroupSummary,
            selfPackageName = applicationContext.packageName
        )

        serviceScope.launch {
            try {
                val settingsRepo = SettingsRepository(applicationContext)
                val settings = settingsRepo.getSettings()
                val dao = AppDatabase.getInstance(applicationContext).notificationDao()

                when (val decision = NotificationFilterEngine.evaluate(rawInput, settings)) {
                    FilterDecision.DropSilently -> Unit

                    is FilterDecision.BlockAd -> {
                        // Cancel the ad notification locally right away
                        runCatching { cancelNotification(key) }
                        dao.insert(
                            NotificationEntity(
                                packageName = packageName,
                                appName = appName,
                                title = decision.cleanTitle,
                                content = decision.cleanContent,
                                category = NotificationCategory.AD,
                                status = NotificationStatus.AD_BLOCKED,
                                matchedRule = decision.matchedKeyword,
                                receivedAt = receivedAt,
                                fingerprint = "ad_${packageName}_${receivedAt}"
                            )
                        )
                    }

                    is FilterDecision.Ignore -> {
                        dao.insert(
                            NotificationEntity(
                                packageName = packageName,
                                appName = appName,
                                title = decision.cleanTitle,
                                content = decision.cleanContent,
                                category = NotificationCategory.IGNORED,
                                status = NotificationStatus.IGNORED,
                                matchedRule = decision.reason,
                                receivedAt = receivedAt,
                                fingerprint = "ign_${packageName}_${receivedAt}"
                            )
                        )
                    }

                    is FilterDecision.Accept -> {
                        // Deduplicate identical alerts arriving within 60 seconds
                        val dupWindow = receivedAt - 60_000L
                        val duplicateCount = dao.countRecentDuplicate(decision.fingerprint, dupWindow)
                        if (duplicateCount > 0) return@launch

                        dao.insert(
                            NotificationEntity(
                                packageName = packageName,
                                appName = appName,
                                title = decision.cleanTitle,
                                content = decision.cleanContent,
                                category = decision.category,
                                status = NotificationStatus.PENDING,
                                matchedRule = decision.matchedRule,
                                receivedAt = receivedAt,
                                fingerprint = decision.fingerprint
                            )
                        )

                        if (decision.isUrgent && settings.urgentInstantSend && settings.hasAnyDeliveryChannel) {
                            DeliveryScheduler.triggerImmediate(applicationContext)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process notification from $packageName", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isListenerConnected.value = false
        serviceScope.cancel()
    }

    private fun extractString(seq: CharSequence?): String {
        return seq?.toString()?.trim().orEmpty()
    }

    private fun resolveAppName(packageName: String): String {
        return runCatching {
            val pm = applicationContext.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        }.getOrElse { packageName }
    }

    companion object {
        private const val TAG = "MuseNotiListener"
        private val _isListenerConnected = MutableStateFlow(false)
        val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

        fun requestRebindSafe(context: Context) {
            runCatching {
                requestRebind(ComponentName(context, MuseNotificationListenerService::class.java))
            }
        }
    }
}
