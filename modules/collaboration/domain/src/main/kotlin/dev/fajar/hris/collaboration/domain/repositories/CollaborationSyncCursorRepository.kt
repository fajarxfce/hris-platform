package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationSyncOwner
import dev.fajar.hris.collaboration.domain.entities.CollaborationSyncPosition
import dev.fajar.hris.core.domain.Result

interface CollaborationSyncCursorRepository {
    fun seal(owner: CollaborationSyncOwner, position: CollaborationSyncPosition): Result<String>

    fun open(owner: CollaborationSyncOwner, cursor: String): Result<CollaborationSyncPosition>
}
