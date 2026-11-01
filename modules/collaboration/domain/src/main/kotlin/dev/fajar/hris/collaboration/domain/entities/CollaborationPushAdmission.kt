package dev.fajar.hris.collaboration.domain.entities

import dev.fajar.hris.identity.domain.entities.NativePushRegistration
import java.time.Instant

/** Only permission to enqueue/send an opaque wake hint. Never a Matrix transport grant. */
sealed interface CollaborationPushTarget {
    /** Permanent device/parent retirement permits rejection of this provider pusher. */
    data object Retired : CollaborationPushTarget

    /** Temporary loss of availability is acknowledged without deleting the retained pusher. */
    data object Suppressed : CollaborationPushTarget

    data class Ready(
        val binding: CollaborationPushBinding,
        val deviceVersion: Long,
        val nativeSessionVersion: Long,
        val credentialVersion: Long,
        val gatewayVersion: Long,
        val registration: NativePushRegistration,
        val observedAt: Instant,
        val expiresAt: Instant,
    ) : CollaborationPushTarget {
        override fun toString() = "CollaborationPushTarget.Ready(<redacted>)"
    }
}
