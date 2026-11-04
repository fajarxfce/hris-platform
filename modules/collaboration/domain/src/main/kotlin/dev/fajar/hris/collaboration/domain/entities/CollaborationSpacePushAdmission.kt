package dev.fajar.hris.collaboration.domain.entities

import dev.fajar.hris.identity.domain.entities.NativePushRegistration
import java.time.Instant

/** An opaque wake target, never content, reply, device-key or interactive admission. */
sealed interface CollaborationSpacePushTarget {
    data object Suppressed : CollaborationSpacePushTarget

    data object Retired : CollaborationSpacePushTarget

    data class Ready(
        val binding: CollaborationSpacePushBinding,
        val identity: CollaborationSpaceIdentity,
        val device: CollaborationSpaceDevice,
        val provider: CollaborationSpaceProviderState,
        val nativeSessionVersion: Long,
        val credentialVersion: Long,
        val gateway: CollaborationSpaceGatewaySession,
        val registration: NativePushRegistration,
        val observedAt: Instant,
        val expiresAt: Instant,
    ) : CollaborationSpacePushTarget {
        override fun toString() = "CollaborationSpacePushTarget.Ready(<metadata>)"
    }
}

class CollaborationSpacePushHint(
    val event: CollaborationPushEvent?,
    keys: List<CollaborationSpacePushKey>,
) {
    val keys: List<CollaborationSpacePushKey> = keys.toList()

    override fun toString() = "CollaborationSpacePushHint(<redacted>)"
}

/** Only unknown or proven permanently retired keys may be removed by Matrix. */
class CollaborationSpacePushReceipt(rejected: List<CollaborationSpacePushKey>) {
    val rejected: List<CollaborationSpacePushKey> = rejected.toList()

    override fun toString() = "CollaborationSpacePushReceipt(<redacted>)"
}
