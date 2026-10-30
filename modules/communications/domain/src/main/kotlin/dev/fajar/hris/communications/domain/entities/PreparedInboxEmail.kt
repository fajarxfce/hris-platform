package dev.fajar.hris.communications.domain.entities

import dev.fajar.hris.mail.domain.entities.OutboundMail

sealed interface PreparedInboxEmail {
    data object Handled : PreparedInboxEmail

    data class Ready(val message: OutboundMail) : PreparedInboxEmail
}
