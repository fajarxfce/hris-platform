package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.Instant
import java.util.UUID

/** A cohort expires only its own immutable awards, never credit from a competing closing. */
fun belongsToLeaveCarryExpiry(
    expiry: LeaveCarryExpiry,
    employeeId: UUID,
    closing: LeaveYearClosing?,
    bucket: LeaveCarryBucket?,
): Boolean =
    closing != null &&
        bucket != null &&
        closing.employeeId == employeeId &&
        closing.id == bucket.id &&
        closing.typeId == expiry.typeId &&
        closing.year + 1 == expiry.year &&
        bucket.year == expiry.year &&
        closing.timezone == expiry.timezone &&
        bucket.validity == expiry.validity &&
        when (val source = expiry.source) {
            is LeaveCarryExpirySource.Batch -> closing.batchId == source.batchId
            is LeaveCarryExpirySource.Closing ->
                closing.batchId == null &&
                    closing.id == source.closingId &&
                    closing.employeeId == source.employeeId
        }

fun validateLeaveCarryExpiryCheckpoint(
    expiry: LeaveCarryExpiry,
    job: BackgroundJob,
    attempt: LeaveCarryExpiryAttempt,
    completed: Int,
): Result<Unit> {
    val scheduledFor = job.request.scheduledFor
    if (
        job.request.kind != JobKind.LEAVE_CARRY_EXPIRY ||
            expiry.jobId != job.request.id ||
            attempt.jobId != job.request.id ||
            attempt.attempt.toLong() != expiry.version + 1 ||
            expiry.version !in 0..7 ||
            expiry.totalEmployees !in 1..5000 ||
            completed !in 0..expiry.totalEmployees ||
            attempt.baseCompleted !in 0..completed ||
            job.request.totalItems != expiry.totalEmployees - attempt.baseCompleted + 1 ||
            job.request.progressMode != JobProgressMode.FIXED_TOTAL ||
            job.completedItems != completed - attempt.baseCompleted ||
            job.request.values != mapOf("expiryId" to expiry.id.toString()) ||
            scheduledFor == null ||
            scheduledFor.isBefore(expiry.validity.expiresAt)
    )
        return Result.Failed(
            Failure(FailureKind.CONFLICT, "leave_carry_expiry_checkpoint_inconsistent")
        )
    return Result.Success(Unit)
}

fun validateLeaveCarryExpiryProgress(
    expiry: LeaveCarryExpiry,
    job: BackgroundJob,
    attempt: LeaveCarryExpiryAttempt,
    completed: Int,
    now: Instant,
): Result<Unit> {
    val checked = validateLeaveCarryExpiryCheckpoint(expiry, job, attempt, completed)
    if (checked is Result.Failed) return checked
    if (now.isBefore(expiry.validity.expiresAt))
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_carry_expiry_not_due"))
    return Result.Success(Unit)
}

fun availableLeaveCarryExpiryActions(
    actor: Actor,
    overview: LeaveCarryExpiryOverview,
): Set<LeaveCarryExpiryAction> =
    if (
        "leave.year.close" in actor.permissions &&
            overview.expiry.version in 0..6 &&
            overview.status in setOf(JobStatus.FAILED, JobStatus.CANCELLED)
    )
        setOf(LeaveCarryExpiryAction.RESUME)
    else emptySet()
