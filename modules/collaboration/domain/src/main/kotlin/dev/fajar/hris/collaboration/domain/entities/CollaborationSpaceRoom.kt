package dev.fajar.hris.collaboration.domain.entities

import dev.fajar.hris.administration.domain.entities.ClientVersion
import java.time.Instant
import java.util.UUID

enum class CollaborationSpaceRoomOrigin {
    SPACE,
    LEGACY,
}

data class CollaborationSpaceOriginalRoom(
    val companyId: UUID,
    val conversationId: UUID,
    val roomId: String,
    val boundAt: Instant,
)

/** Stable provider candidate. Current native/group/room admission is separate. */
data class CollaborationSpaceRoomIntent(
    val spaceId: UUID,
    val conversationId: UUID,
    val firstAccountId: UUID,
    val secondAccountId: UUID,
    val requestedBy: UUID,
    val matrixCreatorAccountId: UUID,
    val serverName: String,
    val firstMatrixUserId: String,
    val secondMatrixUserId: String,
    val origin: CollaborationSpaceRoomOrigin,
    val original: CollaborationSpaceOriginalRoom?,
    val createdAt: Instant,
) {
    override fun toString() = "CollaborationSpaceRoomIntent(<metadata>)"
}

/** Immutable verified room publication; private native proof stays in its request. */
data class CollaborationSpaceRoomBinding(
    val spaceId: UUID,
    val conversationId: UUID,
    val publishedBy: UUID,
    val requestRevision: Long,
    val leaseToken: UUID,
    val roomId: String,
    val createdAt: Instant,
) {
    override fun toString() = "CollaborationSpaceRoomBinding(<metadata>)"
}

data class CollaborationSpaceRoomRequest(
    val spaceId: UUID,
    val conversationId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val deviceVersion: Long,
    val providerGeneration: Long,
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
    override fun toString() = "CollaborationSpaceRoomRequest(redacted)"
}

enum class CollaborationSpaceRoomFailure {
    REQUEST_EXPIRED,
    ATTEMPTS_EXHAUSTED,
    ACCESS_DENIED,
    PROVIDER_UNAVAILABLE,
    PROVIDER_IDENTITY_CHANGED,
    PUBLICATION_CHANGED,
    ROOM_MISMATCH,
}

data class CollaborationSpaceRoomOutcome(
    val revision: Long,
    val state: CollaborationSpaceProvisioningState,
    val at: Instant,
    val failure: CollaborationSpaceRoomFailure?,
)

data class CollaborationSpaceRoomLease(
    val request: CollaborationSpaceRoomRequest,
    val attempt: Int,
    val ownerId: UUID,
    val token: UUID,
    val until: Instant,
) {
    override fun toString() = "CollaborationSpaceRoomLease(redacted)"
}

data class CollaborationSpaceRoomProvisioning(
    val request: CollaborationSpaceRoomRequest,
    val createdAt: Instant,
    val availableAt: Instant,
    val state: CollaborationSpaceProvisioningState,
    val attempts: Int,
    val lease: CollaborationSpaceRoomLease?,
    val lastOutcome: CollaborationSpaceRoomOutcome?,
) {
    override fun toString() = "CollaborationSpaceRoomProvisioning(redacted)"
}

/**
 * Peer-safe operational projection. It carries neither a device nor native authentication proof.
 */
data class CollaborationSpaceRoomStatus(
    val spaceId: UUID,
    val conversationId: UUID,
    val revision: Long,
    val state: CollaborationSpaceProvisioningState,
    val attempts: Int,
    val requestedAt: Instant,
    val availableAt: Instant,
    val expiresAt: Instant,
    val completedAt: Instant?,
    val failure: CollaborationSpaceRoomFailure?,
    val lastOutcome: CollaborationSpaceRoomOutcome?,
)
