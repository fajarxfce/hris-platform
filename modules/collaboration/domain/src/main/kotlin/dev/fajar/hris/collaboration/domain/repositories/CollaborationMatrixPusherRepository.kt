package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.core.domain.Result
import java.time.Instant

/** Server-issued opaque keys only; current device admission belongs to the caller. */
interface CollaborationMatrixPusherRepository {
    fun set(pushKey: String, bearer: String, deadline: Instant): Result<Unit>

    fun remove(pushKey: String, bearer: String, deadline: Instant): Result<Unit>
}
