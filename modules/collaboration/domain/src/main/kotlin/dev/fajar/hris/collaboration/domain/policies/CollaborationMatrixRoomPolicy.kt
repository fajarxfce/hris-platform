package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*

fun collaborationMatrixRoomAliasLocalpart(request: CollaborationMatrixDirectRoom): String =
    "hris_${request.companyId.toString().replace("-", "")}_${request.conversationId.toString().replace("-", "")}"

fun collaborationDirectRoomPower() =
    CollaborationMatrixRoomPower(
        emptyMap(),
        mapOf(
            "m.room.name" to 100L,
            "m.room.power_levels" to 100L,
            "m.room.history_visibility" to 100L,
            "m.room.canonical_alias" to 100L,
            "m.room.avatar" to 100L,
            "m.room.tombstone" to 150L,
            "m.room.server_acl" to 100L,
            "m.room.encryption" to 100L,
        ),
        0,
        0,
        100,
        100,
        100,
        100,
        100,
    )

data class CollaborationMatrixRoomRepair(
    val missingSettings: Set<CollaborationMatrixRoomSetting>,
    val missingInvite: Boolean,
)

/**
 * Only fill missing initial state on a matching creator's room. Never downgrade/adopt alien state.
 */
fun assessCollaborationDirectRoom(
    request: CollaborationMatrixDirectRoom,
    observed: CollaborationMatrixRoomObservation,
    serverName: String,
): Result<CollaborationMatrixRoomRepair> {
    fun rejected() = Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_mismatch"))
    val creator =
        "@${collaborationMatrixLocalpart(request.companyId, request.creatorAccountId)}:$serverName"
    val peer =
        "@${collaborationMatrixLocalpart(request.companyId, request.peerAccountId)}:$serverName"
    if (
        request.creatorAccountId == request.peerAccountId ||
            observed.creator != creator ||
            observed.roomVersion != "12" ||
            observed.federated != false ||
            observed.additionalCreators.isNotEmpty() ||
            observed.tombstoned ||
            observed.marker !=
                CollaborationMatrixRoomMarker(
                    request.companyId.toString(),
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
