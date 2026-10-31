package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationOwnedRoom
import dev.fajar.hris.core.domain.Result
import java.util.UUID

interface CollaborationRoomScopeRepository {
    /** One bounded statement under the caller's company/participant RLS transaction. */
    fun owned(companyId: UUID, accountId: UUID, limit: Int): Result<List<CollaborationOwnedRoom>>
}
