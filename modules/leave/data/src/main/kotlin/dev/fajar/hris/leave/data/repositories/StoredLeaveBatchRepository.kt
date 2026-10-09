package dev.fajar.hris.leave.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.data.datasources.LeaveBatchDataSource
import dev.fajar.hris.leave.data.mappers.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeaveBatchRepository
import java.time.YearMonth
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredLeaveBatchRepository(
    private val source: LeaveBatchDataSource,
    private val json: ObjectMapper,
) : LeaveBatchRepository {
    override fun lock(companyId: UUID): Result<Unit> = safeDatabaseCall { source.lock(companyId) }

    override fun active(
        companyId: UUID,
        typeId: UUID,
        kind: LeaveBatchKind,
        period: YearMonth,
    ): Result<Boolean> = safeDatabaseCall {
        source.active(companyId, typeId, kind.name, period.atDay(1))
    }

    override fun find(companyId: UUID, id: UUID, lock: Boolean): Result<LeaveBatch?> =
        safeDatabaseCall {
            source.find(companyId, id, lock)?.toBatch(json)
        }

    override fun forJob(companyId: UUID, jobId: UUID, lock: Boolean): Result<LeaveBatch?> =
        safeDatabaseCall {
            source.forJob(companyId, jobId, lock)?.toBatch(json)
        }

    override fun list(companyId: UUID, after: UUID?, limit: Int): Result<Page<LeaveBatch>> =
        safeDatabaseCall {
            val rows = source.list(companyId, after, limit + 1)
            Page(
                rows.take(limit).map { it.toBatch(json) },
                if (rows.size > limit) rows[limit - 1].id.toString() else null,
            )
        }

    override fun create(
        companyId: UUID,
        batch: LeaveBatch,
        targets: List<LeaveBatchTarget>,
        attempt: LeaveBatchAttempt,
    ): Result<Unit> = safeDatabaseCall {
        source.insert(batch.toRow(companyId, json))
        source.insertTargets(targets.map { it.toRow(companyId, batch.id) })
        source.insertAttempt(attempt.toRow(companyId, batch.id))
    }

    override fun next(companyId: UUID, batchId: UUID): Result<LeaveBatchTarget?> =
        safeDatabaseCall {
            source.next(companyId, batchId)?.toTarget()
        }

    override fun outcome(
        companyId: UUID,
        batch: LeaveBatch,
        result: LeaveBatchResult,
    ): Result<Unit> = safeDatabaseCall { source.outcome(result.toRow(companyId, batch, json)) }

    override fun counts(companyId: UUID, batchId: UUID): Result<LeaveBatchCounts> =
        safeDatabaseCall {
            val counts = source.counts(companyId, batchId)
            LeaveBatchCounts(
                counts["APPLIED"] ?: 0,
                counts["UNCHANGED"] ?: 0,
                counts["SKIPPED"] ?: 0,
                counts["FAILED"] ?: 0,
            )
        }

    override fun results(
        companyId: UUID,
        batchId: UUID,
        after: Int?,
        limit: Int,
    ): Result<Page<LeaveBatchResult>> = safeDatabaseCall {
        val rows = source.results(companyId, batchId, after, limit + 1)
        Page(
            rows.take(limit).map { it.toResult(json) },
            if (rows.size > limit) rows[limit - 1].ordinal.toString() else null,
        )
    }

    override fun attempts(companyId: UUID, batchId: UUID): Result<List<LeaveBatchAttempt>> =
        safeDatabaseCall {
            source.attempts(companyId, batchId).map { it.toAttempt() }
        }

    override fun transition(
        companyId: UUID,
        batch: LeaveBatch,
        status: LeaveBatchStatus,
        attempt: LeaveBatchAttempt?,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                attempt?.let { source.insertAttempt(it.toRow(companyId, batch.id)) }
                source.transition(
                    companyId,
                    batch.id,
                    batch.version,
                    status.name,
                    attempt?.jobId ?: batch.jobId,
                )
            }
            .requireCurrentVersion()
            .map { MutationReceipt(batch.id, it) }
}
