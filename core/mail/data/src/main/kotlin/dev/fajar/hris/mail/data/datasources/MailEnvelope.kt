package dev.fajar.hris.mail.data.datasources

import java.util.UUID

data class MailEnvelope(
    val id: UUID,
    val recipient: String,
    val subject: String,
    val text: String,
) {
    override fun toString(): String = "MailEnvelope(id=$id, <redacted>)"
}
