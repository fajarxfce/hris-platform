package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationToDevice
import dev.fajar.hris.core.domain.Result
import java.time.Instant

interface CollaborationMatrixToDeviceRepository {
    /** Provider queue acceptance only. Preserve the same device-scoped transaction ID on retry. */
    fun send(request: CollaborationToDevice, token: String, deadline: Instant): Result<Unit>
}
