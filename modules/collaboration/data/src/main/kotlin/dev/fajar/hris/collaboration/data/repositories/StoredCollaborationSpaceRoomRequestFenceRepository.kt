package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceRoomRequestFenceDataSource
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomRequestFence
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceRoomRequestFenceRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import java.util.UUID

class StoredCollaborationSpaceRoomRequestFenceRepository(
    private val source: CollaborationSpaceRoomRequestFenceDataSource
) : CollaborationSpaceRoomRequestFenceRepository {
    override fun find(
        accountId: UUID,
        operationId: UUID,
    ): Result<CollaborationSpaceRoomRequestFence?> = safeDatabaseCall {
        source.find(accountId, operationId)?.toRoomRequestFence()?.also {
            require(it.accountId == accountId && it.original.operationId == operationId)
        }
    }

    override fun record(value: CollaborationSpaceRoomRequestFence): Result<Unit> =
        safeDatabaseCall {
            source.insert(value.toRoomRequestFenceRecord())
        }
}
