package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationRecipientDevice
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

interface CollaborationRecipientRepository {
    /** At most 100 accounts and 500 rows, under the caller's current company scope. */
    fun devices(
        companyId: UUID,
        accountIds: Set<UUID>,
        at: Instant,
    ): Result<List<CollaborationRecipientDevice>>
}
