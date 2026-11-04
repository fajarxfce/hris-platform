package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixLocalpart
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRoomAccessRepository
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

class InternalCollaborationMatrixRoomAccessRepository(source: MatrixRoomAccessDataSource) :
    CollaborationMatrixRoomAccessRepository {
    private val reader = MatrixRoomAccessReader(source)

    override fun join(
        room: CollaborationOwnedRoom,
        bearer: String,
        deadline: Instant,
    ): Result<String> = safeMatrixProviderCall { reader.join(room.roomId, bearer, deadline) }

    override fun receipt(
        room: CollaborationOwnedRoom,
        receiptType: String,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ): Result<Unit> = safeMatrixProviderCall {
        reader.receipt(room.roomId, receiptType, eventId, bearer, deadline)
    }

    override fun state(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents> = safeMatrixProviderCall {
        reader.state(frame(owner, room), bearer, deadline)
    }

    override fun members(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        at: String?,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents> = safeMatrixProviderCall {
        reader.members(frame(owner, room), at, bearer, deadline)
    }

    private fun frame(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
    ): MatrixRoomAccessFrame {
        fun user(account: UUID) =
            "@${collaborationMatrixLocalpart(owner.companyId, account)}:${owner.serverName}"
        return MatrixRoomAccessFrame(
            room.roomId,
            setOf(user(room.firstAccountId), user(room.secondAccountId)),
            user(owner.accountId),
            user(room.creatorAccountId),
        )
    }
}
