package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.LeaveRequestPreview

fun LeaveRequestPreview.toResponse(): LeaveRequestPreviewResponse =
    LeaveRequestPreviewResponse(
        employeeId,
        evaluatedAt,
        timezone,
        policy.toResponse(),
        days.map { it.toResponse() },
        days.sumOf { it.portion.halfDays }.toLeaveDays(),
        excludedDates,
        balances.map {
            LeavePreviewBalanceResponse(
                it.balance.year,
                it.balance.availableHalfDays.toLeaveDays(),
                it.balance.reservedHalfDays.toLeaveDays(),
                it.balance.consumedHalfDays.toLeaveDays(),
                it.requestedHalfDays.toLeaveDays(),
                it.remainingHalfDays.toLeaveDays(),
                it.balance.version,
            )
        },
        revision,
    )
