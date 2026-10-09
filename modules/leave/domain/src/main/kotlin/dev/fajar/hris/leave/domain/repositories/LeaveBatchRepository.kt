package dev.fajar.hris.leave.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.YearMonth
import java.util.UUID

interface LeaveBatchRepository {
    fun lock(companyId: UUID): Result<Unit>

    fun active(
        companyId: UUID,
        typeId: UUID,
        kind: LeaveBatchKind,
        period: YearMonth,
    ): Result<Boolean>

    fun find(companyId: UUID, id: UUID, lock: Boolean = false): Result<LeaveBatch?>

    fun forJob(companyId: UUID, jobId: UUID, lock: Boolean = false): Result<LeaveBatch?>

    fun list(companyId: UUID, after: UUID?, limit: Int): Result<Page<LeaveBatch>>

    fun create(
        companyId: UUID,
        batch: LeaveBatch,
        targets: List<LeaveBatchTarget>,
        attempt: LeaveBatchAttempt,
    ): Result<Unit>

    fun next(companyId: UUID, batchId: UUID): Result<LeaveBatchTarget?>

    fun outcome(companyId: UUID, batch: LeaveBatch, result: LeaveBatchResult): Result<Unit>

    fun counts(companyId: UUID, batchId: UUID): Result<LeaveBatchCounts>

    fun results(
        companyId: UUID,
        batchId: UUID,
        after: Int?,
        limit: Int,
    ): Result<Page<LeaveBatchResult>>

    fun attempts(companyId: UUID, batchId: UUID): Result<List<LeaveBatchAttempt>>

    fun transition(
        companyId: UUID,
        batch: LeaveBatch,
        status: LeaveBatchStatus,
        attempt: LeaveBatchAttempt? = null,
    ): Result<MutationReceipt>
}
