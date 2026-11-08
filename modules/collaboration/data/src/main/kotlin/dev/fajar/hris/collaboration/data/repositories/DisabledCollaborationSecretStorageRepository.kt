package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationSecretStorageDefaultKey
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSecretStorageRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationSecretStorageRepository : CollaborationSecretStorageRepository {
    override fun defaultKey(
        userId: String,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationSecretStorageDefaultKey?> =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
}
