package dev.fajar.hris.leave.data.mappers

import dev.fajar.hris.leave.data.models.*
import dev.fajar.hris.leave.domain.entities.*

fun LeaveCalendarDayRow.toCalendarDay(): LeaveCalendarDay {
    require(employeeCount > 0 && requestCount >= employeeCount)
    require(listOf(pendingCount, approvedCount, cancellationPendingCount).all { it >= 0 })
    require(
        pendingCount.toLong() + approvedCount + cancellationPendingCount == requestCount.toLong()
    )
    return LeaveCalendarDay(
        date,
        employeeCount,
        requestCount,
        pendingCount,
        approvedCount,
        cancellationPendingCount,
    )
}

fun LeaveCalendarEntryRow.toCalendarEntry(): LeaveCalendarEntry {
    val portion = LeavePortion.entries.single { it.mask == portionMask }
    val current = LeaveStatus.valueOf(status)
    require(
        current in
            setOf(LeaveStatus.PENDING, LeaveStatus.APPROVED, LeaveStatus.CANCELLATION_PENDING)
    )
    require(version >= 0)
    return LeaveCalendarEntry(
        requestId,
        employeeId,
        employeeNumber,
        employeeName,
        typeCode,
        typeName,
        portion,
        current,
        version,
    )
}
