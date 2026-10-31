package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixKeyRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationMatrixKeyRepository : CollaborationMatrixKeyRepository {
    override fun ownDeviceKeys(
        userId: String,
        deviceId: String,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixDeviceKeys?> = unavailable()

    override fun upload(
        request: CollaborationMatrixKeyUpload,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixKeyCounts> = unavailable()

    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
}
