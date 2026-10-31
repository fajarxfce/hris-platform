package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationToDevice
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixToDeviceRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationMatrixToDeviceRepository : CollaborationMatrixToDeviceRepository {
    override fun send(
        request: CollaborationToDevice,
        token: String,
        deadline: Instant,
    ): Result<Unit> = Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
}
