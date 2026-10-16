package dev.fajar.hris.workforce.delivery.responses

import java.time.LocalDate

data class EmployeeCalendarReviewResponse(
    val employee: WorkforceEmployeeResponse,
    val from: LocalDate,
    val until: LocalDate,
    val calendar: EmployeeCalendarResponse,
    val assignmentAtStart: ScheduleAssignmentResponse?,
    val rosterOverrides: List<RosterOverrideResponse>,
)
