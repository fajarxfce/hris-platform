package dev.fajar.hris.workforce.delivery.responses
data class EmployeeCalendarResponse(
    val scheduleVersion: Long?,
    val days: List<ScheduledDayResponse>,
)
