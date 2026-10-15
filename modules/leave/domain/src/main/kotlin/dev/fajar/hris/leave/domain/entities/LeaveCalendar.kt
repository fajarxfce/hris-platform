package dev.fajar.hris.leave.domain.entities

import java.time.LocalDate
import java.time.YearMonth

data class LeaveCalendar(
    val month: YearMonth,
    val scope: LeaveCalendarScope,
    val companyDate: LocalDate,
    val timezone: String,
    val days: List<LeaveCalendarDay>,
)
