package com.kmkk21.musenotibridge

import android.app.Application
import com.kmkk21.musenotibridge.data.prefs.SettingsRepository
import com.kmkk21.musenotibridge.service.BridgeKeepAliveService
import com.kmkk21.musenotibridge.worker.DeliveryScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MuseBridgeApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val settings = SettingsRepository(this@MuseBridgeApplication).getSettings()
            if (settings.masterEnabled) {
                DeliveryScheduler.schedulePeriodic(
                    this@MuseBridgeApplication,
                    settings.batchIntervalMinutes
                )
                if (settings.keepAliveEnabled) {
                    BridgeKeepAliveService.start(this@MuseBridgeApplication)
                }
            }
        }
    }
}
