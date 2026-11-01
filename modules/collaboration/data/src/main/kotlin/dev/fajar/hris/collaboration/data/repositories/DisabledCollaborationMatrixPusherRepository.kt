package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixPusherRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationMatrixPusherRepository : CollaborationMatrixPusherRepository {
    override fun set(pushKey: String, bearer: String, deadline: Instant): Result<Unit> =
        unavailable()

    override fun remove(pushKey: String, bearer: String, deadline: Instant): Result<Unit> =
        unavailable()

    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_push_unavailable"))
}
