package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

enum class CollaborationSpaceIdentityOrigin {
    LEGACY,
    SPACE,
}

/** Immutable provider-user intent. No credential, device or room authority is conveyed. */
data class CollaborationSpaceIdentity(
    val spaceId: UUID,
    val accountId: UUID,
    val serverName: String,
    val matrixUserId: String,
    val origin: CollaborationSpaceIdentityOrigin,
    val legacy: CollaborationLegacyDevicePosition?,
    val createdAt: Instant,
    val version: Long = 0,
) {
    override fun toString() = "CollaborationSpaceIdentity(<metadata>)"
}

/** A single eligible statement snapshot; null repository output means unavailable scope. */
data class CollaborationSpaceIdentityInput(
    val spaceId: UUID,
    val accountId: UUID,
    val selected: CollaborationSpaceIdentity?,
    val preferred: CollaborationLegacyDevice?,
)
