package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.*

fun LeaveAccount.toResponse(): LeaveAccountResponse =
    LeaveAccountResponse(
        id,
        employeeId,
        typeId,
        balance.year,
        balance.availableHalfDays.toLeaveDays(),
        balance.reservedHalfDays.toLeaveDays(),
        balance.consumedHalfDays.toLeaveDays(),
        balance.version,
        balance.closed,
    )

fun LeaveAccrualPosting.toResponse(): LeaveAccrualPostingResponse =
    LeaveAccrualPostingResponse(
        id,
        employeeId,
        typeId,
        processedMonth,
        award.period,
        award.eligibleFrom,
        award.eligibleUntil,
        award.halfDays.toLeaveDays(),
        frequency.name,
        policy.toResponse(),
        employmentVersion,
        balanceVersion,
        actorId,
        recordedAt,
        reason,
    )

fun LeaveYearClosing.toResponse(): LeaveYearClosingResponse =
    LeaveYearClosingResponse(
        id,
        employeeId,
        typeId,
        year,
        sourceVersion,
        destinationVersion,
        availableHalfDays.toLeaveDays(),
        consumedHalfDays.toLeaveDays(),
        rollover.carryHalfDays.toLeaveDays(),
        rollover.expireHalfDays.toLeaveDays(),
        policy.toResponse(),
        actorId,
        recordedAt,
        reason,
    )

fun LeaveEntitlements.toResponse(): LeaveEntitlementsResponse =
    LeaveEntitlementsResponse(
        balance.toResponse(),
        frequency?.name,
        postings.map { it.toResponse() },
        closing?.toResponse(),
    )
