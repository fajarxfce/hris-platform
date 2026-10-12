package dev.fajar.hris.workforce.delivery.responses

import java.time.LocalDate

data class WorkPeriodOverviewResponse(
    val month: String,
    val companyDate: LocalDate,
    val companyTimezone: String,
    val period: WorkPeriodResponse?,
    val attempt: WorkPeriodAttemptResponse?,
    val availableActions: List<String>,
)
