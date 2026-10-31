package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationRoomScopeDataSource
import dev.fajar.hris.collaboration.domain.entities.CollaborationOwnedRoom
import dev.fajar.hris.collaboration.domain.repositories.CollaborationRoomScopeRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import java.util.UUID

class StoredCollaborationRoomScopeRepository(private val source: CollaborationRoomScopeDataSource) :
    CollaborationRoomScopeRepository {
    override fun owned(
        companyId: UUID,
        accountId: UUID,
        limit: Int,
    ): Result<List<CollaborationOwnedRoom>> = safeDatabaseCall {
        source.owned(companyId, accountId, limit).map {
            CollaborationOwnedRoom(
                it.companyId,
                it.conversationId,
                it.firstAccountId,
                it.secondAccountId,
                it.creatorAccountId,
                it.serverName,
                it.roomId,
            )
        }
    }
}
