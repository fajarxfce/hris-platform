package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result

/** Wire encoding only; current native/completed-device admission is always a use-case duty. */
interface CollaborationSpaceSyncCursorRepository {
    fun seal(
        owner: CollaborationSpaceHistoryOwner,
        position: CollaborationSyncPosition,
    ): Result<String>

    /**
     * An authenticated original-company cursor returns null: initialize the complete new
     * scope/filter.
     */
    fun open(
        owner: CollaborationSpaceHistoryOwner,
        cursor: String,
    ): Result<CollaborationSyncPosition?>
}
