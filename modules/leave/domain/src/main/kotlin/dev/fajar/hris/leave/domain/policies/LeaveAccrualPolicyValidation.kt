package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*

fun validateLeaveAccrualPolicy(
    accrual: LeaveAccrualPolicy,
    partialDaysAllowed: Boolean,
): Result<Unit> {
    val range =
        when (accrual.frequency) {
            LeaveAccrualFrequency.MANUAL -> 0..0
            LeaveAccrualFrequency.MONTHLY -> 1..62
            LeaveAccrualFrequency.ANNUAL -> 1..732
        }
    val fields = mutableMapOf<String, String>()
    if (
        accrual.halfDaysPerPeriod !in range ||
            (!partialDaysAllowed && accrual.halfDaysPerPeriod % 2 != 0)
    )
        fields["accrual.daysPerPeriod"] = "out_of_range"
    if (
        accrual.carryLimitHalfDays !in 0..732 ||
            (!partialDaysAllowed && accrual.carryLimitHalfDays % 2 != 0)
    )
        fields["accrual.carryLimitDays"] = "out_of_range"
    return if (fields.isEmpty()) Result.Success(Unit)
    else
        Result.Failed(
            Failure(FailureKind.VALIDATION, "invalid_leave_accrual_policy", fields = fields)
        )
}
