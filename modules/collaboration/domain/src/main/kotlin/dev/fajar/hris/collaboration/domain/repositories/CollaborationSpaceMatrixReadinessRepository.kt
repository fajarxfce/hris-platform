package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceHistoryOwner
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceReadinessControl
import dev.fajar.hris.core.domain.Result
import java.time.Instant

/** Only transports an opaque hint. The caller owns current target admission and its deadline. */
interface CollaborationSpaceMatrixReadinessRepository {
    fun send(
        control: CollaborationSpaceReadinessControl,
        owner: CollaborationSpaceHistoryOwner,
        token: String,
        deadline: Instant,
    ): Result<Unit>
}
