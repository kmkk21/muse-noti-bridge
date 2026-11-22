package com.kmkk21.musenotibridge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kmkk21.musenotibridge.data.prefs.SettingsRepository
import com.kmkk21.musenotibridge.worker.DeliveryScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(context).getSettings()
                if (settings.masterEnabled) {
                    DeliveryScheduler.schedulePeriodic(context, settings.batchIntervalMinutes)
                    MuseNotificationListenerService.requestRebindSafe(context)
                    if (settings.keepAliveEnabled) {
                        BridgeKeepAliveService.start(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
