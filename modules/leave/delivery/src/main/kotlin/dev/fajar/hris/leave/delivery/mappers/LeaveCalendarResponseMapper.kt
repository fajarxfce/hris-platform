package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.*

fun LeaveCalendar.toResponse(): LeaveCalendarResponse =
    LeaveCalendarResponse(
        month,
        scope.name,
        companyDate,
        timezone,
        days.map {
            LeaveCalendarDayResponse(
                it.date,
                it.employeeCount,
                it.requestCount,
                it.pendingCount,
                it.approvedCount,
                it.cancellationPendingCount,
            )
        },
    )

fun LeaveCalendarEntries.toResponse(): LeaveCalendarEntriesResponse =
    LeaveCalendarEntriesResponse(
        date,
        scope.name,
        companyDate,
        timezone,
        Page(
            entries.items.map {
                LeaveCalendarEntryResponse(
                    it.requestId,
                    it.employeeId,
                    it.employeeNumber,
                    it.employeeName,
                    it.typeCode,
                    it.typeName,
                    it.portion.name,
                    it.status.name,
                    it.version,
                )
            },
            entries.nextCursor,
        ),
    )
