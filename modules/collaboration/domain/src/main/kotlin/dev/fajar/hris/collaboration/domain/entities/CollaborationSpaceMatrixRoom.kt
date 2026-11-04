package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

data class CollaborationSpaceMatrixDirectRoom(
    val spaceId: UUID,
    val conversationId: UUID,
    val creatorAccountId: UUID,
    val peerAccountId: UUID,
    val creatorUserId: String,
    val peerUserId: String,
)

data class CollaborationSpaceMatrixRoomMarker(
    val spaceId: String,
    val conversationId: String,
    val kind: String,
)

/**
 * Bounded protocol observation only. HRIS authority and a durable publication fence are separate.
 */
data class CollaborationSpaceMatrixRoomObservation(
    val creator: String?,
    val roomVersion: String?,
    val federated: Boolean?,
    val marker: CollaborationSpaceMatrixRoomMarker?,
    val additionalCreators: List<String>,
    val tombstoned: Boolean,
    val encryptionAlgorithm: String?,
    val joinRule: String?,
    val historyVisibility: String?,
    val guestAccess: String?,
    val power: CollaborationMatrixRoomPower?,
    val members: Map<String, String>,
)
