package com.kmkk21.musenotibridge.delivery

import com.kmkk21.musenotibridge.data.prefs.BridgeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object WebhookSender {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun postDigest(
        settings: BridgeSettings,
        jsonPayload: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.webhookUrl.isNotBlank()) { "Webhook URL이 비어 있습니다." }

            val requestBuilder = Request.Builder()
                .url(settings.webhookUrl)
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "MuseNotiBridge/1.0")

            if (settings.webhookAuthHeader.isNotBlank()) {
                val rawHeader = settings.webhookAuthHeader.trim()
                if (rawHeader.contains(":")) {
                    val key = rawHeader.substringBefore(":").trim()
                    val value = rawHeader.substringAfter(":").trim()
                    if (key.isNotEmpty()) {
                        requestBuilder.header(key, value)
                    }
                } else {
                    requestBuilder.header("Authorization", rawHeader)
                }
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyPreview = response.body?.string()?.take(160).orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: $bodyPreview")
                }
                "HTTP ${response.code} OK"
            }
        }
    }
}
