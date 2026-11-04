package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRecipientDevice
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

interface CollaborationSpaceRecipientRepository {
    /**
     * Currently scoped to the transaction caller at root company scope. Current canonical group
     * eligibility and an exact verified room pair gate peers; own candidates need no room. This
     * projection supplies public metadata only, not caller/native admission or signed provider
     * trust. At most 100 accounts and five devices per account; original Matrix coordinates are
     * retained.
     */
    fun devices(
        spaceId: UUID,
        accountIds: Set<UUID>,
        at: Instant,
    ): Result<List<CollaborationSpaceRecipientDevice>>
}
