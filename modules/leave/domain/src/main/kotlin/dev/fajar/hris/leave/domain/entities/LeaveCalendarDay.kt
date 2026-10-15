package dev.fajar.hris.leave.domain.entities

import java.time.LocalDate

data class LeaveCalendarDay(
    val date: LocalDate,
    val employeeCount: Int,
    val requestCount: Int,
    val pendingCount: Int,
    val approvedCount: Int,
    val cancellationPendingCount: Int,
)
