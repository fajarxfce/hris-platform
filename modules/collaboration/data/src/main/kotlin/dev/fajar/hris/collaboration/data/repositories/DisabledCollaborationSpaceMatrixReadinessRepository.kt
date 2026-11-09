package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceHistoryOwner
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceReadinessControl
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixReadinessRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationSpaceMatrixReadinessRepository :
    CollaborationSpaceMatrixReadinessRepository {
    override fun send(
        control: CollaborationSpaceReadinessControl,
        owner: CollaborationSpaceHistoryOwner,
        token: String,
        deadline: Instant,
    ): Result<Unit> =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_space_readiness_unavailable"))
}
