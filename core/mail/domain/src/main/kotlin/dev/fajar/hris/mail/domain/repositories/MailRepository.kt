package dev.fajar.hris.mail.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.mail.domain.entities.OutboundMail

fun interface MailRepository {
    fun send(message: OutboundMail): Result<Unit>
}
