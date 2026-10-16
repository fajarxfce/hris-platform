package dev.fajar.hris.workforce.delivery.responses

import java.time.LocalDate

data class RosterBulkDayResponse(
    val workDate: LocalDate,
    val expectedVersion: Long?,
    val overrideActive: Boolean,
    val before: ScheduledDayResponse,
    val after: ScheduledDayResponse,
    val changed: Boolean,
)
