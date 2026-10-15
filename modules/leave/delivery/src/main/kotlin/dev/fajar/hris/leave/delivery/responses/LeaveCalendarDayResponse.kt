package dev.fajar.hris.leave.delivery.responses

import java.time.LocalDate

data class LeaveCalendarDayResponse(
    val date: LocalDate,
    val employeeCount: Int,
    val requestCount: Int,
    val pendingCount: Int,
    val approvedCount: Int,
    val cancellationPendingCount: Int,
)
