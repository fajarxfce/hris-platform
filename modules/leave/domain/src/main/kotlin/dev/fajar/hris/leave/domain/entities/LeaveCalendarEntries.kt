package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.core.domain.Page
import java.time.LocalDate

data class LeaveCalendarEntries(
    val date: LocalDate,
    val scope: LeaveCalendarScope,
    val companyDate: LocalDate,
    val timezone: String,
    val entries: Page<LeaveCalendarEntry>,
)
