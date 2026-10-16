package dev.fajar.hris.workforce.domain.entities

import java.time.LocalDate

data class RosterBulkDay(
    val workDate: LocalDate,
    val expectedVersion: Long?,
    val overrideActive: Boolean,
    val before: ScheduledDay,
    val after: ScheduledDay,
    val changed: Boolean,
)
