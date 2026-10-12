package dev.fajar.hris.workforce.delivery.responses

import java.time.LocalDate

data class OvertimePlanningReviewResponse(
    val employee: WorkforceEmployeeResponse,
    val workDate: LocalDate,
    val companyDate: LocalDate,
    val timezone: String,
    val schedule: ScheduledDayResponse,
)
