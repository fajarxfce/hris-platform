package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.*

fun validateLeaveAccrualScheduleSelection(first: YearMonth, count: Int): Result<Unit> =
    if (first.year !in 1900..2199 || count !in 1..12)
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_accrual_schedule"))
    else Result.Success(Unit)

/** Finite, explicitly reviewed periods; this policy neither acquires data nor schedules work. */
fun leaveAccrualScheduleMonths(
    first: YearMonth,
    count: Int,
    frequency: LeaveAccrualFrequency,
): Result<List<YearMonth>> {
    val valid = validateLeaveAccrualScheduleSelection(first, count)
    if (valid is Result.Failed) return valid
    if (frequency == LeaveAccrualFrequency.MANUAL)
        return Result.Failed(Failure(FailureKind.VALIDATION, "leave_accrual_not_configured"))
    val step = if (frequency == LeaveAccrualFrequency.ANNUAL) 12L else 1L
    val months = (0 until count).map { first.plusMonths(it * step) }
    if (months.last().year > 2199)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_accrual_schedule"))
    return Result.Success(java.util.List.copyOf(months))
}

fun validateScheduledLeavePolicy(type: LeaveType, frequency: LeaveAccrualFrequency): Result<Unit> {
    if (!type.active)
        return Result.Failed(Failure(FailureKind.VALIDATION, "leave_type_unavailable"))
    val accrual =
        type.policy.accrual
            ?: return Result.Failed(Failure(FailureKind.VALIDATION, "leave_accrual_not_configured"))
    if (accrual.frequency != frequency)
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_schedule_frequency_changed"))
    if (frequency == LeaveAccrualFrequency.MANUAL)
        return Result.Failed(Failure(FailureKind.VALIDATION, "leave_accrual_not_configured"))
    return validateLeaveAccrualPolicy(accrual, type.policy.allowPartialDays)
}

/**
 * Monthly credit follows a complete month; annual eligibility is evaluated in the selected month.
 */
fun leaveAccrualDueAt(
    period: YearMonth,
    frequency: LeaveAccrualFrequency,
    timezone: String,
): Instant {
    require(frequency != LeaveAccrualFrequency.MANUAL)
    val date =
        if (frequency == LeaveAccrualFrequency.MONTHLY) period.plusMonths(1).atDay(1)
        else period.atDay(1)
    return date.atStartOfDay(ZoneId.of(timezone)).withLaterOffsetAtOverlap().toInstant()
}
