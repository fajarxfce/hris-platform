package dev.fajar.hris.communications.domain.entities

import dev.fajar.hris.identity.domain.entities.NativePushRegistration
import dev.fajar.hris.push.domain.entities.PushMessage

sealed interface PreparedInboxPush {
    data object Handled : PreparedInboxPush

    class Ready(val registration: NativePushRegistration, val message: PushMessage) :
        PreparedInboxPush {
        override fun toString() = "PreparedInboxPush.Ready(<redacted>)"
    }
}
