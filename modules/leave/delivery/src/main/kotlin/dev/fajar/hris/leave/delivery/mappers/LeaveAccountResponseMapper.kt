package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.*

fun LeaveAccrualPolicy.toResponse(): LeaveAccrualPolicyResponse =
    LeaveAccrualPolicyResponse(
        frequency.name,
        halfDaysPerPeriod.toLeaveDays(),
        carryLimitHalfDays.toLeaveDays(),
        carryValidityMonths,
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
        carry?.toResponse(),
    )

fun LeaveCarryAward.toResponse(): LeaveCarryBalanceResponse =
    LeaveCarryBalanceResponse(
        bucket.id,
        bucket.initialHalfDays.toLeaveDays(),
        bucket.availableHalfDays.toLeaveDays(),
        bucket.reservedHalfDays.toLeaveDays(),
        bucket.consumedHalfDays.toLeaveDays(),
        bucket.expiredHalfDays.toLeaveDays(),
        bucket.transferredHalfDays.toLeaveDays(),
        bucket.deductedHalfDays.toLeaveDays(),
        bucket.validity?.through,
        bucket.validity?.expiresAt,
        timezone,
        bucket.version,
    )
