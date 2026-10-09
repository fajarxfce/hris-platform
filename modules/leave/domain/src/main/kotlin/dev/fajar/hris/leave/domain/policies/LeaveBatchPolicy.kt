package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.YearMonth
import java.util.UUID

fun validateLeaveBatchInput(
    period: YearMonth,
    expectedPolicyVersion: Long,
    employeeIds: Set<UUID>?,
    reason: String,
): Result<Unit> {
    if (
        period.year !in 1900..2199 ||
            expectedPolicyVersion < 0 ||
            (employeeIds != null && employeeIds.size !in 1..5000) ||
            reason.isBlank() ||
            reason.length > 1000
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_batch"))
    return Result.Success(Unit)
}

fun validateLeaveBatchJob(actor: Actor, lease: JobLease, kind: LeaveBatchKind): Result<Unit> {
    val request = lease.job.request
    if (
        request.kind != kind.jobKind ||
            actor.companyId != request.companyId ||
            actor.accountId != request.actorId
    )
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
    return actor.requirePermission(kind.permission)
}

fun validateLeaveBatchProgress(
    batch: LeaveBatch,
    job: BackgroundJob,
    counts: LeaveBatchCounts,
    attempt: LeaveBatchAttempt,
): Result<Unit> {
    if (
        batch.status != LeaveBatchStatus.RUNNING ||
            batch.jobId != job.request.id ||
            attempt.jobId != job.request.id ||
            counts.completed != attempt.baseCompleted + job.completedItems ||
            job.request.totalItems != batch.totalEmployees - attempt.baseCompleted + 1 ||
            job.request.values["batchId"] != batch.id.toString()
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_batch_checkpoint_inconsistent"))
    return Result.Success(Unit)
}
