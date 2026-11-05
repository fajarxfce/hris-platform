package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/** Worker-only raw projection operations; callers own global and company transactions. */
interface CollaborationSummaryProjectionRepository {
    fun lockAccounts(now: Instant, limit: Int): Result<List<CollaborationSummaryAccountWork>>

    fun targets(
        accountId: UUID,
        after: CollaborationSummaryTarget?,
        limit: Int,
    ): Result<List<CollaborationSummaryTarget>>

    fun enqueue(targets: List<CollaborationSummaryTarget>, now: Instant): Result<Boolean>

    fun advanceAccount(
        accountId: UUID,
        after: CollaborationSummaryTarget?,
        next: Instant?,
    ): Result<Boolean>

    fun dueCompanies(now: Instant, limit: Int): Result<List<UUID>>

    fun lockDue(companyId: UUID, now: Instant, limit: Int): Result<List<UUID>>

    fun candidates(companyId: UUID, ids: List<UUID>): Result<List<CollaborationSummaryCandidate>>

    fun records(companyId: UUID, ids: List<UUID>): Result<List<CollaborationSummaryRecord>>

    fun save(records: List<CollaborationSummaryRecord>): Result<Boolean>

    fun complete(companyId: UUID, ids: List<UUID>): Result<Boolean>
}
