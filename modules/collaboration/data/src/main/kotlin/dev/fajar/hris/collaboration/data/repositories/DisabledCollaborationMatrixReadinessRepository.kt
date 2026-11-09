package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationReadinessControl
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixReadinessRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationMatrixReadinessRepository : CollaborationMatrixReadinessRepository {
    override fun send(
        control: CollaborationReadinessControl,
        serverName: String,
        token: String,
        deadline: Instant,
    ): Result<Unit> =
        Result.Failed(
            Failure(FailureKind.UNAVAILABLE, "collaboration_readiness_control_unavailable")
        )
}
