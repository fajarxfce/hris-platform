package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationSummaryProjectionDataSource
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSummaryProjectionRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationDirectSummaryRecordsRecord
import dev.fajar.hris.schema.tables.records.CollaborationSummaryRefreshQueueRecord
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class StoredCollaborationSummaryProjectionRepository(
    private val source: CollaborationSummaryProjectionDataSource
) : CollaborationSummaryProjectionRepository {
    override fun lockAccounts(
        now: Instant,
        limit: Int,
    ): Result<List<CollaborationSummaryAccountWork>> = safeDatabaseCall {
        require(limit in 1..4)
        source.lockAccounts(now, limit).map { row ->
            require((row.afterCompanyId == null) == (row.afterConversationId == null))
            CollaborationSummaryAccountWork(
                requireNotNull(row.accountId),
                row.afterCompanyId?.let {
                    CollaborationSummaryTarget(it, requireNotNull(row.afterConversationId))
                },
            )
        }
    }

    override fun targets(
        accountId: UUID,
        after: CollaborationSummaryTarget?,
        limit: Int,
    ): Result<List<CollaborationSummaryTarget>> = safeDatabaseCall {
        require(limit in 1..201)
        source.targets(accountId, after?.companyId, after?.conversationId, limit).map {
            CollaborationSummaryTarget(
                requireNotNull(it.companyId),
                requireNotNull(it.conversationId),
            )
        }
    }

    override fun enqueue(targets: List<CollaborationSummaryTarget>, now: Instant): Result<Boolean> =
        safeDatabaseCall {
            require(targets.size in 1..200 && targets.distinct().size == targets.size)
            source.enqueue(
                targets.map { target ->
                    CollaborationSummaryRefreshQueueRecord().apply {
                        companyId = target.companyId
                        conversationId = target.conversationId
                        dueAt = now.atOffset(ZoneOffset.UTC)
                    }
                }
            )
        }

    override fun advanceAccount(
        accountId: UUID,
        after: CollaborationSummaryTarget?,
        next: Instant?,
    ): Result<Boolean> = safeDatabaseCall {
        require((after == null) == (next == null))
        source.advanceAccount(accountId, after?.companyId, after?.conversationId, next)
    }

    override fun dueCompanies(now: Instant, limit: Int): Result<List<UUID>> = safeDatabaseCall {
        require(limit in 1..4)
        source.dueCompanies(now, limit)
    }

    override fun lockDue(companyId: UUID, now: Instant, limit: Int): Result<List<UUID>> =
        safeDatabaseCall {
            require(limit in 1..200)
            source.lockDue(companyId, now, limit)
        }

    override fun candidates(
        companyId: UUID,
        ids: List<UUID>,
    ): Result<List<CollaborationSummaryCandidate>> = safeDatabaseCall {
        require(ids.size in 1..200 && ids.distinct().size == ids.size)
        source.candidates(companyId, ids).map {
            CollaborationSummaryCandidate(
                CollaborationSummaryTarget(it.companyId, it.conversationId),
                it.firstAccountId,
                it.secondAccountId,
                it.firstDisplayName,
                it.secondDisplayName,
            )
        }
    }

    override fun records(
        companyId: UUID,
        ids: List<UUID>,
    ): Result<List<CollaborationSummaryRecord>> = safeDatabaseCall {
        require(ids.size in 1..200 && ids.distinct().size == ids.size)
        source.records(companyId, ids).map {
            CollaborationSummaryRecord(
                CollaborationSummaryTarget(
                    requireNotNull(it.companyId),
                    requireNotNull(it.conversationId),
                ),
                requireNotNull(it.ownerAccountId),
                requireNotNull(it.peerAccountId),
                requireNotNull(it.version),
                it.peerDisplayName,
            )
        }
    }

    override fun save(records: List<CollaborationSummaryRecord>): Result<Boolean> =
        safeDatabaseCall {
            require(
                records.size in 1..400 && records.map { it.target.companyId }.distinct().size == 1
            )
            require(records.map { it.target to it.ownerAccountId }.distinct().size == records.size)
            source.save(
                records.map { value ->
                    CollaborationDirectSummaryRecordsRecord().apply {
                        companyId = value.target.companyId
                        conversationId = value.target.conversationId
                        ownerAccountId = value.ownerAccountId
                        peerAccountId = value.peerAccountId
                        version = value.version
                        peerDisplayName = value.peerDisplayName
                    }
                }
            )
        }

    override fun complete(companyId: UUID, ids: List<UUID>): Result<Boolean> = safeDatabaseCall {
        require(ids.size in 1..200 && ids.distinct().size == ids.size)
        source.complete(companyId, ids)
    }
}
