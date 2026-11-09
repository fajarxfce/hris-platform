package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Stable PostgreSQL UUID ordering; Java UUID.compareTo uses signed halves. */
data class CollaborationReadinessPosition(val accountId: UUID, val deviceId: UUID) :
    Comparable<CollaborationReadinessPosition> {
    override fun compareTo(other: CollaborationReadinessPosition): Int =
        compareValuesBy(this, other, { it.accountId.toString() }, { it.deviceId.toString() })
}

/** Public binding coordinates only; the worker must acquire the target's own scope. */
data class CollaborationReadinessTarget(
    val position: CollaborationReadinessPosition,
    val nativeSessionId: UUID,
)

data class CollaborationReadinessLease(
    val eventId: UUID,
    val companyId: UUID,
    val publisherAccountId: UUID,
    val publisherDeviceId: UUID,
    val ownerId: UUID,
    val token: UUID,
    val expiresAt: Instant,
) {
    override fun toString() = "CollaborationReadinessLease(<redacted>)"
}

data class CollaborationReadinessCheckpoint(
    val lease: CollaborationReadinessLease,
    val publicationCutoff: Instant,
    val after: CollaborationReadinessPosition?,
    /** Present only on V2 Matrix controls; the independent FCM journal has no such cursor. */
    val publicationRevision: Long? = null,
)
