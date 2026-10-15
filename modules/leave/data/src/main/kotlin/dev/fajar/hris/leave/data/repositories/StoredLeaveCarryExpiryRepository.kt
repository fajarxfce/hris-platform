package dev.fajar.hris.leave.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.data.datasources.LeaveCarryExpiryDataSource
import dev.fajar.hris.leave.data.mappers.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeaveCarryExpiryRepository
import java.util.UUID

class StoredLeaveCarryExpiryRepository(private val source: LeaveCarryExpiryDataSource) :
    LeaveCarryExpiryRepository {
    override fun find(companyId: UUID, id: UUID, lock: Boolean): Result<LeaveCarryExpiry?> =
        safeDatabaseCall {
            source
                .find(companyId, id, lock)
                ?.also { require(it.companyId == companyId && it.id == id) }
                ?.toCarryExpiry()
        }

    override fun forJob(companyId: UUID, jobId: UUID, lock: Boolean): Result<LeaveCarryExpiry?> =
        safeDatabaseCall {
            source
                .forJob(companyId, jobId, lock)
                ?.also { require(it.companyId == companyId && it.jobId == jobId) }
                ?.toCarryExpiry()
        }

    override fun list(companyId: UUID, after: UUID?, limit: Int): Result<Page<LeaveCarryExpiry>> =
        safeDatabaseCall {
            require(limit in 1..100)
            val rows = source.list(companyId, after, limit + 1)
            require(
                rows.size <= limit + 1 &&
                    rows.all { it.companyId == companyId } &&
                    rows.map { it.id }.distinct().size == rows.size
            )
            Page(
                java.util.List.copyOf(rows.take(limit).map { it.toCarryExpiry() }),
                if (rows.size > limit) rows[limit - 1].id.toString() else null,
            )
        }

    override fun overview(companyId: UUID, id: UUID): Result<LeaveCarryExpiryOverview?> =
        safeDatabaseCall {
            val rows = source.overviews(companyId, id, null, 1)
            require(rows.size <= 1)
            rows
                .singleOrNull()
                ?.also { require(it.expiry.companyId == companyId && it.expiry.id == id) }
                ?.toCarryExpiryOverview()
        }

    override fun overviews(
        companyId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<LeaveCarryExpiryOverview>> = safeDatabaseCall {
        require(limit in 1..100)
        val rows = source.overviews(companyId, null, after, limit + 1)
        require(
            rows.size <= limit + 1 &&
                rows.all { it.expiry.companyId == companyId } &&
                rows.map { it.expiry.id }.distinct().size == rows.size
        )
        Page(
            java.util.List.copyOf(rows.take(limit).map { it.toCarryExpiryOverview() }),
            if (rows.size > limit) rows[limit - 1].expiry.id.toString() else null,
        )
    }

    override fun create(
        companyId: UUID,
        expiry: LeaveCarryExpiry,
        attempt: LeaveCarryExpiryAttempt,
    ): Result<Unit> = safeDatabaseCall {
        source.insert(expiry.toRow(companyId))
        source.insertAttempt(attempt.toRow(companyId, expiry.id))
    }

    override fun next(companyId: UUID, id: UUID, after: Int): Result<LeaveBatchTarget?> =
        safeDatabaseCall {
            require(after in 0..5000)
            source.next(companyId, id, after)?.let {
                require(
                    it.companyId == companyId &&
                        it.expiryId == id &&
                        it.ordinal == after + 1 &&
                        it.ordinal in 1..5000
                )
                LeaveBatchTarget(it.ordinal, it.employeeId)
            }
        }

    override fun completed(companyId: UUID, id: UUID): Result<Int> = safeDatabaseCall {
        source.completed(companyId, id).also { require(it in 0..5000) }
    }

    override fun outcome(
        companyId: UUID,
        id: UUID,
        outcome: LeaveCarryExpiryOutcome,
    ): Result<Unit> = safeDatabaseCall { source.outcome(outcome.toRow(companyId, id)) }

    override fun results(
        companyId: UUID,
        id: UUID,
        after: Int?,
        limit: Int,
    ): Result<Page<LeaveCarryExpiryOutcome>> = safeDatabaseCall {
        require(limit in 1..100 && (after == null || after in 1..5000))
        val rows = source.results(companyId, id, after, limit + 1)
        require(
            rows.size <= limit + 1 &&
                rows.all { it.companyId == companyId && it.expiryId == id } &&
                rows.map { it.ordinal }.distinct().size == rows.size
        )
        Page(
            java.util.List.copyOf(rows.take(limit).map { it.toExpiryOutcome() }),
            if (rows.size > limit) rows[limit - 1].ordinal.toString() else null,
        )
    }

    override fun attempts(companyId: UUID, id: UUID): Result<List<LeaveCarryExpiryAttempt>> =
        safeDatabaseCall {
            val rows = source.attempts(companyId, id)
            require(
                rows.size <= 8 &&
                    rows.all { it.companyId == companyId && it.expiryId == id } &&
                    rows.map { it.attempt }.distinct().size == rows.size
            )
            java.util.List.copyOf(rows.map { it.toExpiryAttempt() })
        }

    override fun resume(
        companyId: UUID,
        expiry: LeaveCarryExpiry,
        attempt: LeaveCarryExpiryAttempt,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.insertAttempt(attempt.toRow(companyId, expiry.id))
                source.resume(companyId, expiry.id, expiry.version, attempt.jobId)
            }
            .flatMap { changed ->
                if (changed) Result.Success(MutationReceipt(expiry.id, expiry.version + 1))
                else Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            }
}
