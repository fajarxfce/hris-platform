package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Separate cursors and leases for the same atomic first-key publication. */
enum class CollaborationSpaceReadinessChannel {
    MATRIX,
    PUSH,
}

data class CollaborationSpaceReadinessLease(
    val eventId: UUID,
    val channel: CollaborationSpaceReadinessChannel,
    val spaceId: UUID,
    val publisherAccountId: UUID,
    val publisherDeviceId: UUID,
    val ownerId: UUID,
    val token: UUID,
    val until: Instant,
) {
    override fun toString() = "CollaborationSpaceReadinessLease(<redacted>)"
}

data class CollaborationSpaceReadinessCheckpoint(
    val lease: CollaborationSpaceReadinessLease,
    val publicationRevision: Long,
    val publicationCutoff: Instant,
    val capturedAt: Instant,
    val expiresAt: Instant,
    val availableAt: Instant,
    val attempts: Int,
    val after: CollaborationReadinessPosition?,
) {
    override fun toString() = "CollaborationSpaceReadinessCheckpoint(<redacted>)"
}
