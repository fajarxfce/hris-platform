package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*

fun leaveLedgerMovements(
    days: List<LeaveDay>,
    effect: LeaveBalanceEffect,
): List<LeaveLedgerMovement> =
    days
        .groupBy { it.workDate.year }
        .toSortedMap()
        .map { (year, allocation) ->
            val units = allocation.sumOf { it.portion.halfDays }
            LeaveLedgerMovement(
                year,
                effect.kind,
                units * effect.available,
                units * effect.reserved,
                units * effect.consumed,
            )
        }

fun validateLeaveMovement(balance: LeaveBalance, movement: LeaveLedgerMovement): Result<Unit> {
    if (balance.closed) return Result.Failed(Failure(FailureKind.CONFLICT, "leave_year_closed"))
    if (
        balance.availableHalfDays.toLong() + movement.availableDelta > Int.MAX_VALUE ||
            balance.reservedHalfDays.toLong() + movement.reservedDelta > Int.MAX_VALUE ||
            balance.consumedHalfDays.toLong() + movement.consumedDelta > Int.MAX_VALUE
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_balance_limit"))
    if (balance.availableHalfDays.toLong() + movement.availableDelta < 0)
        return Result.Failed(Failure(FailureKind.CONFLICT, "insufficient_leave_balance"))
    if (
        balance.reservedHalfDays.toLong() + movement.reservedDelta < 0 ||
            balance.consumedHalfDays.toLong() + movement.consumedDelta < 0
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_ledger_conflict"))
    return Result.Success(Unit)
}
