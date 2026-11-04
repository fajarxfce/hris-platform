package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*

/** Pure DM hints from an already validated complete original/published read scope. */
fun collaborationSpaceDirectMetadata(
    owner: CollaborationSpaceHistoryOwner,
    scope: CollaborationSpaceReadScope,
): Map<String, List<String>> =
    scope.rooms
        .groupBy { room ->
            when (room) {
                is CollaborationSpaceReadRoom.Published -> {
                    val intent = room.value.intent
                    if (intent.firstAccountId == owner.accountId) intent.secondMatrixUserId
                    else intent.firstMatrixUserId
                }
                is CollaborationSpaceReadRoom.Original -> {
                    val conversation = room.value.conversation
                    val peer =
                        if (conversation.firstAccountId == owner.accountId)
                            conversation.secondAccountId
                        else conversation.firstAccountId
                    "@${collaborationMatrixLocalpart(conversation.companyId, peer)}:${owner.serverName}"
                }
            }
        }
        .mapValues { (_, rooms) -> rooms.map { it.roomId }.sorted() }
        .toSortedMap()
