package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationReadinessControl
import dev.fajar.hris.core.domain.Result
import java.time.Instant

/** Send only to the captured target's own Matrix device, using its admitted provider credential. */
interface CollaborationMatrixReadinessRepository {
    fun send(
        control: CollaborationReadinessControl,
        serverName: String,
        token: String,
        deadline: Instant,
    ): Result<Unit>
}
