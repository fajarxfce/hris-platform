package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*

/** Matrix's DM hint is a projection of immutable HRIS DIRECT ownership, not another registry. */
fun collaborationDirectMetadata(
    owner: CollaborationSyncOwner,
    scope: CollaborationRoomScope,
): Map<String, List<String>> =
    scope.rooms
        .groupBy {
            val peer =
                if (it.firstAccountId == owner.accountId) it.secondAccountId else it.firstAccountId
            "@${collaborationMatrixLocalpart(owner.companyId, peer)}:${owner.serverName}"
        }
        .mapValues { (_, rooms) -> rooms.map { it.roomId }.sorted() }
        .toSortedMap()

/** Copy and bound confirmation input before any pending acquisition. */
fun normalizeCollaborationDirectMetadata(
    value: Map<String, List<String>>
): Result<Map<String, List<String>>> {
    if (
        value.size > COLLABORATION_SYNC_ROOM_LIMIT ||
            value.values.sumOf { it.size.toLong() } > COLLABORATION_SYNC_ROOM_LIMIT ||
            value.any { (user, rooms) ->
                user.length !in 2..255 ||
                    !user.startsWith('@') ||
                    user.any { it.code !in 33..126 } ||
                    rooms.isEmpty() ||
                    rooms.distinct().size != rooms.size ||
                    rooms.any { room ->
                        room.length !in 2..255 ||
                            !room.startsWith('!') ||
                            room.any { it.code !in 33..126 }
                    }
            }
    )
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "invalid_collaboration_direct_metadata")
        )
    return Result.Success(value.mapValues { (_, rooms) -> rooms.sorted() }.toSortedMap())
}
