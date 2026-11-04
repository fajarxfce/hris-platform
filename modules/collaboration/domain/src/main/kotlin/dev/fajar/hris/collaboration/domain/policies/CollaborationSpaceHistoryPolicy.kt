package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import java.util.UUID

/** Canonical coordinates only; metadata never substitutes for current device admission. */
fun validCollaborationSpaceHistoryIdentity(owner: CollaborationSpaceHistoryOwner): Boolean {
    fun compact(id: UUID) = id.toString().replace("-", "")
    if (
        listOf(owner.spaceId, owner.accountId, owner.deviceId).any { it == UUID(0, 0) } ||
            !validCollaborationSpaceServer(owner.serverName) ||
            owner.matrixUserId.length !in 1..255
    )
        return false
    val company = originalHistoryUserCompany(owner)
    val legacy = owner.legacy
    return if (legacy == null) {
        (company != null ||
            owner.matrixUserId ==
                "@${collaborationSpaceMatrixLocalpart(owner.spaceId, owner.accountId)}:${owner.serverName}") &&
            owner.matrixDeviceId == "HRISG_${compact(owner.deviceId)}"
    } else {
        legacy.companyId != UUID(0, 0) &&
            legacy.deviceId != UUID(0, 0) &&
            company == legacy.companyId &&
            owner.matrixDeviceId == "HRIS_${compact(legacy.deviceId)}"
    }
}

/** Exact original namespace encoded by an admitted canonical user, not a selected HR company. */
fun collaborationSpaceHistoryOriginalCompany(owner: CollaborationSpaceHistoryOwner): UUID? =
    if (validCollaborationSpaceHistoryIdentity(owner)) originalHistoryUserCompany(owner) else null

private fun originalHistoryUserCompany(owner: CollaborationSpaceHistoryOwner): UUID? {
    if (owner.matrixUserId.substringAfter(':', "") != owner.serverName) return null
    val account = owner.accountId.toString().replace("-", "")
    val match =
        Regex("@hris_([a-f0-9]{32})_$account").matchEntire(owner.matrixUserId.substringBefore(':'))
            ?: return null
    val value = match.groupValues[1]
    if (value == "0".repeat(32)) return null
    return UUID.fromString(
        "${value.substring(0, 8)}-${value.substring(8, 12)}-${value.substring(12, 16)}-${value.substring(16, 20)}-${value.substring(20)}"
    )
}

/**
 * Canonical protocol coordinates only; current native and completed-device admission is separate.
 */
fun validCollaborationSpaceHistoryOwner(
    owner: CollaborationSpaceHistoryOwner,
    room: CollaborationSpaceBoundRoom,
): Boolean {
    val intent = room.intent
    val binding = room.binding
    if (
        !validCollaborationSpaceHistoryIdentity(owner) ||
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
    return when (intent.origin) {
        CollaborationSpaceRoomOrigin.SPACE -> intent.original == null
        CollaborationSpaceRoomOrigin.LEGACY -> {
            val original = intent.original ?: return false
            original.companyId != UUID(0, 0) &&
                (legacy == null || original.companyId == legacy.companyId) &&
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
