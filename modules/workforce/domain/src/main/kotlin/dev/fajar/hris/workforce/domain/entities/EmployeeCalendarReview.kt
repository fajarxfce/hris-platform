package dev.fajar.hris.workforce.domain.entities

import java.time.LocalDate

data class EmployeeCalendarReview(
    val employee: WorkforceEmployeeReference,
    val from: LocalDate,
    val until: LocalDate,
    val calendar: EmployeeCalendar,
    val assignmentAtStart: ScheduleAssignment?,
)
