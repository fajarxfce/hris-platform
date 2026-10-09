package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollPeriod.toResponse() =
    PayrollPeriodResponse(
        id,
        earningsMonth.toString(),
        plannedPaymentMonth.toString(),
        plannedPaymentDate,
        timezone,
        participantCount,
        authorId,
        createdAt,
        status.name,
        version,
    )

fun PayrollPeriodChange.toResponse() =
    PayrollPeriodChangeResponse(version, status.name, actorId, recordedAt, reason)

fun PayrollPeriodMember.toResponse() =
    PayrollPeriodMemberResponse(employeeId, inputVersion, inputStatus?.name)

fun PayrollPeriodDetails.toResponse() =
    PayrollPeriodDetailsResponse(
        period.toResponse(),
        Page(members.items.map { it.toResponse() }, members.nextCursor),
        Page(history.items.map { it.toResponse() }, history.nextCursor),
    )
