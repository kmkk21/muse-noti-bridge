package com.kmkk21.musenotibridge.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "muse_bridge_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val AD_BLOCK_ENABLED = booleanPreferencesKey("ad_block_enabled")
        val KEEP_ALIVE_ENABLED = booleanPreferencesKey("keep_alive_enabled")
        val APP_FILTER_MODE = stringPreferencesKey("app_filter_mode")
        val SELECTED_PACKAGES = stringSetPreferencesKey("selected_packages")
        val AD_KEYWORDS = stringSetPreferencesKey("ad_keywords")
        val IGNORE_KEYWORDS = stringSetPreferencesKey("ignore_keywords")
        val URGENT_KEYWORDS = stringSetPreferencesKey("urgent_keywords")
        val MAX_CONTENT_CHARS = intPreferencesKey("max_content_chars")
        val BATCH_INTERVAL_MINUTES = intPreferencesKey("batch_interval_minutes")
        val URGENT_INSTANT_SEND = booleanPreferencesKey("urgent_instant_send")

        val EMAIL_ENABLED = booleanPreferencesKey("email_enabled")
        val SMTP_PRESET = stringPreferencesKey("smtp_preset")
        val SMTP_HOST = stringPreferencesKey("smtp_host")
        val SMTP_PORT = intPreferencesKey("smtp_port")
        val SMTP_USE_SSL = booleanPreferencesKey("smtp_use_ssl")
        val SMTP_USERNAME = stringPreferencesKey("smtp_username")
        val SMTP_PASSWORD = stringPreferencesKey("smtp_password")
        val RECIPIENT_EMAIL = stringPreferencesKey("recipient_email")

        val WEBHOOK_ENABLED = booleanPreferencesKey("webhook_enabled")
        val WEBHOOK_URL = stringPreferencesKey("webhook_url")
        val WEBHOOK_AUTH_HEADER = stringPreferencesKey("webhook_auth_header")

        val MUSE_INSTRUCTION = stringPreferencesKey("muse_instruction")
    }

    val settingsFlow: Flow<BridgeSettings> = context.dataStore.data.map { prefs ->
        val presetName = prefs[Keys.SMTP_PRESET] ?: SmtpPreset.GMAIL.name
        val preset = runCatching { SmtpPreset.valueOf(presetName) }.getOrDefault(SmtpPreset.GMAIL)
        val filterModeName = prefs[Keys.APP_FILTER_MODE] ?: AppFilterMode.EXCLUDE_SELECTED.name
        val filterMode = runCatching { AppFilterMode.valueOf(filterModeName) }
            .getOrDefault(AppFilterMode.EXCLUDE_SELECTED)

        BridgeSettings(
            masterEnabled = prefs[Keys.MASTER_ENABLED] ?: true,
            adBlockEnabled = prefs[Keys.AD_BLOCK_ENABLED] ?: true,
            keepAliveEnabled = prefs[Keys.KEEP_ALIVE_ENABLED] ?: true,
            appFilterMode = filterMode,
            selectedPackages = prefs[Keys.SELECTED_PACKAGES] ?: emptySet(),
            adKeywords = prefs[Keys.AD_KEYWORDS] ?: BridgeSettings.DEFAULT_AD_KEYWORDS,
            ignoreKeywords = prefs[Keys.IGNORE_KEYWORDS] ?: BridgeSettings.DEFAULT_IGNORE_KEYWORDS,
            urgentKeywords = prefs[Keys.URGENT_KEYWORDS] ?: BridgeSettings.DEFAULT_URGENT_KEYWORDS,
            maxContentChars = prefs[Keys.MAX_CONTENT_CHARS] ?: 160,
            batchIntervalMinutes = prefs[Keys.BATCH_INTERVAL_MINUTES] ?: 120,
            urgentInstantSend = prefs[Keys.URGENT_INSTANT_SEND] ?: true,
            emailEnabled = prefs[Keys.EMAIL_ENABLED] ?: true,
            smtpPreset = preset,
            smtpHost = prefs[Keys.SMTP_HOST] ?: preset.host,
            smtpPort = prefs[Keys.SMTP_PORT] ?: preset.port,
            smtpUseSsl = prefs[Keys.SMTP_USE_SSL] ?: preset.useSsl,
            smtpUsername = prefs[Keys.SMTP_USERNAME] ?: "",
            smtpPassword = prefs[Keys.SMTP_PASSWORD] ?: "",
            recipientEmail = prefs[Keys.RECIPIENT_EMAIL] ?: "",
            webhookEnabled = prefs[Keys.WEBHOOK_ENABLED] ?: false,
            webhookUrl = prefs[Keys.WEBHOOK_URL] ?: "",
            webhookAuthHeader = prefs[Keys.WEBHOOK_AUTH_HEADER] ?: "",
            museInstruction = prefs[Keys.MUSE_INSTRUCTION] ?: BridgeSettings.DEFAULT_MUSE_INSTRUCTION
        )
    }

    suspend fun getSettings(): BridgeSettings = settingsFlow.first()

    suspend fun updateSettings(transform: (BridgeSettings) -> BridgeSettings) {
        val current = getSettings()
        val updated = transform(current)
        context.dataStore.edit { prefs ->
            prefs[Keys.MASTER_ENABLED] = updated.masterEnabled
            prefs[Keys.AD_BLOCK_ENABLED] = updated.adBlockEnabled
            prefs[Keys.KEEP_ALIVE_ENABLED] = updated.keepAliveEnabled
            prefs[Keys.APP_FILTER_MODE] = updated.appFilterMode.name
            prefs[Keys.SELECTED_PACKAGES] = updated.selectedPackages
            prefs[Keys.AD_KEYWORDS] = updated.adKeywords
            prefs[Keys.IGNORE_KEYWORDS] = updated.ignoreKeywords
            prefs[Keys.URGENT_KEYWORDS] = updated.urgentKeywords
            prefs[Keys.MAX_CONTENT_CHARS] = updated.maxContentChars.coerceIn(60, 400)
            prefs[Keys.BATCH_INTERVAL_MINUTES] = updated.batchIntervalMinutes.coerceIn(5, 720)
            prefs[Keys.URGENT_INSTANT_SEND] = updated.urgentInstantSend

            prefs[Keys.EMAIL_ENABLED] = updated.emailEnabled
            prefs[Keys.SMTP_PRESET] = updated.smtpPreset.name
            prefs[Keys.SMTP_HOST] = updated.smtpHost.trim()
            prefs[Keys.SMTP_PORT] = updated.smtpPort
            prefs[Keys.SMTP_USE_SSL] = updated.smtpUseSsl
            prefs[Keys.SMTP_USERNAME] = updated.smtpUsername.trim()
            prefs[Keys.SMTP_PASSWORD] = updated.smtpPassword.trim()
            prefs[Keys.RECIPIENT_EMAIL] = updated.recipientEmail.trim()

            prefs[Keys.WEBHOOK_ENABLED] = updated.webhookEnabled
            prefs[Keys.WEBHOOK_URL] = updated.webhookUrl.trim()
            prefs[Keys.WEBHOOK_AUTH_HEADER] = updated.webhookAuthHeader.trim()
            prefs[Keys.MUSE_INSTRUCTION] = updated.museInstruction.trim()
        }
    }
}
