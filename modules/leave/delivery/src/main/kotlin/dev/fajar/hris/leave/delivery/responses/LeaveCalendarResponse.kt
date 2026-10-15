package dev.fajar.hris.leave.delivery.responses

import java.time.LocalDate
import java.time.YearMonth

data class LeaveCalendarResponse(
    val month: YearMonth,
    val scope: String,
    val companyDate: LocalDate,
    val timezone: String,
    val days: List<LeaveCalendarDayResponse>,
)
