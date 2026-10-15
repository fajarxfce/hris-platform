package dev.fajar.hris.leave.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.time.LocalDate

data class LeaveCalendarEntriesResponse(
    val date: LocalDate,
    val scope: String,
    val companyDate: LocalDate,
    val timezone: String,
    val entries: Page<LeaveCalendarEntryResponse>,
)
