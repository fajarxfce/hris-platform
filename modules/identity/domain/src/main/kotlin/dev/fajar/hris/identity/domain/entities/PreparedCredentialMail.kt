package dev.fajar.hris.identity.domain.entities

import dev.fajar.hris.mail.domain.entities.OutboundMail

sealed interface PreparedCredentialMail {
    data class Ready(val message: OutboundMail, val challenge: CredentialChallenge) :
        PreparedCredentialMail

    data object Skipped : PreparedCredentialMail
}
