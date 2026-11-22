package com.kmkk21.musenotibridge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kmkk21.musenotibridge.data.db.AppDatabase
import com.kmkk21.musenotibridge.data.db.DeliveryLogEntity
import com.kmkk21.musenotibridge.data.db.NotificationEntity
import com.kmkk21.musenotibridge.data.db.NotificationStatus
import com.kmkk21.musenotibridge.data.prefs.AppFilterMode
import com.kmkk21.musenotibridge.data.prefs.BridgeSettings
import com.kmkk21.musenotibridge.data.prefs.SettingsRepository
import com.kmkk21.musenotibridge.data.prefs.SmtpPreset
import com.kmkk21.musenotibridge.delivery.DeliveryCoordinator
import com.kmkk21.musenotibridge.domain.DigestFormatter
import com.kmkk21.musenotibridge.service.BridgeKeepAliveService
import com.kmkk21.musenotibridge.service.MuseNotificationListenerService
import com.kmkk21.musenotibridge.util.InstalledAppItem
import com.kmkk21.musenotibridge.util.PermissionHelper
import com.kmkk21.musenotibridge.worker.DeliveryScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class SystemStatusState(
    val notificationPermissionGranted: Boolean = false,
    val listenerBound: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false
)

class BridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val dao = AppDatabase.getInstance(context).notificationDao()
    private val settingsRepo = SettingsRepository(context)
    private val coordinator = DeliveryCoordinator(context)

    private val startOfTodayMs: Long
        get() = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    val settings: StateFlow<BridgeSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BridgeSettings())

    val recentNotifications: StateFlow<List<NotificationEntity>> = dao.observeRecent(250)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deliveryLogs: StateFlow<List<DeliveryLogEntity>> = dao.observeRecentDeliveryLogs(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCount: StateFlow<Int> = dao.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val sentTodayCount: StateFlow<Int> = dao.observeSentCountSince(startOfTodayMs)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val adBlockedTodayCount: StateFlow<Int> = dao.observeAdBlockedCountSince(startOfTodayMs)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val estimatedPendingTokens: StateFlow<Int> = combine(recentNotifications, settings) { list, cfg ->
        val pending = list.filter {
            it.status == NotificationStatus.PENDING || it.status == NotificationStatus.FAILED
        }
        if (pending.isEmpty()) 0
        else DigestFormatter.format(pending, cfg.museInstruction).estimatedTokens
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _systemStatus = MutableStateFlow(SystemStatusState())
    val systemStatus: StateFlow<SystemStatusState> = _systemStatus.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        refreshSystemStatus()
        viewModelScope.launch {
            MuseNotificationListenerService.isListenerConnected.collect { connected ->
                _systemStatus.value = _systemStatus.value.copy(listenerBound = connected)
            }
        }
    }

    fun refreshSystemStatus() {
        val notiOk = PermissionHelper.isNotificationListenerEnabled(context)
        val batteryOk = PermissionHelper.isIgnoringBatteryOptimizations(context)
        _systemStatus.value = SystemStatusState(
            notificationPermissionGranted = notiOk,
            listenerBound = MuseNotificationListenerService.isListenerConnected.value || notiOk,
            batteryOptimizationIgnored = batteryOk
        )
        if (notiOk) {
            MuseNotificationListenerService.requestRebindSafe(context)
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun loadInstalledAppsIfNeeded() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = PermissionHelper.loadInstalledApps(context)
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(masterEnabled = enabled) }
            val cfg = settingsRepo.getSettings()
            if (enabled) {
                DeliveryScheduler.schedulePeriodic(context, cfg.batchIntervalMinutes)
                if (cfg.keepAliveEnabled) BridgeKeepAliveService.start(context)
                _snackbarMessage.value = "Muse 알림 브릿지가 활성화되었습니다."
            } else {
                DeliveryScheduler.cancelPeriodic(context)
                BridgeKeepAliveService.stop(context)
                _snackbarMessage.value = "Muse 알림 브릿지가 일시 중지되었습니다."
            }
        }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(adBlockEnabled = enabled) }
        }
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(keepAliveEnabled = enabled) }
            if (enabled && settings.value.masterEnabled) {
                BridgeKeepAliveService.start(context)
            } else {
                BridgeKeepAliveService.stop(context)
            }
        }
    }

    fun setUrgentInstantSend(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(urgentInstantSend = enabled) }
        }
    }

    fun setBatchIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(batchIntervalMinutes = minutes) }
            if (settings.value.masterEnabled) {
                DeliveryScheduler.schedulePeriodic(context, minutes)
            }
            _snackbarMessage.value = "전송 주기가 ${formatInterval(minutes)}로 설정되었습니다."
        }
    }

    fun setMaxContentChars(chars: Int) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(maxContentChars = chars) }
        }
    }

    fun setAppFilterMode(mode: AppFilterMode) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(appFilterMode = mode) }
        }
    }

    fun togglePackageSelection(packageName: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings { current ->
                val next = current.selectedPackages.toMutableSet()
                if (!next.add(packageName)) next.remove(packageName)
                current.copy(selectedPackages = next)
            }
        }
    }

    fun addAdKeyword(keyword: String) {
        val clean = keyword.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(adKeywords = it.adKeywords + clean) }
        }
    }

    fun removeAdKeyword(keyword: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(adKeywords = it.adKeywords - keyword) }
        }
    }

    fun addIgnoreKeyword(keyword: String) {
        val clean = keyword.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(ignoreKeywords = it.ignoreKeywords + clean) }
        }
    }

    fun removeIgnoreKeyword(keyword: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(ignoreKeywords = it.ignoreKeywords - keyword) }
        }
    }

    fun addUrgentKeyword(keyword: String) {
        val clean = keyword.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(urgentKeywords = it.urgentKeywords + clean) }
        }
    }

    fun removeUrgentKeyword(keyword: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(urgentKeywords = it.urgentKeywords - keyword) }
        }
    }

    fun saveDeliverySettings(
        emailEnabled: Boolean,
        smtpPreset: SmtpPreset,
        smtpHost: String,
        smtpPort: Int,
        smtpUseSsl: Boolean,
        smtpUsername: String,
        smtpPassword: String,
        recipientEmail: String,
        webhookEnabled: Boolean,
        webhookUrl: String,
        webhookAuthHeader: String,
        museInstruction: String
    ) {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(
                    emailEnabled = emailEnabled,
                    smtpPreset = smtpPreset,
                    smtpHost = smtpHost,
                    smtpPort = smtpPort,
                    smtpUseSsl = smtpUseSsl,
                    smtpUsername = smtpUsername,
                    smtpPassword = smtpPassword,
                    recipientEmail = recipientEmail,
                    webhookEnabled = webhookEnabled,
                    webhookUrl = webhookUrl,
                    webhookAuthHeader = webhookAuthHeader,
                    museInstruction = museInstruction.ifBlank { BridgeSettings.DEFAULT_MUSE_INSTRUCTION }
                )
            }
            val cfg = settingsRepo.getSettings()
            if (cfg.masterEnabled) {
                DeliveryScheduler.schedulePeriodic(context, cfg.batchIntervalMinutes)
            }
            _snackbarMessage.value = "Muse 전송 설정이 저장되었습니다."
        }
    }

    fun sendPendingNow() {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val outcome = coordinator.deliverPendingBatch()
                _snackbarMessage.value = outcome.summaryMessage
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun sendTestMessage(candidateSettings: BridgeSettings) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val outcome = coordinator.sendTestMessage(candidateSettings)
                _snackbarMessage.value = outcome.summaryMessage
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun requeueNotification(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.requeueForDelivery(id)
            _snackbarMessage.value = "해당 알림을 다음 전송 대기열에 다시 추가했습니다."
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAll()
            _snackbarMessage.value = "모든 알림 기록을 비웠습니다."
        }
    }

    companion object {
        fun formatInterval(minutes: Int): String = when {
            minutes < 60 -> "${minutes}분"
            minutes % 60 == 0 -> "${minutes / 60}시간"
            else -> "${minutes / 60}시간 ${minutes % 60}분"
        }
    }
}
