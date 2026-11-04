package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Bounded coalesced wake state. This contains no plaintext or authority to read a room. */
data class CollaborationSpacePushHead(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
    val revision: Long,
    val deliveredRevision: Long,
    val createdAt: Instant,
    val lastHintAt: Instant,
    val expiresAt: Instant,
    val visibleRevision: Long,
    val event: CollaborationPushEvent?,
    val visibleAt: Instant?,
    val recentEventCount: Int,
    val availableAt: Instant,
    val attemptRevision: Long,
    val attempts: Int,
    val lease: CollaborationSpacePushLease?,
) {
    override fun toString() = "CollaborationSpacePushHead(<redacted>)"
}

data class CollaborationSpacePushLease(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
    val ownerId: UUID,
    val token: UUID,
    val revision: Long,
    val expiresAt: Instant,
) {
    override fun toString() = "CollaborationSpacePushLease(<redacted>)"
}
