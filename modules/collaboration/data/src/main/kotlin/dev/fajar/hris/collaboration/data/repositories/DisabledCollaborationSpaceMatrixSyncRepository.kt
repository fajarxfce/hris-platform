package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixSyncRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationSpaceMatrixSyncRepository : CollaborationSpaceMatrixSyncRepository {
    override fun sync(
        owner: CollaborationSpaceHistoryOwner,
        scope: CollaborationSpaceReadScope,
        position: CollaborationSyncPosition?,
        timeoutMillis: Int,
        fullState: Boolean,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationMatrixSyncBatch> =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
}
