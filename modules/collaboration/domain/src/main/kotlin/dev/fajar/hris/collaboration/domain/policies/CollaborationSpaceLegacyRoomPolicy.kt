package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID

/** Original provider coordinates only. Current native/group admission belongs to the use case. */
fun prepareCollaborationSpaceLegacyRoom(
    group: CollaborationSpaceDirectConversation,
    first: CollaborationSpaceMember,
    second: CollaborationSpaceMember,
    original: CollaborationSpaceLegacyConversation,
    serverName: String,
    now: Instant,
): Result<CollaborationOwnedRoom> {
    val conversation = original.conversation
    val room = original.room ?: return legacyRoomMismatch()
    val firstIdentity = first.identity ?: return legacyRoomMismatch()
    val secondIdentity = second.identity ?: return legacyRoomMismatch()
    val pair = setOf(group.firstAccountId, group.secondAccountId)
    fun finite(at: Instant) = at.nano % 1000 == 0 && at.epochSecond in -62135596800L..253402300799L
    if (
        listOf(
                group.spaceId,
                group.id,
                group.firstAccountId,
                group.secondAccountId,
                conversation.companyId,
                conversation.id,
            )
            .any { it == UUID(0, 0) } ||
            group.firstAccountId.toString() >= group.secondAccountId.toString() ||
            group.createdBy !in pair ||
            group.version != 0L ||
            first.spaceId != group.spaceId ||
            second.spaceId != group.spaceId ||
            original.spaceId != group.spaceId ||
            first.accountId != group.firstAccountId ||
            second.accountId != group.secondAccountId ||
            conversation.firstAccountId != group.firstAccountId ||
            conversation.secondAccountId != group.secondAccountId ||
            conversation.createdBy !in pair ||
            conversation.version != 0L ||
            room.createdBy !in pair ||
            !validCollaborationSpaceServer(serverName) ||
            room.serverName != serverName ||
            !validCollaborationSpaceRoomId(room.roomId) ||
            listOf(
                    group.createdAt,
                    conversation.createdAt,
                    room.boundAt,
                    firstIdentity.selectedAt,
                    secondIdentity.selectedAt,
                )
                .any { !finite(it) || it > now } ||
            room.boundAt < conversation.createdAt
    )
        return legacyRoomMismatch()
    fun matches(account: UUID, identity: CollaborationSpacePublicIdentity) =
        identity.serverName == serverName &&
            identity.userId ==
                "@${collaborationMatrixLocalpart(conversation.companyId, account)}:$serverName"
    if (!matches(first.accountId, firstIdentity) || !matches(second.accountId, secondIdentity))
        return legacyRoomMismatch()
    return Result.Success(
        CollaborationOwnedRoom(
            conversation.companyId,
            conversation.id,
            conversation.firstAccountId,
            conversation.secondAccountId,
            room.createdBy,
            serverName,
            room.roomId,
        )
    )
}

/**
 * Historical rooms must already be healthy. Missing state never authorizes repair or replacement.
 */
fun assessCollaborationSpaceLegacyRoom(
    room: CollaborationOwnedRoom,
    observed: CollaborationMatrixRoomObservation,
): Result<Unit> {
    if (
        !validCollaborationSpaceRoomId(room.roomId) ||
            !validCollaborationSpaceServer(room.serverName) ||
            listOf(room.companyId, room.conversationId, room.firstAccountId, room.secondAccountId)
                .any { it == UUID(0, 0) } ||
            room.firstAccountId.toString() >= room.secondAccountId.toString() ||
            room.creatorAccountId !in setOf(room.firstAccountId, room.secondAccountId)
    )
        return legacyRoomMismatch()
    val peer =
        if (room.creatorAccountId == room.firstAccountId) room.secondAccountId
        else room.firstAccountId
    return assessCollaborationDirectRoom(
            CollaborationMatrixDirectRoom(
                room.companyId,
                room.conversationId,
                room.creatorAccountId,
                peer,
            ),
            observed,
            room.serverName,
        )
        .flatMap {
            if (it.missingSettings.isEmpty() && !it.missingInvite) Result.Success(Unit)
            else legacyRoomMismatch()
        }
}

private fun legacyRoomMismatch() =
    Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_mismatch"))
