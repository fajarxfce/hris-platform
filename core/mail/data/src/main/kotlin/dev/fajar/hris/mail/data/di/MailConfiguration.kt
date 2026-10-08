package dev.fajar.hris.mail.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.mail.data.datasources.JakartaMailDataSource
import dev.fajar.hris.mail.data.repositories.SmtpMailRepository
import dev.fajar.hris.mail.domain.repositories.MailRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration(proxyBeanMethods = false)
class MailConfiguration {
    @Bean
    fun mailRepository(environment: Environment): MailRepository {
        if (!environment.getProperty("HRIS_MAIL_ENABLED", Boolean::class.java, false))
            return MailRepository {
                Result.Failed(Failure(FailureKind.UNAVAILABLE, "mail_not_configured"))
            }
        val settings =
            SmtpSettings(
                environment.getRequiredProperty("HRIS_MAIL_HOST"),
                environment.getProperty("HRIS_MAIL_PORT", Int::class.java, 587),
                environment.getRequiredProperty("HRIS_MAIL_FROM"),
                environment.getProperty("HRIS_MAIL_USERNAME", ""),
                environment.getProperty("HRIS_MAIL_PASSWORD", ""),
                environment.getProperty("HRIS_MAIL_TRANSPORT", "STARTTLS"),
                environment.getProperty(
                    "HRIS_MAIL_ALLOW_LOOPBACK_PLAINTEXT",
                    Boolean::class.java,
                    false,
                ),
            )
        return SmtpMailRepository(JakartaMailDataSource(createSmtpSender(settings), settings.from))
    }
}
