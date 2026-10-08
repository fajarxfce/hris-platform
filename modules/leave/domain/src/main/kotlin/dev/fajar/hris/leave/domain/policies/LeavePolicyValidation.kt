package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*

fun validateLeaveType(type: LeaveType, reason: String): Result<Unit> {
    val p = type.policy
    if (
        !type.code.matches(Regex("[A-Z][A-Z0-9_-]{0,31}")) ||
            p.name.isBlank() ||
            p.name.length > 200 ||
            p.minServiceMonths !in 0..120 ||
            p.maxRequestDays !in 1..366 ||
            p.allowedContracts.isEmpty() ||
            type.effectiveFrom.year !in 1900..2200 ||
            reason.isBlank() ||
            reason.length > 1000 ||
            type.version < 0
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_policy"))
    return Result.Success(Unit)
}

fun validateLeaveBalanceAdjustment(balance: LeaveBalance, delta: Int): Result<Unit> {
    if (delta == 0 || delta !in -732..732)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_adjustment"))
    if (balance.availableHalfDays.toLong() + delta !in 0L..Int.MAX_VALUE.toLong())
        return Result.Failed(Failure(FailureKind.CONFLICT, "insufficient_leave_balance"))
    return Result.Success(Unit)
}
