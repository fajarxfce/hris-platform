package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

/** Explicit receipt protocols; current authentication is admitted separately from either key. */
sealed interface CollaborationSpaceRoomReceiptDescriptor {
    val operationId: UUID
    val spaceId: UUID
    val conversationId: UUID
    val expectedAttempt: CollaborationSpaceRoomObservedAttempt?
}

enum class CollaborationSpaceRoomRequestFormat {
    NATIVE_PROOF,
    STABLE_INTENT,
}

/** Stable business intent. Credential rotation and app upgrades cannot rewrite this fingerprint. */
data class CollaborationSpaceRoomRequestIntent(
    override val operationId: UUID,
    override val spaceId: UUID,
    override val conversationId: UUID,
    val deviceId: UUID,
    val expectedDeviceVersion: Long,
    override val expectedAttempt: CollaborationSpaceRoomObservedAttempt?,
) : CollaborationSpaceRoomReceiptDescriptor {
    override fun toString() = "CollaborationSpaceRoomRequestIntent([metadata])"
}
