package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixSyncRepository
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import java.time.Instant
import java.util.UUID

class InternalCollaborationSpaceMatrixSyncRepository(
    source: MatrixSyncDataSource,
    private val crypto: IdentityTokenDataSource,
) : CollaborationSpaceMatrixSyncRepository {
    private val reader = MatrixSyncReader(source)

    override fun sync(
        owner: CollaborationSpaceHistoryOwner,
        scope: CollaborationSpaceReadScope,
        position: CollaborationSyncPosition?,
        timeoutMillis: Int,
        fullState: Boolean,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationMatrixSyncBatch> = safeMatrixProviderCall {
        if (scope.rooms.size > COLLABORATION_SYNC_ROOM_LIMIT) throw MatrixResponseException()
        val groups =
            scope.rooms.filterIsInstance<CollaborationSpaceReadRoom.Published>().map { it.value }
        val originals =
            scope.rooms.mapNotNull {
                when (it) {
                    is CollaborationSpaceReadRoom.Published -> it.original
                    is CollaborationSpaceReadRoom.Original -> it.value
                }
            }
        val checked = snapshotCollaborationSpaceReadScope(owner, groups, originals, deadline)
        if (checked !is Result.Success || checked.value != scope) throw MatrixResponseException()
        val rooms = scope.rooms.associateBy { it.roomId }
        val frames =
            rooms.mapValues { (_, room) ->
                when (room) {
                    is CollaborationSpaceReadRoom.Published -> {
                        val intent = room.value.intent
                        MatrixSyncRoomFrame(
                            setOf(intent.firstMatrixUserId, intent.secondMatrixUserId),
                            if (intent.matrixCreatorAccountId == intent.firstAccountId)
                                intent.firstMatrixUserId
                            else intent.secondMatrixUserId,
                        )
                    }
                    is CollaborationSpaceReadRoom.Original -> {
                        val conversation = room.value.conversation
                        fun user(account: UUID) =
                            "@${collaborationMatrixLocalpart(conversation.companyId, account)}:${owner.serverName}"
                        MatrixSyncRoomFrame(
                            setOf(
                                user(conversation.firstAccountId),
                                user(conversation.secondAccountId),
                            ),
                            user(requireNotNull(room.value.room).createdBy),
                        )
                    }
                }
            }
        val direct = collaborationSpaceDirectMetadata(owner, scope)
        val frame =
            MatrixSyncFrame(owner.matrixUserId, owner.matrixDeviceId, frames, direct) {
                id,
                privatePosition ->
                when (val room = requireNotNull(rooms[id])) {
                    is CollaborationSpaceReadRoom.Published ->
                        sealMatrixSpaceRoomCursor(crypto, owner, room.value, privatePosition)
                    is CollaborationSpaceReadRoom.Original ->
                        sealMatrixSpaceRoomCursor(crypto, owner, room.value, privatePosition)
                }
            }
        reader.sync(frame, position, timeoutMillis, fullState, bearer, deadline)
    }
}
