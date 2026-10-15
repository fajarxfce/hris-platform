package dev.fajar.hris.leave.data.models

import java.time.LocalDate

data class LeaveCalendarDayRow(
    val date: LocalDate,
    val employeeCount: Int,
    val requestCount: Int,
    val pendingCount: Int,
    val approvedCount: Int,
    val cancellationPendingCount: Int,
)
