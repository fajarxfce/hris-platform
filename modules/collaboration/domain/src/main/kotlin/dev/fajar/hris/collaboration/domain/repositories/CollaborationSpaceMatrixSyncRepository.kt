package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant

/**
 * Fixed read protocol only; the caller owns native admission and complete inventory revalidation.
 */
interface CollaborationSpaceMatrixSyncRepository {
    fun sync(
        owner: CollaborationSpaceHistoryOwner,
        scope: CollaborationSpaceReadScope,
        position: CollaborationSyncPosition?,
        timeoutMillis: Int,
        fullState: Boolean,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationMatrixSyncBatch>
}
