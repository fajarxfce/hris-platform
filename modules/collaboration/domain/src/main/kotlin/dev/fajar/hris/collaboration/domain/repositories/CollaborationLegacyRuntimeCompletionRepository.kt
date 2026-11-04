package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result

interface CollaborationLegacyRuntimeCompletionRepository {
    /** Owned metadata only; a completed mapping cannot waive current admission. */
    fun find(owner: CollaborationLegacyRuntimeOwner): Result<CollaborationLegacyRuntimeCompletion?>

    /**
     * Worker capability only. Caller owns the same transaction as group credential/device/queue and
     * journal publication. Any failure, including final mapping readback, must roll it back.
     */
    fun complete(
        request: CompleteCollaborationLegacyRuntimeRequest
    ): Result<CollaborationLegacyRuntimeCompletion>
}
