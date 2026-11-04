package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixLocalpart
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRoomHistoryRepository
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import java.time.Instant
import java.util.UUID

class InternalCollaborationMatrixRoomHistoryRepository(
    private val source: MatrixRoomHistoryDataSource,
    private val crypto: IdentityTokenDataSource,
) : CollaborationMatrixRoomHistoryRepository {
    private val reader = MatrixRoomHistoryReader(source)

    override fun messages(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        request: CollaborationRoomPageRequest,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomHistory> = safeMatrixProviderCall {
        reader.messages(frame(owner, room), request, bearer, deadline)
    }

    override fun context(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        eventId: String,
        limit: Int,
        bearer: String,
        deadline: Instant,
        filter: CollaborationRoomHistoryFilter,
    ): Result<CollaborationRoomHistory> = safeMatrixProviderCall {
        reader.context(frame(owner, room), eventId, limit, bearer, deadline, filter)
    }

    override fun event(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomHistory> = safeMatrixProviderCall {
        reader.event(frame(owner, room), eventId, bearer, deadline)
    }

    private fun frame(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
    ): MatrixRoomHistoryFrame {
        fun user(account: UUID) =
            "@${collaborationMatrixLocalpart(owner.companyId, account)}:${owner.serverName}"
        return MatrixRoomHistoryFrame(
            room.roomId,
            setOf(user(room.firstAccountId), user(room.secondAccountId)),
            user(owner.accountId),
            user(room.creatorAccountId),
            object : MatrixRoomHistoryCursors {
                override fun open(cursor: String) =
                    openMatrixRoomCursor(crypto, owner, room.roomId, cursor)

                override fun seal(position: String) =
                    sealMatrixRoomCursor(crypto, owner, room.roomId, position)
            },
        )
    }
}
