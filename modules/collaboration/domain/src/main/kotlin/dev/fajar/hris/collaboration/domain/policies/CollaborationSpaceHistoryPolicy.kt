package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import java.util.UUID

/**
 * Canonical protocol coordinates only; current native and completed-device admission is separate.
 */
fun validCollaborationSpaceHistoryOwner(
    owner: CollaborationSpaceHistoryOwner,
    room: CollaborationSpaceBoundRoom,
): Boolean {
    fun compact(id: UUID) = id.toString().replace("-", "")
    val intent = room.intent
    val binding = room.binding
    if (
        listOf(owner.spaceId, owner.accountId, owner.deviceId).any { it == UUID(0, 0) } ||
            owner.spaceId != intent.spaceId ||
            owner.spaceId != binding.spaceId ||
            owner.serverName != intent.serverName ||
            binding.conversationId != intent.conversationId ||
            binding.matrixCreatorAccountId != intent.matrixCreatorAccountId ||
            owner.accountId !in setOf(intent.firstAccountId, intent.secondAccountId) ||
            intent.matrixCreatorAccountId !in
                setOf(intent.firstAccountId, intent.secondAccountId) ||
            !validCollaborationSpaceRoomId(binding.roomId) ||
            !validCollaborationSpaceRoomRequest(
                CollaborationSpaceMatrixDirectRoom(
                    intent.spaceId,
                    intent.conversationId,
                    intent.firstAccountId,
                    intent.secondAccountId,
                    intent.firstMatrixUserId,
                    intent.secondMatrixUserId,
                ),
                owner.serverName,
            )
    )
        return false
    val originalUser =
        if (owner.accountId == intent.firstAccountId) intent.firstMatrixUserId
        else intent.secondMatrixUserId
    if (owner.matrixUserId != originalUser) return false
    val legacy = owner.legacy
    if (legacy == null) {
        if (
            owner.matrixUserId !=
                "@hrisg_${compact(owner.spaceId)}_${compact(owner.accountId)}:${owner.serverName}" ||
                owner.matrixDeviceId != "HRISG_${compact(owner.deviceId)}"
        )
            return false
    } else if (
        legacy.companyId == UUID(0, 0) ||
            legacy.deviceId == UUID(0, 0) ||
            owner.matrixUserId !=
                "@${collaborationMatrixLocalpart(legacy.companyId, owner.accountId)}:${owner.serverName}" ||
            owner.matrixDeviceId != "HRIS_${compact(legacy.deviceId)}"
    )
        return false
    return when (intent.origin) {
        CollaborationSpaceRoomOrigin.SPACE -> intent.original == null
        CollaborationSpaceRoomOrigin.LEGACY -> {
            val original = intent.original ?: return false
            legacy != null &&
                original.companyId == legacy.companyId &&
                original.conversationId != UUID(0, 0) &&
                original.roomId == binding.roomId &&
                intent.firstMatrixUserId ==
                    "@${collaborationMatrixLocalpart(original.companyId, intent.firstAccountId)}:${owner.serverName}" &&
                intent.secondMatrixUserId ==
                    "@${collaborationMatrixLocalpart(original.companyId, intent.secondAccountId)}:${owner.serverName}"
        }
    }
}

/** An exact original company namespace, never a group UUID substituted for a company. */
fun collaborationSpaceLegacyHistoryOwner(
    owner: CollaborationSpaceHistoryOwner,
    room: CollaborationSpaceBoundRoom,
): CollaborationSyncOwner? {
    if (!validCollaborationSpaceHistoryOwner(owner, room)) return null
    if (room.intent.origin != CollaborationSpaceRoomOrigin.LEGACY) return null
    val original = owner.legacy ?: return null
    return CollaborationSyncOwner(
        original.companyId,
        owner.accountId,
        original.deviceId,
        owner.serverName,
    )
}
