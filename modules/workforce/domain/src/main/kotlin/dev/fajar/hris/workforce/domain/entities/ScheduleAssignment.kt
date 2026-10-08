package dev.fajar.hris.workforce.domain.entities

import java.time.DayOfWeek
import java.time.LocalDate

data class ScheduleAssignment(
    val effectiveFrom: LocalDate,
    val revision: Long,
    val days: Map<DayOfWeek, ShiftSnapshot>,
)
