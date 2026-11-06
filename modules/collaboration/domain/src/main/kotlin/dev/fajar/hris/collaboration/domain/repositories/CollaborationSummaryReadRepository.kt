package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID

interface CollaborationSummaryReadRepository {
    /** Hold the shared publication head until this admitted transaction completes. */
    fun head(companyId: UUID): Result<CollaborationSummaryHead>

    fun snapshot(
        owner: CollaborationSummaryOwner,
        after: UUID?,
        limit: Int,
    ): Result<List<CollaborationSummaryFact>>

    fun changes(
        owner: CollaborationSummaryOwner,
        after: Long,
        upper: Long,
        limit: Int,
    ): Result<List<CollaborationSummaryReference>>

    fun facts(
        owner: CollaborationSummaryOwner,
        ids: List<UUID>,
    ): Result<List<CollaborationSummaryFact>>

    /**
     * One coherent read across relevant account fanout, pair refresh and unpublished references.
     */
    fun pending(owner: CollaborationSummaryOwner): Result<Boolean>
}
