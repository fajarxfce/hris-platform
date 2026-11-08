package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationSecretStorageDefaultKey
import dev.fajar.hris.core.domain.Result
import java.time.Instant

/** Read-only provider probe. Missing data is distinct from denial or provider failure. */
interface CollaborationSecretStorageRepository {
    fun defaultKey(
        userId: String,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationSecretStorageDefaultKey?>
}
