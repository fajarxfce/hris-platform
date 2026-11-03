package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceContact
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.domain.Result
import java.util.UUID

/**
 * Read under the current account's transaction-local context. Directory eligibility is independent
 * of selected HR company; the calling use case must separately admit the live native session.
 */
interface CollaborationSpaceRepository {
    fun current(): Result<UUID?>

    /** At most 50 accounts and eight public company labels per account, ordered by account ID. */
    fun contacts(
        spaceId: UUID,
        excludedAccountId: UUID,
        query: String,
        after: UUID?,
        limit: Int,
    ): Result<Page<CollaborationSpaceContact>>
}
