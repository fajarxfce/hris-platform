package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixSyncRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationMatrixSyncRepository : CollaborationMatrixSyncRepository {
    override fun sync(
        owner: CollaborationSyncOwner,
        scope: CollaborationRoomScope,
        position: CollaborationSyncPosition?,
        timeoutMillis: Int,
        fullState: Boolean,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationMatrixSyncBatch> =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
}
