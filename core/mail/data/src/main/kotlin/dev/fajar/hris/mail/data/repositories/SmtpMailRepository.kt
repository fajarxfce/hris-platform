package dev.fajar.hris.mail.data.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.mail.data.datasources.*
import dev.fajar.hris.mail.data.safeMailCall
import dev.fajar.hris.mail.domain.entities.OutboundMail
import dev.fajar.hris.mail.domain.repositories.MailRepository

class SmtpMailRepository(private val source: MailDataSource) : MailRepository {
    override fun send(message: OutboundMail): Result<Unit> = safeMailCall {
        source.send(MailEnvelope(message.id, message.recipient, message.subject, message.text))
    }
}
