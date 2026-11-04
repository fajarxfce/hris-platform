package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixRoomHistoryRepository
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import java.time.Instant

class InternalCollaborationSpaceMatrixRoomHistoryRepository(
    private val source: MatrixRoomHistoryDataSource,
    private val crypto: IdentityTokenDataSource,
) : CollaborationSpaceMatrixRoomHistoryRepository {
    private val reader = MatrixRoomHistoryReader(source)

    override fun messages(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceBoundRoom,
        request: CollaborationRoomPageRequest,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomHistory> = safeMatrixProviderCall {
        reader.messages(frame(owner, room), request, bearer, deadline)
    }

    override fun context(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceBoundRoom,
        eventId: String,
        limit: Int,
        bearer: String,
        deadline: Instant,
        filter: CollaborationRoomHistoryFilter,
    ): Result<CollaborationRoomHistory> = safeMatrixProviderCall {
        reader.context(frame(owner, room), eventId, limit, bearer, deadline, filter)
    }

    override fun event(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceBoundRoom,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomHistory> = safeMatrixProviderCall {
        reader.event(frame(owner, room), eventId, bearer, deadline)
    }

    private fun frame(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceBoundRoom,
    ): MatrixRoomHistoryFrame {
        if (!validCollaborationSpaceHistoryOwner(owner, room)) throw MatrixResponseException()
        val intent = room.intent
        return MatrixRoomHistoryFrame(
            room.binding.roomId,
            setOf(intent.firstMatrixUserId, intent.secondMatrixUserId),
            owner.matrixUserId,
            if (intent.matrixCreatorAccountId == intent.firstAccountId) intent.firstMatrixUserId
            else intent.secondMatrixUserId,
            object : MatrixRoomHistoryCursors {
                override fun open(cursor: String) =
                    openMatrixSpaceRoomCursor(crypto, owner, room, cursor)

                override fun seal(position: String) =
                    sealMatrixSpaceRoomCursor(crypto, owner, room, position)
            },
        )
    }
}
