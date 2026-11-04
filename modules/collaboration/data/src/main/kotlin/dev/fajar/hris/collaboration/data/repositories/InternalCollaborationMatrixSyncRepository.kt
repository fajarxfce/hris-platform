package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixSyncRepository
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import java.time.Instant
import java.util.UUID

/** Fixed company DIRECT framing; provider filters are an optimization, never ACLs. */
class InternalCollaborationMatrixSyncRepository(
    source: MatrixSyncDataSource,
    private val crypto: IdentityTokenDataSource,
) : CollaborationMatrixSyncRepository {
    private val reader = MatrixSyncReader(source)

    override fun sync(
        owner: CollaborationSyncOwner,
        scope: CollaborationRoomScope,
        position: CollaborationSyncPosition?,
        timeoutMillis: Int,
        fullState: Boolean,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationMatrixSyncBatch> = safeMatrixProviderCall {
        fun user(account: UUID) =
            "@${collaborationMatrixLocalpart(owner.companyId, account)}:${owner.serverName}"
        val frame =
            MatrixSyncFrame(
                user(owner.accountId),
                collaborationMatrixDeviceId(owner.deviceId),
                scope.rooms.associate {
                    it.roomId to
                        MatrixSyncRoomFrame(
                            setOf(user(it.firstAccountId), user(it.secondAccountId)),
                            user(it.creatorAccountId),
                        )
                },
                collaborationDirectMetadata(owner, scope),
            ) { roomId, privatePosition ->
                sealMatrixRoomCursor(crypto, owner, roomId, privatePosition)
            }
        reader.sync(frame, position, timeoutMillis, fullState, bearer, deadline)
    }
}
