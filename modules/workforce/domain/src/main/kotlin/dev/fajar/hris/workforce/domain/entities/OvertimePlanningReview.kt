package dev.fajar.hris.workforce.domain.entities

import java.time.LocalDate

data class OvertimePlanningReview(
    val employee: WorkforceEmployeeReference,
    val workDate: LocalDate,
    val companyDate: LocalDate,
    val timezone: String,
    val schedule: ScheduledDay,
    val revision: String,
)
