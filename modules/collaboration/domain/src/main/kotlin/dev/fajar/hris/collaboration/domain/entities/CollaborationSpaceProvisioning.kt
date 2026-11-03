package dev.fajar.hris.collaboration.domain.entities

import dev.fajar.hris.administration.domain.entities.ClientVersion
import java.time.Instant
import java.util.UUID

/** A durable request intent; current native/group admission is required before provider I/O. */
data class CollaborationSpaceProvisioningRequest(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
    val revision: Long,
    val nativeSessionVersion: Long,
    val credentialVersion: Long,
    val authenticatedAt: Instant,
    val mfaVerifiedAt: Instant?,
    val correlationId: UUID,
    val client: ClientVersion,
    val requestedAt: Instant,
    val expiresAt: Instant,
) {
    override fun toString() = "CollaborationSpaceProvisioningRequest(redacted)"
}

enum class CollaborationSpaceProvisioningState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

enum class CollaborationSpaceProvisioningFailure {
    REQUEST_EXPIRED,
    ATTEMPTS_EXHAUSTED,
    ACCESS_DENIED,
    PROVIDER_UNAVAILABLE,
    PROVIDER_IDENTITY_CHANGED,
    PUBLICATION_CHANGED,
}

data class CollaborationSpaceProvisioningOutcome(
    val revision: Long,
    val state: CollaborationSpaceProvisioningState,
    val at: Instant,
    val failure: CollaborationSpaceProvisioningFailure?,
)

data class CollaborationSpaceProvisioningLease(
    val request: CollaborationSpaceProvisioningRequest,
    val attempt: Int,
    val ownerId: UUID,
    val token: UUID,
    val until: Instant,
) {
    override fun toString() = "CollaborationSpaceProvisioningLease(redacted)"
}

data class CollaborationSpaceProvisioning(
    val request: CollaborationSpaceProvisioningRequest,
    val createdAt: Instant,
    val availableAt: Instant,
    val state: CollaborationSpaceProvisioningState,
    val attempts: Int,
    val lease: CollaborationSpaceProvisioningLease?,
    val lastOutcome: CollaborationSpaceProvisioningOutcome?,
) {
    override fun toString() = "CollaborationSpaceProvisioning(redacted)"
}
