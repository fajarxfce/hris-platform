package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant

interface CollaborationMatrixSyncRepository {
    /**
     * Caller authorizes the exact retained scope before and after this bounded external exchange.
     */
    fun sync(
        owner: CollaborationSyncOwner,
        scope: CollaborationRoomScope,
        position: CollaborationSyncPosition?,
        timeoutMillis: Int,
        fullState: Boolean,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationMatrixSyncBatch>
}
