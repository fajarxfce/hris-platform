package dev.fajar.hris.mail.data.di

import java.util.Properties
import org.springframework.mail.javamail.JavaMailSenderImpl

fun createSmtpSender(settings: SmtpSettings) =
    JavaMailSenderImpl().apply {
        host = settings.host
        port = settings.port
        username = settings.username
        password = settings.password
        defaultEncoding = Charsets.UTF_8.name()
        javaMailProperties =
            Properties().apply {
                setProperty("mail.smtp.auth", settings.username.isNotBlank().toString())
                setProperty(
                    "mail.smtp.starttls.enable",
                    (settings.transport == "STARTTLS").toString(),
                )
                setProperty(
                    "mail.smtp.starttls.required",
                    (settings.transport == "STARTTLS").toString(),
                )
                setProperty("mail.smtp.ssl.enable", (settings.transport == "TLS").toString())
                setProperty("mail.smtp.ssl.checkserveridentity", "true")
                setProperty("mail.smtp.connectiontimeout", settings.timeout.toMillis().toString())
                setProperty("mail.smtp.timeout", settings.timeout.toMillis().toString())
                setProperty("mail.smtp.writetimeout", settings.timeout.toMillis().toString())
                setProperty("mail.smtp.quitwait", "false")
                setProperty("mail.debug", "false")
            }
    }
