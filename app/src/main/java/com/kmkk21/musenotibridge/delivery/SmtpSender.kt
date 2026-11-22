package com.kmkk21.musenotibridge.delivery

import com.kmkk21.musenotibridge.data.prefs.BridgeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object SmtpSender {

    suspend fun sendEmail(
        settings: BridgeSettings,
        subject: String,
        body: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.smtpHost.isNotBlank()) { "SMTP 서버 주소가 비어 있습니다." }
            require(settings.smtpUsername.isNotBlank()) { "발신 이메일 주소가 비어 있습니다." }
            require(settings.smtpPassword.isNotBlank()) { "앱 비밀번호가 비어 있습니다." }
            require(settings.recipientEmail.isNotBlank()) { "수신 이메일(Muse Mail) 주소가 비어 있습니다." }

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.host", settings.smtpHost)
                put("mail.smtp.port", settings.smtpPort.toString())
                put("mail.smtp.connectiontimeout", "15000")
                put("mail.smtp.timeout", "15000")
                put("mail.smtp.writetimeout", "15000")
                if (settings.smtpUseSsl) {
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.socketFactory.port", settings.smtpPort.toString())
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                } else {
                    put("mail.smtp.starttls.enable", "true")
                }
            }

            val session = Session.getInstance(
                props,
                object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(settings.smtpUsername, settings.smtpPassword)
                    }
                }
            )

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(settings.smtpUsername))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(settings.recipientEmail))
                setSubject(subject, "UTF-8")
                setText(body, "UTF-8")
            }

            Transport.send(message)
        }
    }
}
