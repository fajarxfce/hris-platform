package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixRoomAccessRepository
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

class InternalCollaborationSpaceMatrixRoomAccessRepository(source: MatrixRoomAccessDataSource) :
    CollaborationSpaceMatrixRoomAccessRepository {
    private val reader = MatrixRoomAccessReader(source)

    override fun join(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceReadRoom,
        bearer: String,
        deadline: Instant,
    ): Result<String> = safeMatrixProviderCall {
        reader.join(frame(owner, room, deadline).roomId, bearer, deadline)
    }

    override fun receipt(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceReadRoom,
        receiptType: String,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ): Result<Unit> = safeMatrixProviderCall {
        reader.receipt(frame(owner, room, deadline).roomId, receiptType, eventId, bearer, deadline)
    }

    override fun state(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceReadRoom,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents> = safeMatrixProviderCall {
        reader.state(frame(owner, room, deadline), bearer, deadline)
    }

    override fun members(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceReadRoom,
        at: String?,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents> = safeMatrixProviderCall {
        reader.members(frame(owner, room, deadline), at, bearer, deadline)
    }

    private fun frame(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceReadRoom,
        deadline: Instant,
    ): MatrixRoomAccessFrame =
        when (room) {
            is CollaborationSpaceReadRoom.Published -> {
                val bound = room.value
                val snapshot =
                    snapshotCollaborationSpaceBoundRoom(
                        owner.spaceId,
                        owner.accountId,
                        owner.serverName,
                        bound.binding.roomId,
                        bound,
                        deadline,
                    )
                if (
                    snapshot !is Result.Success ||
                        snapshot.value != bound ||
                        !validCollaborationSpaceHistoryOwner(owner, bound)
                )
                    throw MatrixResponseException()
                val original = room.original
                if (
                    original != null &&
                        snapshotCollaborationSpaceReadScope(
                            owner,
                            listOf(bound),
                            listOf(original),
                            deadline,
                        ) is
                            Result.Failed
                )
                    throw MatrixResponseException()
                val intent = bound.intent
                MatrixRoomAccessFrame(
                    bound.binding.roomId,
                    setOf(intent.firstMatrixUserId, intent.secondMatrixUserId),
                    owner.matrixUserId,
                    if (intent.matrixCreatorAccountId == intent.firstAccountId)
                        intent.firstMatrixUserId
                    else intent.secondMatrixUserId,
                )
            }
            is CollaborationSpaceReadRoom.Original -> {
                val value = room.value
                if (
                    snapshotCollaborationSpaceOriginalReadRoom(owner, value, deadline)
                        is Result.Failed
                )
                    throw MatrixResponseException()
                val conversation = value.conversation
                val binding = requireNotNull(value.room)
                fun user(account: UUID) =
                    "@${collaborationMatrixLocalpart(conversation.companyId, account)}:${owner.serverName}"
                MatrixRoomAccessFrame(
                    binding.roomId,
                    setOf(user(conversation.firstAccountId), user(conversation.secondAccountId)),
                    owner.matrixUserId,
                    user(binding.createdBy),
                )
            }
        }
}
