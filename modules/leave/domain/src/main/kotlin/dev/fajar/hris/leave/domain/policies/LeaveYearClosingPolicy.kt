package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*

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
