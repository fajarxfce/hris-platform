package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.Instant

/**
 * Adjustments use ordinary credit first. Expired free carry is settled before applying the delta.
 */
fun planLeaveCarryAdjustment(
    balance: LeaveBalance,
    bucket: LeaveCarryBucket?,
    delta: Int,
    now: Instant,
): Result<LeaveCarryMovement?> {
    if (bucket == null) return validateLeaveBalanceAdjustment(balance, delta).map { null }
    val valid = validateLeaveCarryBucket(bucket)
    if (valid is Result.Failed) return valid
    if (
        bucket.year != balance.year ||
            bucket.availableHalfDays > balance.availableHalfDays ||
            bucket.reservedHalfDays > balance.reservedHalfDays ||
            bucket.consumedHalfDays > balance.consumedHalfDays
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_carry_conflict"))
    val expiration = planLeaveCarryExpiry(bucket, now)
    if (expiration is Result.Failed) return expiration
    val expiry = (expiration as Result.Success).value
    val available = balance.availableHalfDays - (expiry?.halfDays ?: 0)
    val adjusted =
        validateLeaveBalanceAdjustment(balance.copy(availableHalfDays = available), delta)
    if (adjusted is Result.Failed) return adjusted
    val movement =
        if (expiry != null) expiry
        else {
            val ordinary = available - bucket.availableHalfDays
            val carriedDebit = maxOf(0, -delta - ordinary)
            if (carriedDebit == 0) null
            else LeaveCarryMovement(bucket.id, LeaveCarryEffect.DEDUCT, carriedDebit)
        }
    return if (movement == null) Result.Success(null)
    else applyLeaveCarryMovement(bucket, movement).map { movement }
}
