package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

data class CollaborationMatrixDirectRoom(
    val companyId: UUID,
    val conversationId: UUID,
    val creatorAccountId: UUID,
    val peerAccountId: UUID,
)

sealed interface CollaborationMatrixRoomCreation {
    data class Created(val roomId: String) : CollaborationMatrixRoomCreation

    data object AliasInUse : CollaborationMatrixRoomCreation
}

enum class CollaborationMatrixRoomSetting {
    POWER_LEVELS,
    ENCRYPTION,
    JOIN_RULES,
    HISTORY_VISIBILITY,
    GUEST_ACCESS,
}

data class CollaborationMatrixRoomMarker(
    val companyId: String,
    val conversationId: String,
    val kind: String,
)

data class CollaborationMatrixRoomPower(
    val users: Map<String, Long>,
    val events: Map<String, Long>,
    val usersDefault: Long,
    val eventsDefault: Long,
    val stateDefault: Long,
    val invite: Long,
    val kick: Long,
    val ban: Long,
    val redact: Long,
)

/**
 * Bounded protocol observation only. HRIS authority and a durable publication fence are separate.
 */
data class CollaborationMatrixRoomObservation(
    val creator: String?,
    val roomVersion: String?,
    val federated: Boolean?,
    val marker: CollaborationMatrixRoomMarker?,
    val additionalCreators: List<String>,
    val tombstoned: Boolean,
    val encryptionAlgorithm: String?,
    val joinRule: String?,
    val historyVisibility: String?,
    val guestAccess: String?,
    val power: CollaborationMatrixRoomPower?,
    val members: Map<String, String>,
)
