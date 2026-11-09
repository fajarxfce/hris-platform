package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

/** One current observation, not a reusable send grant or a provider credential. */
data class CollaborationSpaceDirectTransport(
    val spaceId: UUID,
    val accountId: UUID,
    val nativeSessionId: UUID,
    val nativeSessionVersion: Long,
    val conversation: CollaborationSpaceDirectConversation,
    val device: CollaborationSpaceDeviceStatus,
    val matrixUserId: String,
    val matrixDeviceId: String,
    val room: CollaborationSpacePublishedRoom,
    val recipientReady: Boolean,
) {
    override fun toString() = "CollaborationSpaceDirectTransport(<metadata>)"
}
