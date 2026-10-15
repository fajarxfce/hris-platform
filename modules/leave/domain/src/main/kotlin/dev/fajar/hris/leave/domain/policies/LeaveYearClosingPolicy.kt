package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.Instant

fun calculateLeaveYearRollover(
    balance: LeaveBalance,
    carryLimit: Int,
    destination: LeaveBalance,
    pendingRequests: Boolean,
): Result<LeaveYearRollover> {
    if (balance.closed) return Result.Failed(Failure(FailureKind.CONFLICT, "leave_year_closed"))
    if (
        balance.availableHalfDays < 0 ||
            balance.reservedHalfDays < 0 ||
            balance.consumedHalfDays < 0 ||
            destination.year != balance.year + 1 ||
            carryLimit !in 0..732
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_account_inconsistent"))
    if (balance.reservedHalfDays > 0 || pendingRequests)
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_resolution_required"))
    val carry = minOf(balance.availableHalfDays, carryLimit)
    if (carry > 0 && destination.closed)
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_destination_year_closed"))
    if (destination.availableHalfDays.toLong() + carry > Int.MAX_VALUE)
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_balance_limit"))
    return Result.Success(LeaveYearRollover(carry, balance.availableHalfDays - carry))
}

/** Settles old deadlines before calculating a new award; no I/O or policy selection occurs here. */
fun planLeaveYearClosing(
    balance: LeaveBalance,
    bucket: LeaveCarryBucket?,
    accrual: LeaveAccrualPolicy?,
    destination: LeaveBalance,
    pendingRequests: Boolean,
    now: Instant,
    timezone: String,
): Result<LeaveYearClosingPlan> {
    val validityResult =
        leaveCarryValidity(balance.year + 1, accrual?.carryValidityMonths, timezone)
    if (validityResult is Result.Failed) return validityResult
    val validity = (validityResult as Result.Success).value
    var source = balance
    var carried = bucket
    var settlement: LeaveCarryMovement? = null
    if (bucket != null) {
        val consistent = validateLeaveCarryBalance(balance, bucket)
        if (consistent is Result.Failed) return consistent
        val expiration = planLeaveCarryExpiry(bucket, now)
        if (expiration is Result.Failed) return expiration
        settlement = (expiration as Result.Success).value
        if (settlement != null) {
            val applied = applyLeaveCarryMovement(bucket, settlement)
            if (applied is Result.Failed) return applied
            carried = (applied as Result.Success).value
            source =
                balance.copy(
                    availableHalfDays = balance.availableHalfDays - settlement.halfDays,
                    version = balance.version + 1,
                )
        }
    }
    if (source.version !in 0..999999996L || destination.version !in 0..999999999L)
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_balance_limit"))
    val limit =
        if (validity != null && !now.isBefore(validity.expiresAt)) 0
        else accrual?.carryLimitHalfDays ?: 0
    val calculated = calculateLeaveYearRollover(source, limit, destination, pendingRequests)
    if (calculated is Result.Failed) return calculated
    val rollover = (calculated as Result.Success).value
    val movements = mutableListOf<LeaveCarryMovement>()
    if (carried != null && carried.availableHalfDays > 0) {
        val transfer = minOf(carried.availableHalfDays, rollover.carryHalfDays)
        if (transfer > 0)
            movements += LeaveCarryMovement(carried.id, LeaveCarryEffect.TRANSFER, transfer)
        val expire = carried.availableHalfDays - transfer
        if (expire > 0) movements += LeaveCarryMovement(carried.id, LeaveCarryEffect.EXPIRE, expire)
        for (movement in movements) {
            val applied = applyLeaveCarryMovement(requireNotNull(carried), movement)
            if (applied is Result.Failed) return applied
            carried = (applied as Result.Success).value
        }
    }
    return Result.Success(
        LeaveYearClosingPlan(
            source,
            rollover,
            validity,
            settlement,
            java.util.List.copyOf(movements),
        )
    )
}
