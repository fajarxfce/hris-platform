package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.util.UUID

fun collaborationSpaceMatrixRoomAliasLocalpart(
    request: CollaborationSpaceMatrixDirectRoom
): String =
    "hrisg_dm_${request.spaceId.toString().replace("-", "")}_${request.conversationId.toString().replace("-", "")}"

fun validCollaborationSpaceRoomId(roomId: String): Boolean =
    roomId.length in 2..255 && roomId.startsWith('!') && roomId.all { it.code in 33..126 }

/** Canonical public coordinates only, not live group/room/device authority. */
fun validCollaborationSpaceRoomRequest(
    request: CollaborationSpaceMatrixDirectRoom,
    server: String,
): Boolean {
    fun user(account: UUID, value: String): Boolean {
        val compact = account.toString().replace("-", "")
        val space = request.spaceId.toString().replace("-", "")
        return value.length <= 255 &&
            value.substringAfter(':', "") == server &&
            (value.substringBefore(':') == "@hrisg_${space}_$compact" ||
                value.substringBefore(':').matches(Regex("@hris_[a-f0-9]{32}_$compact")))
    }
    return validCollaborationSpaceServer(server) &&
        listOf(
                request.spaceId,
                request.conversationId,
                request.creatorAccountId,
                request.peerAccountId,
            )
            .none { it == UUID(0, 0) } &&
        request.creatorAccountId != request.peerAccountId &&
        request.creatorUserId != request.peerUserId &&
        user(request.creatorAccountId, request.creatorUserId) &&
        user(request.peerAccountId, request.peerUserId)
}

/**
 * Only fill missing initial state on a matching creator's room. Never downgrade/adopt alien state.
 */
fun assessCollaborationSpaceDirectRoom(
    request: CollaborationSpaceMatrixDirectRoom,
    observed: CollaborationSpaceMatrixRoomObservation,
    serverName: String,
): Result<CollaborationMatrixRoomRepair> {
    fun rejected() = Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_mismatch"))
    val creator = request.creatorUserId
    val peer = request.peerUserId
    if (
        !validCollaborationSpaceRoomRequest(request, serverName) ||
            observed.creator != creator ||
            observed.roomVersion != "12" ||
            observed.federated != false ||
            observed.additionalCreators.isNotEmpty() ||
            observed.tombstoned ||
            observed.marker !=
                CollaborationSpaceMatrixRoomMarker(
                    request.spaceId.toString(),
                    request.conversationId.toString(),
                    "DIRECT",
                ) ||
            observed.members[creator] != "join" ||
            observed.members.keys.any { it != creator && it != peer } ||
            observed.members[peer]?.let { it !in setOf("invite", "join") } == true
    )
        return rejected()
    val missing = mutableSetOf<CollaborationMatrixRoomSetting>()
    val strings =
        listOf(
            Triple(
                CollaborationMatrixRoomSetting.ENCRYPTION,
                observed.encryptionAlgorithm,
                "m.megolm.v1.aes-sha2",
            ),
            Triple(CollaborationMatrixRoomSetting.JOIN_RULES, observed.joinRule, "invite"),
            Triple(
                CollaborationMatrixRoomSetting.HISTORY_VISIBILITY,
                observed.historyVisibility,
                "invited",
            ),
            Triple(CollaborationMatrixRoomSetting.GUEST_ACCESS, observed.guestAccess, "forbidden"),
        )
    for ((setting, value, expected) in strings) {
        if (value == null) missing += setting else if (value != expected) return rejected()
    }
    if (observed.power == null) missing += CollaborationMatrixRoomSetting.POWER_LEVELS
    else if (observed.power != collaborationDirectRoomPower()) return rejected()
    return Result.Success(
        CollaborationMatrixRoomRepair(missing.toSet(), observed.members[peer] == null)
    )
}
