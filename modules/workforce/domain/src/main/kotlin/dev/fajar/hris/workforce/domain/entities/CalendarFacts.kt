package dev.fajar.hris.workforce.domain.entities

import java.time.LocalDate

data class CalendarFacts(
    val from: LocalDate,
    val until: LocalDate,
    val scheduleVersion: Long?,
    val assignments: List<ScheduleAssignment>,
    val roster: List<RosterOverride>,
    val holidays: List<WorkHoliday>,
)
