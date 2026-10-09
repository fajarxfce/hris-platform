package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.LeaveAccrualFrequency
import java.time.*

fun validateLeaveAccrualPeriod(
    month: YearMonth,
    today: LocalDate,
    frequency: LeaveAccrualFrequency,
): Result<Unit> {
    if (frequency == LeaveAccrualFrequency.MANUAL)
        return Result.Failed(Failure(FailureKind.VALIDATION, "leave_accrual_not_configured"))
    if (
        month.year !in 1900..2199 ||
            month > YearMonth.from(today) ||
            (frequency == LeaveAccrualFrequency.MONTHLY && !today.isAfter(month.atEndOfMonth()))
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "leave_accrual_not_due"))
    return Result.Success(Unit)
}
