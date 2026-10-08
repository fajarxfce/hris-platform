package dev.fajar.hris.mail.domain.entities

import java.util.UUID

data class OutboundMail(
    val id: UUID,
    val recipient: String,
    val subject: String,
    val text: String,
) {
    override fun toString(): String = "OutboundMail(id=$id, <redacted>)"
}
