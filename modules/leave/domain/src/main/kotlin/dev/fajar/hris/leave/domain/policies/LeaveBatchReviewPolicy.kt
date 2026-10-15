package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.LocalDate
import java.time.YearMonth

fun validateLeaveBatchPeriod(kind: LeaveBatchKind, period: YearMonth): Result<Unit> =
    if (
        period.year !in 1900..2199 || (kind == LeaveBatchKind.YEAR_CLOSE && period.monthValue != 12)
    )
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_batch"))
    else Result.Success(Unit)

fun validateLeaveBatchPolicy(
    kind: LeaveBatchKind,
    period: YearMonth,
    today: LocalDate,
    type: LeaveType,
): Result<Unit> {
    if (kind == LeaveBatchKind.YEAR_CLOSE)
        return if (period.year >= today.year)
            Result.Failed(Failure(FailureKind.VALIDATION, "leave_year_not_ended"))
        else Result.Success(Unit)
    val accrual =
        type.policy.accrual
            ?: return Result.Failed(Failure(FailureKind.VALIDATION, "leave_accrual_not_configured"))
    if (!type.active)
        return Result.Failed(Failure(FailureKind.VALIDATION, "leave_type_unavailable"))
    return validateLeaveAccrualPolicy(accrual, type.policy.allowPartialDays).flatMap {
        validateLeaveAccrualPeriod(period, today, accrual.frequency)
    }
}

/** Hints only: the command independently rechecks current authority and the stopped job. */
fun availableLeaveBatchActions(
    actor: Actor,
    batch: LeaveBatch,
    job: BackgroundJob,
    attempts: List<LeaveBatchAttempt>,
    policyVersion: Long?,
    active: Boolean,
): Set<LeaveBatchAction> =
    if (
        batch.kind.permission in actor.permissions &&
            batch.actorId == actor.accountId &&
            batch.status != LeaveBatchStatus.COMPLETED &&
            job.status in setOf(JobStatus.FAILED, JobStatus.CANCELLED) &&
            job.request.id == batch.jobId &&
            job.request.actorId == batch.actorId &&
            job.request.kind == batch.kind.jobKind &&
            job.request.values["batchId"] == batch.id.toString() &&
            attempts.size in 1..7 &&
            policyVersion == batch.policyVersion &&
            !active
    )
        setOf(LeaveBatchAction.RESUME)
    else emptySet()
