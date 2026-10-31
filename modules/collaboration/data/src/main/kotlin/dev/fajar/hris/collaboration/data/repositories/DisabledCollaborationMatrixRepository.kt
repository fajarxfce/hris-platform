package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixLogin
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationMatrixRepository : CollaborationMatrixRepository {
    override fun login(request: CollaborationMatrixLogin) = unavailable()

    override fun logoutDevice(accessToken: String) = unavailable()

    override fun whoAmI(accessToken: String, deadline: Instant) = unavailable()

    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
}
