package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.*

fun LeaveAccrualPolicy.toResponse(): LeaveAccrualPolicyResponse =
    LeaveAccrualPolicyResponse(
        frequency.name,
        halfDaysPerPeriod.toLeaveDays(),
        carryLimitHalfDays.toLeaveDays(),
    )

fun LeaveBalance.toResponse(): LeaveBalanceResponse =
    LeaveBalanceResponse(
        year,
        availableHalfDays.toLeaveDays(),
        reservedHalfDays.toLeaveDays(),
        consumedHalfDays.toLeaveDays(),
        version,
        closed,
        accountId,
    )
