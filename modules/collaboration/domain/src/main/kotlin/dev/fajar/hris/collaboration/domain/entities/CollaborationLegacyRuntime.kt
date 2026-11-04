package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Original company/native IDs remain provenance, never a substitute group scope. */
data class CollaborationLegacyRuntimeOwner(
    val companyId: UUID,
    val deviceId: UUID,
    val accountId: UUID,
    val nativeSessionId: UUID,
)

enum class CollaborationLegacyRuntimePhase {
    LEGACY,
    DRAINING,
}

data class CollaborationLegacyRuntimeTarget(val spaceId: UUID, val deviceId: UUID)

data class CollaborationLegacyRuntime(
    val owner: CollaborationLegacyRuntimeOwner,
    val phase: CollaborationLegacyRuntimePhase,
    val version: Long,
    val createdAt: Instant,
    val frozenAt: Instant?,
    val target: CollaborationLegacyRuntimeTarget?,
)

/** A bounded server operation, not a provider token or client admission grant. */
data class CollaborationLegacyRuntimeLease(
    val owner: CollaborationLegacyRuntimeOwner,
    val slot: Int,
    val runtimeVersion: Long,
    val token: UUID,
    val startedAt: Instant,
    val expiresAt: Instant,
    val releasedAt: Instant?,
) {
    override fun toString(): String = "CollaborationLegacyRuntimeLease([redacted])"
}
