package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixCredential
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceMatrixLogin
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixRepository
import dev.fajar.hris.core.domain.*

class DisabledCollaborationSpaceMatrixRepository : CollaborationSpaceMatrixRepository {
    override fun login(
        request: CollaborationSpaceMatrixLogin
    ): Result<CollaborationMatrixCredential> =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
}
