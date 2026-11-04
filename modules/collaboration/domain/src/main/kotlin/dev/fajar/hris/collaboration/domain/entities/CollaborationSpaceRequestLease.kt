package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

data class CollaborationSpaceRequestOwner(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
)

enum class CollaborationSpaceRequestKind {
    GATEWAY,
    PROVIDER_RENEWAL,
}

/** An internal requested position, never a public provider or room access grant. */
data class CollaborationSpaceRequestPosition(
    val owner: CollaborationSpaceRequestOwner,
    val kind: CollaborationSpaceRequestKind,
    val gatewayId: UUID? = null,
    val gatewayVersion: Long? = null,
)

data class CollaborationSpaceRequestLease(
    val position: CollaborationSpaceRequestPosition,
    val slot: Int,
    val token: UUID,
    val startedAt: Instant,
    val expiresAt: Instant,
) {
    override fun toString() = "CollaborationSpaceRequestLease(<redacted>)"
}
